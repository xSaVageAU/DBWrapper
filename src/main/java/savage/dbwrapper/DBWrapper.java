package savage.dbwrapper;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import savage.dbwrapper.commands.DBWrapperCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import savage.dbwrapper.config.DBWrapperConfig;
import savage.dbwrapper.database.DatabaseManager;
import savage.dbwrapper.database.mariadb.MariaDBManager;
import savage.dbwrapper.database.redis.RedisManager;
import savage.dbwrapper.utils.ConfigLoader;

import java.nio.file.Path;

public class DBWrapper implements ModInitializer, PreLaunchEntrypoint {
	public static final String MOD_ID = "dbwrapper";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static DatabaseManager databaseManager;
	private static DBWrapperConfig config;
	private static final Path CONFIG_DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
	private static final Path CONFIG_FILE = CONFIG_DIRECTORY.resolve("config.json");
	private static boolean shutdownHookRegistered = false;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("DBWrapper initializing...");

		// Initialize configuration
		loadConfiguration();

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			DBWrapperCommands.register(dispatcher, config);
		});

		// Initialize database manager based on config
		if (config.getMariadb().isEnabled()) {
			databaseManager = new MariaDBManager();
			((MariaDBManager) databaseManager).setConfig(config);
			databaseManager.initialize();

			// Register shutdown hook for proper cleanup
			if (!shutdownHookRegistered) {
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					LOGGER.info("Shutdown hook triggered - cleaning up database processes");
					if (databaseManager != null && databaseManager.isDatabaseRunning()) {
						try {
							databaseManager.stopDatabase();
						} catch (Exception e) {
							LOGGER.error("Error during shutdown hook database cleanup", e);
						}
					}
				}));
				shutdownHookRegistered = true;
			}

			if (config.isAutoStart()) {
				startDatabaseServices();
			}
		}

		if (config.getRedis().isEnabled()) {
			// Create separate Redis manager
			RedisManager redisManager = new RedisManager();
			redisManager.setConfig(config);
			redisManager.initialize();

			// Start Redis if auto-start is enabled
			if (config.isAutoStart()) {
				LOGGER.info("Starting Redis database...");
				redisManager.installDatabase();
				redisManager.startDatabase();
			}
		}

		// Register server lifecycle events
		registerServerEvents();
	}


	@Override
	public void onPreLaunch() {
		// Pre-launch initialization
		LOGGER.info("DBWrapper pre-launch initialization");
	}

	private void loadConfiguration() {
		try {
			// Load main config
			config = ConfigLoader.loadConfig(CONFIG_FILE, DBWrapperConfig.class);

			LOGGER.info("Configuration loaded: MariaDB enabled = {}, Redis enabled = {}, auto start = {}",
			        config.getMariadb().isEnabled(), config.getRedis().isEnabled(), config.isAutoStart());
			LOGGER.info("DBWrapper configuration: mariadb.port={}, redis.port={}",
			        config.getMariadb().getPort(), config.getRedis().getPort());
		} catch (Exception e) {
			LOGGER.error("Failed to load configuration, using defaults", e);
			config = new DBWrapperConfig();
		}
	}

	private void startDatabaseServices() {
		if (databaseManager == null) {
			LOGGER.error("No database manager initialized");
			return;
		}

		// Install database if needed
		LOGGER.info("Installing database...");
		databaseManager.installDatabase();

		// Start database if not running
		if (!databaseManager.isDatabaseRunning()) {
			LOGGER.info("Starting database...");
			databaseManager.startDatabase();
		}
	}

	private void stopDatabaseServices() {
		if (databaseManager != null && databaseManager.isDatabaseRunning()) {
			LOGGER.info("Stopping database...");
			databaseManager.stopDatabase();
		}
	}

	private void registerServerEvents() {
		// Register server stopping event to clean up database
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			stopDatabaseServices();
			LOGGER.info("DBWrapper cleanup completed");
		});
	}

	public static DatabaseManager getDatabaseManager() {
		return databaseManager;
	}

	public static DBWrapperConfig getConfig() {
		return config;
	}
}