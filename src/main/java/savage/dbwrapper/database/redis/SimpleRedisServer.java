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
    private final String password;
    private final Path dataDirectory;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean running = false;

    // Simple in-memory data store
    private final Map<String, String> dataStore = new HashMap<>();
    private final Map<String, Long> expirationTimes = new HashMap<>();

    // Pub/Sub support
    private final Map<String, List<ClientConnection>> channelSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, ClientConnection> clientConnections = new ConcurrentHashMap<>();

    // Authentication support
    private final Set<String> authenticatedClients = ConcurrentHashMap.newKeySet();
    private int clientCounter = 0;

    // Inner class to track client connections
    private static class ClientConnection {
        final String clientId;
        final BufferedWriter writer;
        final Set<String> subscriptions = new HashSet<>();

        ClientConnection(String clientId, BufferedWriter writer) {
            this.clientId = clientId;
            this.writer = writer;
        }
    }

    public SimpleRedisServer(int port, String password, Path dataDirectory) {
        this.port = port;
        this.password = password;
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
                    String clientId = "client-" + (++clientCounter);
                    executorService.submit(() -> handleClient(clientSocket, clientId));
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

    private void handleClient(Socket socket, String clientId) {
        ClientConnection clientConn;
        try {
            clientConn = new ClientConnection(clientId,
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
            clientConnections.put(clientId, clientConn);
        } catch (IOException e) {
            LOGGER.error("Failed to create client connection for {}", clientId, e);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
              BufferedWriter writer = clientConn.writer) {

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
                        handleCommand(commands, writer, clientId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error handling client", e);
        } finally {
            // Clean up client connection
            clientConnections.remove(clientId);
            // Remove from all channel subscriptions
            for (List<ClientConnection> subscribers : channelSubscriptions.values()) {
                subscribers.remove(clientConn);
            }

            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.error("Error closing socket", e);
            }
        }
    }

    private void handleCommand(String[] commands, BufferedWriter writer, String clientId) throws IOException {
        String command = commands[0].toUpperCase();

        // Check authentication for commands that require it
        boolean requiresAuth = !command.equals("AUTH") && !command.equals("PING") && !command.equals("QUIT");
        if (requiresAuth && password != null && !password.trim().isEmpty() && !authenticatedClients.contains(clientId)) {
            writer.write("-NOAUTH Authentication required.\r\n");
            writer.flush();
            return;
        }

        try {
            switch (command) {
                case "AUTH":
                    if (commands.length >= 2) {
                        String providedPassword = commands[1];
                        if (password == null || password.trim().isEmpty() || password.equals(providedPassword)) {
                            authenticatedClients.add(clientId);
                            writer.write("+OK\r\n");
                            writer.flush();
                            LOGGER.info("Client {} authenticated successfully", clientId);
                        } else {
                            writer.write("-ERR invalid password\r\n");
                            writer.flush();
                            LOGGER.warn("Client {} provided invalid password", clientId);
                        }
                    } else {
                        writer.write("-ERR wrong number of arguments for 'auth' command\r\n");
                        writer.flush();
                    }
                    break;

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
                    if (commands.length >= 2) {
                        String channel = commands[1];
                        ClientConnection clientConn = clientConnections.get(clientId);

                        // Add to channel subscriptions
                        channelSubscriptions.computeIfAbsent(channel, k -> new ArrayList<>()).add(clientConn);
                        clientConn.subscriptions.add(channel);

                        // Send subscription confirmation
                        writer.write("*3\r\n");
                        writer.write("$9\r\n");
                        writer.write("subscribe\r\n");
                        writer.write("$" + channel.length() + "\r\n");
                        writer.write(channel + "\r\n");
                        writer.write(":1\r\n"); // Number of subscriptions
                        writer.flush();
                        LOGGER.info("Client {} subscribed to channel: {}", clientId, channel);
                    } else {
                        writer.write("-ERR wrong number of arguments for 'subscribe' command\r\n");
                        writer.flush();
                    }
                    break;

                case "PUBLISH":
                    if (commands.length >= 3) {
                        String channel = commands[1];
                        String message = commands[2];

                        // Get subscribers for this channel
                        List<ClientConnection> subscribers = channelSubscriptions.get(channel);
                        int recipientCount = 0;

                        if (subscribers != null) {
                            // Send message to all subscribers
                            for (ClientConnection subscriber : subscribers) {
                                try {
                                    // Send Redis pub/sub message format: *3\r\n$7\r\nmessage\r\n$[channel_len]\r\n[channel]\r\n$[message_len]\r\n[message]\r\n
                                    subscriber.writer.write("*3\r\n");
                                    subscriber.writer.write("$7\r\n");
                                    subscriber.writer.write("message\r\n");
                                    subscriber.writer.write("$" + channel.length() + "\r\n");
                                    subscriber.writer.write(channel + "\r\n");
                                    subscriber.writer.write("$" + message.length() + "\r\n");
                                    subscriber.writer.write(message + "\r\n");
                                    subscriber.writer.flush();
                                    recipientCount++;
                                } catch (IOException e) {
                                    LOGGER.warn("Failed to send message to subscriber {}, removing from subscriptions", subscriber.clientId);
                                    // Remove failed subscriber
                                    subscribers.remove(subscriber);
                                }
                            }
                        }

                        // Respond with number of recipients
                        writer.write(":" + recipientCount + "\r\n");
                        writer.flush();
                        LOGGER.info("Published message to channel {}: {} (sent to {} clients)", channel, message, recipientCount);
                    } else {
                        writer.write("-ERR wrong number of arguments for 'publish' command\r\n");
                        writer.flush();
                    }
                    break;

                case "UNSUBSCRIBE":
                    ClientConnection clientConn = clientConnections.get(clientId);
                    if (commands.length >= 2) {
                        // Unsubscribe from specific channel
                        String channel = commands[1];
                        List<ClientConnection> subscribers = channelSubscriptions.get(channel);
                        if (subscribers != null) {
                            subscribers.remove(clientConn);
                            if (subscribers.isEmpty()) {
                                channelSubscriptions.remove(channel);
                            }
                        }
                        clientConn.subscriptions.remove(channel);

                        writer.write("*3\r\n");
                        writer.write("$11\r\n");
                        writer.write("unsubscribe\r\n");
                        writer.write("$" + channel.length() + "\r\n");
                        writer.write(channel + "\r\n");
                        writer.write(":" + clientConn.subscriptions.size() + "\r\n"); // Remaining subscriptions
                        writer.flush();
                        LOGGER.info("Client {} unsubscribed from channel: {}", clientId, channel);
                    } else {
                        // Unsubscribe from all channels
                        for (String channel : new HashSet<>(clientConn.subscriptions)) {
                            List<ClientConnection> subscribers = channelSubscriptions.get(channel);
                            if (subscribers != null) {
                                subscribers.remove(clientConn);
                                if (subscribers.isEmpty()) {
                                    channelSubscriptions.remove(channel);
                                }
                            }
                        }
                        int remainingSubs = clientConn.subscriptions.size();
                        clientConn.subscriptions.clear();

                        writer.write("*3\r\n");
                        writer.write("$11\r\n");
                        writer.write("unsubscribe\r\n");
                        writer.write("$0\r\n");
                        writer.write("\r\n");
                        writer.write(":" + remainingSubs + "\r\n");
                        writer.flush();
                        LOGGER.info("Client {} unsubscribed from all channels", clientId);
                    }
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