package savage.dbwrapper.database;

import savage.dbwrapper.config.DatabaseConfig;
import savage.dbwrapper.config.InstallationProgress;

public interface DatabaseManager {
    void initialize();
    void installDatabase();
    void startDatabase();
    void stopDatabase();
    boolean isDatabaseRunning();
    DatabaseConfig getDatabaseConfig();
    InstallationProgress getInstallationProgress();
    String getDatabaseType();
}