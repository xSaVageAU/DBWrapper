package savage.dbwrapper.database.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.loader.api.FabricLoader;
import savage.dbwrapper.config.DBWrapperConfig;
import savage.dbwrapper.database.DatabaseManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RedisManager implements DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisManager.class);

    private DBWrapperConfig config;
    private final Path configDirectory;
    private final Path redisDirectory;
    private final Path dataDirectory;

    private SimpleRedisServer simpleRedisServer;
    private SimpleRedisClient simpleRedisClient;

    public RedisManager() {
        this.configDirectory = FabricLoader.getInstance().getConfigDir().resolve("dbwrapper");
        this.redisDirectory = configDirectory.resolve("redis");
        this.dataDirectory = redisDirectory.resolve("data");
    }

    public void setConfig(DBWrapperConfig config) {
        this.config = config;
    }

    @Override
    public void initialize() {
        try {
            // Create directories if they don't exist
            Files.createDirectories(configDirectory);
            Files.createDirectories(redisDirectory);
            Files.createDirectories(dataDirectory);

            LOGGER.info("Redis Manager initialized");
        } catch (IOException e) {
            LOGGER.error("Failed to create directories", e);
        }
    }

    @Override
    public void installDatabase() {
        // For our simple Redis, installation is just creating the data directory
        try {
            Files.createDirectories(dataDirectory);
            LOGGER.info("Redis installed successfully");
        } catch (IOException e) {
            LOGGER.error("Failed to install Redis", e);
        }
    }

    @Override
    public void startDatabase() {
        // Check if Redis is actually running by trying to connect
        boolean actuallyRunning = false;
        try {
            SimpleRedisClient testClient = new SimpleRedisClient("localhost", config.getRedis().getPort());
            testClient.connect();
            testClient.ping();
            testClient.close();
            actuallyRunning = true;
        } catch (Exception e) {
            // Redis is not actually running
            actuallyRunning = false;
        }

        if (actuallyRunning) {
            LOGGER.info("Redis is already running on port {}", config.getRedis().getPort());
            return;
        }

        try {
            LOGGER.info("Starting Redis server on port {}", config.getRedis().getPort());

            // Start simple Redis server
            simpleRedisServer = new SimpleRedisServer(
                config.getRedis().getPort(), 
                config.getRedis().getPassword(), 
                dataDirectory,
                config.getRedis().getMaxConnections()
            );
            simpleRedisServer.start();

            // Create client connection
            simpleRedisClient = new SimpleRedisClient("localhost", config.getRedis().getPort());
            simpleRedisClient.connect();

            // Authenticate if password is set
            if (config.getRedis().hasPassword()) {
                simpleRedisClient.auth(config.getRedis().getPassword());
            }

            // Test connection
            String pingResponse = simpleRedisClient.ping();
            LOGGER.info("Redis connection test successful: {}", pingResponse);
            LOGGER.info("Redis started successfully on port {}", config.getRedis().getPort());
        } catch (IOException e) {
            LOGGER.error("Failed to start Redis server", e);
            try {
                if (simpleRedisClient != null) {
                    simpleRedisClient.close();
                }
                if (simpleRedisServer != null) {
                    simpleRedisServer.stop();
                }
            } catch (Exception ex) {
                LOGGER.error("Error cleaning up after failed start", ex);
            }
        }
    }

    @Override
    public void stopDatabase() {
        try {
            // Close client
            if (simpleRedisClient != null) {
                simpleRedisClient.close();
                simpleRedisClient = null;
            }

            // Stop server
            if (simpleRedisServer != null) {
                simpleRedisServer.stop();
                simpleRedisServer = null;
            }

            LOGGER.info("Redis stopped successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to stop Redis", e);
        }
    }

    @Override
    public boolean isDatabaseRunning() {
        // First check if we have a server instance and it's marked as running
        if (simpleRedisServer != null && simpleRedisServer.isRunning()) {
            return true;
        }

        // If not, try to connect to see if Redis is actually running
        try {
            SimpleRedisClient testClient = new SimpleRedisClient("localhost", config.getRedis().getPort());
            testClient.connect();
            testClient.ping();
            testClient.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDatabaseType() {
        return "redis";
    }

    public SimpleRedisClient getRedisClient() {
        if (simpleRedisClient == null) {
            throw new IllegalStateException("Redis is not running");
        }
        return simpleRedisClient;
    }
}