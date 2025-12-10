package savage.dbwrapper.database.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Redis client implementation using pure Java
 * Supports basic Redis protocol commands
 */
public class SimpleRedisClient implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleRedisClient.class);

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public SimpleRedisClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        LOGGER.info("Connected to Redis server at {}:{}", host, port);
    }

    public String ping() throws IOException {
        sendCommand("PING");
        return readSimpleString();
    }

    public void set(String key, String value) throws IOException {
        sendCommand("SET", key, value);
        readSimpleString(); // Read "OK" response
    }

    public void setWithExpiration(String key, String value, long ttlMillis) throws IOException {
        sendCommand("SET", key, value, "PX", String.valueOf(ttlMillis));
        readSimpleString(); // Read "OK" response
    }

    public String get(String key) throws IOException {
        sendCommand("GET", key);
        return readBulkString();
    }

    public boolean exists(String key) throws IOException {
        sendCommand("EXISTS", key);
        return readInteger() == 1;
    }

    public boolean del(String key) throws IOException {
        sendCommand("DEL", key);
        return readInteger() == 1;
    }

    private void sendCommand(String... parts) throws IOException {
        // Send array
        writer.write("*" + parts.length + "\r\n");

        // Send each part as bulk string
        for (String part : parts) {
            if (part == null) {
                writer.write("$" + (-1) + "\r\n");
            } else {
                byte[] bytes = part.getBytes();
                writer.write("$" + bytes.length + "\r\n");
                writer.write(part + "\r\n");
            }
        }

        writer.flush();
    }

    private String readSimpleString() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Connection closed");
        if (!line.startsWith("+")) throw new IOException("Expected simple string, got: " + line);
        return line.substring(1);
    }

    private String readBulkString() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Connection closed");
        if (!line.startsWith("$")) throw new IOException("Expected bulk string, got: " + line);

        int length = Integer.parseInt(line.substring(1));
        if (length == -1) {
            return null; // Null bulk string
        }

        return reader.readLine();
    }

    private int readInteger() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Connection closed");
        if (!line.startsWith(":")) throw new IOException("Expected integer, got: " + line);
        return Integer.parseInt(line.substring(1));
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
            LOGGER.info("Disconnected from Redis server");
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}