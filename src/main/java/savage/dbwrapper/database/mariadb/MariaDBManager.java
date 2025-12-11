package savage.dbwrapper.database.mariadb;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.dbwrapper.config.DBWrapperConfig;
import savage.dbwrapper.database.DatabaseManager;
import savage.dbwrapper.utils.BinaryManager;
import savage.dbwrapper.utils.OSUtils;
import savage.dbwrapper.utils.ProcessUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MariaDBManager implements DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MariaDBManager.class);

    private DBWrapperConfig config;
    private final Path configDirectory;
    private final Path mariaDBDirectory;
    private final Path binDirectory;
    private final Path dataDirectory;

    private Process databaseProcess;

    public MariaDBManager() {
        this.configDirectory = FabricLoader.getInstance().getConfigDir().resolve("dbwrapper");
        this.mariaDBDirectory = configDirectory.resolve("mariadb");
        this.binDirectory = mariaDBDirectory.resolve("bin");
        this.dataDirectory = mariaDBDirectory.resolve("data");
    }

    public void setConfig(DBWrapperConfig config) {
        this.config = config;
    }

    @Override
    public void initialize() {
        try {
            // Create directories if they don't exist
            Files.createDirectories(configDirectory);
            Files.createDirectories(mariaDBDirectory);
            Files.createDirectories(binDirectory);
            Files.createDirectories(dataDirectory);

            LOGGER.info("MariaDB Manager initialized");
        } catch (IOException e) {
            LOGGER.error("Failed to create directories", e);
        }
    }

    @Override
    public void installDatabase() {
        // Delegate to BinaryManager for download/extraction
        try {
            BinaryManager.setupMariaDB(configDirectory, binDirectory);
            
            // Initialize data directory if needed (mysql_install_db)
            Path dataDir = binDirectory.resolve("data");
            if (!Files.exists(dataDir) || isDataDirEmpty(dataDir)) {
                LOGGER.info("Initializing MariaDB data directory...");
                Files.createDirectories(dataDir);
                
                String installDbDataBinary = OSUtils.isWindows() ? "mysql_install_db.exe" : "mariadb-install-db";
                Path installDbPath = binDirectory.resolve("bin/" + installDbDataBinary);
                
                // Fallback: Check older names vs newer names
                if (!Files.exists(installDbPath) && OSUtils.isLinux()) {
                     // Try scripts/mysql_install_db
                     installDbPath = binDirectory.resolve("scripts/mysql_install_db");
                     if (!Files.exists(installDbPath)) {
                         // Try bin/mysql_install_db
                         installDbPath = binDirectory.resolve("bin/mysql_install_db");
                     }
                }
                
                if (!Files.exists(installDbPath)) {
                    throw new IOException("Could not find install DB binary. Checked: " + installDbDataBinary);
                }

                List<String> command = new ArrayList<>();
                command.add(installDbPath.toAbsolutePath().toString());
                command.add("--datadir=" + dataDir.toAbsolutePath().toString());
                
                // CRITICAL: Explicitly set basedir so it can find plugins (InnoDB) and share (english messages)
                // The binDirectory acts as the "root" of extraction
                // On Windows, mysql_install_db.exe does not support --basedir and finds things relative to itself
                if (!OSUtils.isWindows()) {
                    command.add("--basedir=" + binDirectory.toAbsolutePath().toString());
                }
                
                // Redirect error to allow debugging
                // command.add("--log-error=" + binDirectory.resolve("install_error.log").toString());

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(binDirectory.toFile());
                
                LOGGER.info("Running installation command: " + String.join(" ", command));

                Process process = pb.start();
                ProcessUtils.logProcessOutput(process, "MariaDB-Install");
                
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    LOGGER.error("MariaDB init failed with exit code: " + exitCode);
                    // Cleanup failed install to prevent corruption issues on retry
                    LOGGER.warn("Cleaning up corrupt data directory...");
                    try {
                        Files.walk(dataDir)
                            .sorted((a, b) -> b.compareTo(a)) // Delete leaves first
                            .forEach(p -> {
                                try { Files.delete(p); } catch (IOException ignored) {}
                            });
                    } catch (Exception ex) {
                        LOGGER.error("Failed to cleanup data dir", ex);
                    }
                    throw new IOException("MariaDB failed to initialize data directory");
                } else {
                    LOGGER.info("MariaDB data initialized successfully!");
                }
            }
            
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to install MariaDB", e);
        }
    }
    
    private boolean isDataDirEmpty(Path dataDir) {
        try (java.util.stream.Stream<Path> entries = Files.list(dataDir)) {
            return !entries.findAny().isPresent();
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public void startDatabase() {
        if (isDatabaseRunning()) {
            LOGGER.warn("MariaDB is already running");
            return;
        }

        try {
            LOGGER.info("Starting MariaDB...");

            // Determine binary name
            String binaryName = OSUtils.getExecutableName("mysqld");
            Path binaryPath = binDirectory.resolve("bin").resolve(binaryName);
            
            // Linux Fallback: newer versions use 'mariadbd'
            if (!Files.exists(binaryPath) && OSUtils.isLinux()) {
                 binaryPath = binDirectory.resolve("bin/mariadbd");
            }

            if (!Files.exists(binaryPath)) {
                 LOGGER.error("Could not find MariaDB binary at {}", binaryPath);
                 return;
            }

            List<String> command = new ArrayList<>();
            command.add(binaryPath.toAbsolutePath().toString());
            command.add("--no-defaults");
            command.add("--console");
            command.add("--port=" + config.getMariadb().getPort());
            command.add("--datadir=" + binDirectory.resolve("data").toAbsolutePath().toString());
            
            // Only add basedir if strictly necessary or on Windows to avoid path issues
            // command.add("--basedir=" + binDirectory.toAbsolutePath().toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(binDirectory.toFile());

            databaseProcess = processBuilder.start();
            ProcessUtils.logProcessOutput(databaseProcess, "MariaDB");

            // MariaDB is a long-running process, so we can't wait for it to complete
            // Instead, we'll consider it started immediately and let it run, BUT we will wait for it to accept connections
            LOGGER.info("MariaDB process started. Waiting for connection...");
            
            if (waitForReady()) {
                LOGGER.info("MariaDB started successfully on port {}", config.getMariadb().getPort());
                secureDatabase();
            } else {
                LOGGER.error("MariaDB failed to start (timeout exceeded)");
                stopDatabase();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to start MariaDB", e);
        }
    }
    
    private void secureDatabase() {
        String url = "jdbc:mariadb://localhost:" + config.getMariadb().getPort() + "/";
        String username = "root";
        String password = config.getMariadb().getPassword();
        
        // 1. Try connecting with configured password (happy path)
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, username, password)) {
            LOGGER.info("MariaDB already secured with configured password.");
            return;
        } catch (java.sql.SQLException ignored) {
            // Password mismatch? Try empty password (fresh install)
        }
        
        // 2. Try connecting with no password
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, username, "")) {
            LOGGER.info("Detected fresh MariaDB install. Securing root account...");
            
            try (java.sql.Statement stmt = conn.createStatement()) {
                // Set the password for root
                // Use 'Access Denied' safe query
                stmt.execute("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + password + "'");
                stmt.execute("FLUSH PRIVILEGES");
                LOGGER.info("Successfully secured MariaDB root account!");
            }
        } catch (java.sql.SQLException e) {
            LOGGER.error("Failed to secure MariaDB. Could not connect with config password OR empty password.", e);
        }
    }

    private boolean waitForReady() {
        long startTime = System.currentTimeMillis();
        long timeout = 20000; // 20 seconds
        
        while (System.currentTimeMillis() - startTime < timeout) {
            // Use JDBC to check readiness instead of raw socket, simpler usage of existing libs
            try (java.net.Socket ignored = new java.net.Socket("localhost", config.getMariadb().getPort())) {
                return true;
            } catch (IOException e) {
                // Wait and retry
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public void stopDatabase() {
        try {
            String adminBinary = OSUtils.getExecutableName("mysqladmin");
            Path adminPath = binDirectory.resolve("bin").resolve(adminBinary);
            
            // Linux fallback: check for mariadb-admin
            if (!Files.exists(adminPath) && OSUtils.isLinux()) {
                adminPath = binDirectory.resolve("bin/mariadb-admin");
            }
            
            if (!Files.exists(adminPath)) {
                LOGGER.error("Could not find MariaDB admin binary. Tried: {} and mariadb-admin", adminBinary);
                // Don't return, as we still want to try forcibly killing the process in the finally block
            } else {
                ProcessBuilder processBuilder = new ProcessBuilder(
                    adminPath.toAbsolutePath().toString(),
                    "shutdown",
                    "--user=" + config.getMariadb().getUsername(),
                    "--password=" + config.getMariadb().getPassword(),
                    "--port=" + config.getMariadb().getPort()
                    // "--socket=..." might be needed on Linux if we used unix sockets, but we use TCP
                );
    
                processBuilder.directory(binDirectory.toFile());
                Process shutdownProcess = processBuilder.start();
    
                // Wait for shutdown to complete
                if (shutdownProcess.waitFor(10, TimeUnit.SECONDS)) {
                    LOGGER.info("MariaDB stopped successfully");
                } else {
                    LOGGER.error("MariaDB shutdown timed out");
                }
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Failed to stop MariaDB", e);
        } finally {
            // More aggressive process cleanup
            if (databaseProcess != null) {
                try {
                    // Try graceful shutdown first
                    databaseProcess.destroy();
                    if (!databaseProcess.waitFor(5, TimeUnit.SECONDS)) {
                        // Force kill if graceful shutdown fails
                        databaseProcess.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    LOGGER.error("Interrupted while destroying MariaDB process", e);
                    Thread.currentThread().interrupt();
                } finally {
                    databaseProcess = null;
                }
            }
        }
    }

    @Override
    public boolean isDatabaseRunning() {
        return databaseProcess != null && databaseProcess.isAlive();
    }

    @Override
    public String getDatabaseType() {
        return "mariadb";
    }
}