package savage.dbwrapper.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ProcessUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessUtils.class);

    public static void logProcessOutput(Process process, String processName) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[" + processName + "] " + line);
                }
            } catch (IOException e) {
                LOGGER.error("Error reading output from " + processName, e);
            }
        }).start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.error("[" + processName + " ERROR] " + line);
                }
            } catch (IOException e) {
                LOGGER.error("Error reading error output from " + processName, e);
            }
        }).start();
    }

    public static boolean waitForProcess(Process process, long timeout, TimeUnit unit) {
        try {
            return process.waitFor(timeout, unit);
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while waiting for process", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void destroyProcess(Process process) {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted while destroying process", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}