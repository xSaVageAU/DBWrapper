package savage.dbwrapper.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BinaryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryManager.class);
    
    // URL for Linux binary (provided by user)
    private static final String LINUX_DOWNLOAD_URL = "https://dlm.mariadb.com/4516810/MariaDB/mariadb-12.1.2/bintar-linux-systemd-x86_64/mariadb-12.1.2-linux-systemd-x86_64.tar.gz";

    public static void setupMariaDB(Path configDir, Path binDir) throws IOException {
        if (OSUtils.isWindows()) {
            setupWindows(configDir, binDir);
        } else if (OSUtils.isLinux()) {
            setupLinux(binDir);
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + System.getProperty("os.name"));
        }
    }

    private static void setupWindows(Path configDir, Path binDir) throws IOException {
        Path exePath = binDir.resolve("bin/mysqld.exe");
        if (Files.exists(exePath)) {
            LOGGER.info("MariaDB binaries already exist.");
            return;
        }

        LOGGER.info("Extracting MariaDB binaries for Windows...");
        
        // Ensure directory exists
        Files.createDirectories(binDir);

        // Copy zip from resources
        String zipName = "mariadb-winx64.zip";
        Path zipPath = configDir.resolve(zipName);
        
        try (InputStream in = BinaryManager.class.getResourceAsStream("/" + zipName)) {
            if (in == null) {
                throw new IOException("Could not find " + zipName + " in resources!");
            }
            Files.copy(in, zipPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Extract zip
        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                Path filePath = binDir.resolve(entry.getName());
                if (!entry.isDirectory()) {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zipIn, filePath, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.createDirectories(filePath);
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
        
        // Clean up zip
        Files.deleteIfExists(zipPath);
        LOGGER.info("MariaDB binaries extracted successfully.");
    }

    private static void setupLinux(Path binDir) throws IOException {
        // Check for mariadbd or mysqld (some versions symlink)
        if (Files.exists(binDir.resolve("bin/mariadbd")) || Files.exists(binDir.resolve("bin/mysqld"))) {
             LOGGER.info("MariaDB binaries already exist.");
             return;
        }
        
        LOGGER.info("Downloading MariaDB binaries for Linux...");
        Files.createDirectories(binDir);
        
        Path tarPath = binDir.resolve("mariadb.tar.gz");
        
        // Download
        try (BufferedInputStream in = new BufferedInputStream(new URL(LINUX_DOWNLOAD_URL).openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(tarPath.toFile())) {
            byte[] dataBuffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
        LOGGER.info("Download complete. Extracting...");
        
        // Extract using system tar
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "tar", 
                "-xzf", 
                tarPath.getFileName().toString(), 
                "--strip-components=1" // Remove the top-level directory from the tarball
            );
            pb.directory(binDir.toFile());
            pb.inheritIO();
            Process p = pb.start();
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("Timeout extracting MariaDB tarball");
            }
            if (p.exitValue() != 0) {
                 throw new IOException("Failed to extract MariaDB tarball, exit code: " + p.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while extracting tarball", e);
        }
        
        // Cleanup
        Files.deleteIfExists(tarPath);
        
        // Ensure executable privileges
        setExecutable(binDir.resolve("bin/mariadbd"));
        setExecutable(binDir.resolve("bin/mysqld"));
        setExecutable(binDir.resolve("bin/mariadb-install-db")); // Preferred installer
        setExecutable(binDir.resolve("scripts/mysql_install_db")); 
        setExecutable(binDir.resolve("bin/mysqladmin")); // Required for shutdown
        setExecutable(binDir.resolve("bin/mariadb-admin")); // Newer name for shutdown
        
        LOGGER.info("MariaDB binaries installed successfully.");
    }
    
    private static void setExecutable(Path path) {
        if (Files.exists(path)) {
            try {
                if (OSUtils.isLinux()) {
                    Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(path));
                    perms.add(PosixFilePermission.OWNER_EXECUTE);
                    perms.add(PosixFilePermission.GROUP_EXECUTE);
                    perms.add(PosixFilePermission.OTHERS_EXECUTE);
                    Files.setPosixFilePermissions(path, perms);
                } else {
                    path.toFile().setExecutable(true);
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to set executable permission for {}", path, e);
            }
        }
    }
}
