package savage.dbwrapper.database.mariadb;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.dbwrapper.config.DBWrapperConfig;
import savage.dbwrapper.database.DatabaseManager;
import savage.dbwrapper.utils.OSUtils;
import savage.dbwrapper.utils.ProcessUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        try {
            // Check if database files already exist (indicating previous installation)
            // Check both the actual data directory and the expected path that mariadb-install-db creates
            boolean databaseAlreadyExists = Files.exists(dataDirectory.resolve("ibdata1")) ||
                                          Files.exists(dataDirectory.resolve("mysql")) ||
                                          Files.exists(dataDirectory.resolve("performance_schema")) ||
                                          Files.exists(binDirectory.resolve(dataDirectory.toString().replace(":", "")).resolve("ibdata1")) ||
                                          Files.exists(binDirectory.resolve(dataDirectory.toString().replace(":", "")).resolve("mysql")) ||
                                          Files.exists(binDirectory.resolve(dataDirectory.toString().replace(":", "")).resolve("performance_schema"));

            if (databaseAlreadyExists) {
                LOGGER.info("MariaDB data files already exist - skipping installation");
                return;
            }

            // Copy binary from resources
            copyBinaryFromResources();

            // Ensure data directory exists before running installation
            try {
                Files.createDirectories(dataDirectory);
                LOGGER.info("Created data directory at: " + dataDirectory);

                // Create the path structure that mariadb-install-db expects
                // It seems to append the datadir path to the working directory
                Path expectedPath = binDirectory.resolve(dataDirectory.toString().replace(":", ""));
                Files.createDirectories(expectedPath);
                LOGGER.info("Created expected path structure at: " + expectedPath);

            } catch (IOException e) {
                LOGGER.error("Failed to create data directory", e);
                throw new RuntimeException("Failed to create data directory", e);
            }

            // Run installation command
            runInstallationCommand();

            LOGGER.info("MariaDB installed successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to install MariaDB", e);
        }
    }

    @Override
    public void startDatabase() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                binDirectory.resolve("mysqld" + OSUtils.getBinarySuffix()).toString(),
                "--console",
                "--port=" + config.getMariadb().getPort(),
                "--datadir=" + dataDirectory.toString()
            );

            processBuilder.directory(binDirectory.toFile());
            databaseProcess = processBuilder.start();

            // Log process output
            ProcessUtils.logProcessOutput(databaseProcess, "MariaDB");

            // MariaDB is a long-running process, so we can't wait for it to complete
            // Instead, we'll consider it started immediately and let it run
            LOGGER.info("MariaDB started successfully on port {}", config.getMariadb().getPort());
        } catch (IOException e) {
            LOGGER.error("Failed to start MariaDB", e);
        }
    }

    @Override
    public void stopDatabase() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                binDirectory.resolve("mysqladmin" + OSUtils.getBinarySuffix()).toString(),
                "shutdown",
                "--user=" + config.getMariadb().getUsername(),
                "--password=" + config.getMariadb().getPassword(),
                "--port=" + config.getMariadb().getPort()
            );

            processBuilder.directory(binDirectory.toFile());
            Process shutdownProcess = processBuilder.start();

            // Wait for shutdown to complete
            if (shutdownProcess.waitFor(10, TimeUnit.SECONDS)) {
                LOGGER.info("MariaDB stopped successfully");
            } else {
                LOGGER.error("MariaDB shutdown timed out");
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
        if (databaseProcess != null) {
            return databaseProcess.isAlive();
        }
        return false;
    }

    private void copyBinaryFromResources() throws IOException {
        // Check if binary already exists
        if (Files.exists(binDirectory.resolve("mysqld" + OSUtils.getBinarySuffix()))) {
            LOGGER.info("MariaDB binary already copied");
            return;
        }

        String binaryName = "mariadb-" + getMariaDBVersion() + "-" + OSUtils.getOSName() + ".zip";
        Path sourcePath = FabricLoader.getInstance().getModContainer("dbwrapper")
                .orElseThrow(() -> new RuntimeException("Mod container not found"))
                .findPath("assets/dbwrapper/" + binaryName)
                .orElseThrow(() -> new RuntimeException("Binary not found in resources: " + binaryName));

        Path targetPath = configDirectory.resolve(binaryName);

        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Extract zip file
        try {
            // Use Java's built-in ZIP functionality
            // Extract all files to the bin directory since the ZIP contains files at root level
            try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(targetPath.toFile())) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = entries.nextElement();

                    // Skip directories, extract all files to bin directory
                    if (!entry.isDirectory()) {
                        Path entryPath = binDirectory.resolve(entry.getName());
                        Files.createDirectories(entryPath.getParent());
                        try (java.io.InputStream inputStream = zipFile.getInputStream(entry)) {
                            Files.copy(inputStream, entryPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            LOGGER.info("MariaDB binary copied and extracted successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to extract MariaDB binary", e);
            throw new IOException("Failed to extract MariaDB binary", e);
        }
    }

    private void runInstallationCommand() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
            binDirectory.resolve("mariadb-install-db" + OSUtils.getBinarySuffix()).toString(),
            "--datadir=" + dataDirectory.toString(),
            "--password=" + config.getMariadb().getPassword()
        );

        processBuilder.directory(binDirectory.toFile());
        Process installProcess = processBuilder.start();

        // Log process output
        ProcessUtils.logProcessOutput(installProcess, "MariaDB Install");

        boolean completed = installProcess.waitFor(60, TimeUnit.SECONDS);
        if (completed) {
            int exitCode = installProcess.exitValue();
            if (exitCode == 0) {
                LOGGER.info("MariaDB installation command completed successfully");
            } else {
                throw new RuntimeException("MariaDB installation failed with exit code: " + exitCode);
            }
        } else {
            throw new RuntimeException("MariaDB installation timed out");
        }
    }

    private String getMariaDBVersion() {
        // TODO: Make this configurable
        return "12.1.2";
    }

    @Override
    public String getDatabaseType() {
        return "mariadb";
    }
}