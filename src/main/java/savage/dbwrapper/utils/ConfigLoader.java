package savage.dbwrapper.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static <T> T loadConfig(Path configPath, Class<T> configClass) {
        try {
            if (Files.exists(configPath)) {
                String configJson = Files.readString(configPath);
                return GSON.fromJson(configJson, configClass);
            } else {
                // Create default config
                T defaultConfig = configClass.getDeclaredConstructor().newInstance();
                saveConfig(configPath, defaultConfig);
                return defaultConfig;
            }
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
}