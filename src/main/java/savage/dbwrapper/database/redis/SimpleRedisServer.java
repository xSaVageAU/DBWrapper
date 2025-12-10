package savage.dbwrapper.database.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple Redis-like server implementation using pure Java
 * Supports basic Redis protocol commands
 */
public class SimpleRedisServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleRedisServer.class);

    private final int port;
    private final Path dataDirectory;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean running = false;

    // Simple in-memory data store
    private final Map<String, String> dataStore = new HashMap<>();
    private final Map<String, Long> expirationTimes = new HashMap<>();

    // Pub/Sub support - simplified for basic compatibility
    private final Map<String, List<PrintWriter>> channelSubscriptions = new ConcurrentHashMap<>();

    public SimpleRedisServer(int port, Path dataDirectory) {
        this.port = port;
        this.dataDirectory = dataDirectory;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        executorService = Executors.newCachedThreadPool();
        running = true;

        LOGGER.info("Simple Redis server started on port {}", port);

        // Start client handler thread
        new Thread(this::acceptConnections).start();
    }

    private void acceptConnections() {
        try {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executorService.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        LOGGER.error("Error accepting client connection", e);
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                LOGGER.error("Redis server error", e);
            }
        }
    }

    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            while (running) {
                String line = reader.readLine();
                if (line == null) break;

                // Parse Redis protocol
                if (line.startsWith("*")) { // Array
                    int commandCount = Integer.parseInt(line.substring(1));
                    String[] commands = new String[commandCount];

                    for (int i = 0; i < commandCount; i++) {
                        // Read bulk string
                        String bulkLine = reader.readLine();
                        if (bulkLine == null || !bulkLine.startsWith("$")) break;

                        int length = Integer.parseInt(bulkLine.substring(1));
                        if (length == -1) { // Null bulk string
                            commands[i] = null;
                        } else {
                            commands[i] = reader.readLine();
                        }
                    }

                    if (commands.length > 0 && commands[0] != null) {
                        handleCommand(commands, writer);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error handling client", e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.error("Error closing socket", e);
            }
        }
    }

    private void handleCommand(String[] commands, BufferedWriter writer) throws IOException {
        String command = commands[0].toUpperCase();

        try {
            switch (command) {
                case "PING":
                    writer.write("+PONG\r\n");
                    writer.flush();
                    break;

                case "SET":
                    if (commands.length >= 3) {
                        String key = commands[1];
                        String value = commands[2];
                        dataStore.put(key, value);

                        // Handle expiration if provided
                        if (commands.length >= 5 && "PX".equalsIgnoreCase(commands[3])) {
                            long ttl = Long.parseLong(commands[4]);
                            expirationTimes.put(key, System.currentTimeMillis() + ttl);
                        }

                        writer.write("+OK\r\n");
                        writer.flush();
                    } else {
                        writer.write("-ERR wrong number of arguments for 'set' command\r\n");
                        writer.flush();
                    }
                    break;

                case "GET":
                    if (commands.length >= 2) {
                        String key = commands[1];
                        String value = dataStore.get(key);

                        // Check expiration
                        if (value != null && expirationTimes.containsKey(key)) {
                            if (System.currentTimeMillis() > expirationTimes.get(key)) {
                                dataStore.remove(key);
                                expirationTimes.remove(key);
                                value = null;
                            }
                        }

                        if (value != null) {
                            writer.write("$" + value.length() + "\r\n");
                            writer.write(value + "\r\n");
                            writer.flush();
                        } else {
                            writer.write("$-1\r\n"); // Null bulk string
                            writer.flush();
                        }
                    } else {
                        writer.write("-ERR wrong number of arguments for 'get' command\r\n");
                        writer.flush();
                    }
                    break;

                case "DEL":
                    if (commands.length >= 2) {
                        String key = commands[1];
                        String value = dataStore.remove(key);
                        expirationTimes.remove(key);

                        if (value != null) {
                            writer.write(":1\r\n"); // Integer reply
                        } else {
                            writer.write(":0\r\n");
                        }
                        writer.flush();
                    } else {
                        writer.write("-ERR wrong number of arguments for 'del' command\r\n");
                        writer.flush();
                    }
                    break;

                case "EXISTS":
                    if (commands.length >= 2) {
                        String key = commands[1];
                        boolean exists = dataStore.containsKey(key);

                        // Check expiration
                        if (exists && expirationTimes.containsKey(key)) {
                            if (System.currentTimeMillis() > expirationTimes.get(key)) {
                                dataStore.remove(key);
                                expirationTimes.remove(key);
                                exists = false;
                            }
                        }

                        writer.write(":" + (exists ? "1" : "0") + "\r\n");
                        writer.flush();
                    } else {
                        writer.write("-ERR wrong number of arguments for 'exists' command\r\n");
                        writer.flush();
                    }
                    break;

                case "KEYS":
                    writer.write("*0\r\n"); // Empty array - no keys pattern support for simplicity
                    writer.flush();
                    break;

                case "SUBSCRIBE":
                    // Basic SUBSCRIBE support - just acknowledge the subscription
                    if (commands.length >= 2) {
                        String channel = commands[1];
                        writer.write("*3\r\n");
                        writer.write("$9\r\n");
                        writer.write("subscribe\r\n");
                        writer.write("$" + channel.length() + "\r\n");
                        writer.write(channel + "\r\n");
                        writer.write(":1\r\n"); // Number of subscriptions
                        writer.flush();
                        LOGGER.info("Client subscribed to channel: {}", channel);
                    } else {
                        writer.write("-ERR wrong number of arguments for 'subscribe' command\r\n");
                        writer.flush();
                    }
                    break;

                case "PUBLISH":
                    // Basic PUBLISH support - acknowledge but don't actually publish
                    if (commands.length >= 3) {
                        String channel = commands[1];
                        String message = commands[2];
                        writer.write(":0\r\n"); // Number of clients that received the message
                        writer.flush();
                        LOGGER.info("Published message to channel {}: {}", channel, message);
                    } else {
                        writer.write("-ERR wrong number of arguments for 'publish' command\r\n");
                        writer.flush();
                    }
                    break;

                case "UNSUBSCRIBE":
                    // Basic UNSUBSCRIBE support
                    writer.write("*3\r\n");
                    writer.write("$11\r\n");
                    writer.write("unsubscribe\r\n");
                    writer.write("$0\r\n");
                    writer.write("\r\n");
                    writer.write(":0\r\n"); // Number of remaining subscriptions
                    writer.flush();
                    break;

                default:
                    writer.write("-ERR unknown command '" + command + "'\r\n");
                    writer.flush();
                    break;
            }
        } catch (Exception e) {
            writer.write("-ERR " + e.getMessage() + "\r\n");
            writer.flush();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.error("Error closing server socket", e);
        }

        if (executorService != null) {
            executorService.shutdownNow();
        }

        LOGGER.info("Simple Redis server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}