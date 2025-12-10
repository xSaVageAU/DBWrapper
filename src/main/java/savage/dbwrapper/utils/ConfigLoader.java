package savage.dbwrapper.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.dbwrapper.config.DBWrapperConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ALLOWED_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    public static <T> T loadConfig(Path configPath, Class<T> configClass) {
        try {
            T config;
            if (Files.exists(configPath)) {
                String configJson = Files.readString(configPath);
                config = GSON.fromJson(configJson, configClass);
            } else {
                // Create default config
                config = configClass.getDeclaredConstructor().newInstance();
                saveConfig(configPath, config);
            }

            // Check for default password and generate a new one
            if (config instanceof DBWrapperConfig) {
                DBWrapperConfig dbWrapperConfig = (DBWrapperConfig) config;
                if ("password".equals(dbWrapperConfig.getMariadb().getPassword()) || dbWrapperConfig.getMariadb().getPassword().isEmpty()) {
                    LOGGER.warn("Default password detected. Generating a new random password.");
                    String newPassword = generateRandomPassword(16);
                    dbWrapperConfig.getMariadb().setPassword(newPassword);
                    saveConfig(configPath, dbWrapperConfig);
                    LOGGER.info("Successfully generated and saved a new random password. Please check your config file.");
                }
            }
            return config;
        } catch (Exception e) {
            LOGGER.error("Failed to load config from " + configPath, e);
            try {
                return configClass.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                LOGGER.error("Failed to create default config", ex);
                throw new RuntimeException("Failed to load or create config", ex);
            }
        }
    }

    public static <T> void saveConfig(Path configPath, T config) {
        try {
            Files.createDirectories(configPath.getParent());
            String configJson = GSON.toJson(config);
            Files.writeString(configPath, configJson);
            LOGGER.info("Saved config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save config to " + configPath, e);
        }
    }

    private static String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALLOWED_CHARACTERS.charAt(RANDOM.nextInt(ALLOWED_CHARACTERS.length())));
        }
        return sb.toString();
    }
}