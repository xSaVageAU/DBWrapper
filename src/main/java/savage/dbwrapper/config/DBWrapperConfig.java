package savage.dbwrapper.config;

import com.google.gson.annotations.SerializedName;

public class DBWrapperConfig {
    @SerializedName("enable_mariadb")
    private boolean enableMariaDB = false;

    @SerializedName("database_port")
    private int databasePort = 3307;

    @SerializedName("auto_start")
    private boolean autoStart = true;

    @SerializedName("debug_logging")
    private boolean debugLogging = false;

    public boolean isEnableMariaDB() {
        return enableMariaDB;
    }

    public void setEnableMariaDB(boolean enableMariaDB) {
        this.enableMariaDB = enableMariaDB;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    public void setDatabasePort(int databasePort) {
        this.databasePort = databasePort;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }
}