package savage.dbwrapper.config;

import com.google.gson.annotations.SerializedName;

public class DatabaseConfig {
    @SerializedName("database_type")
    private String databaseType = "mariadb";

    @SerializedName("host")
    private String host = "localhost";

    @SerializedName("port")
    private int port = 3307;

    @SerializedName("username")
    private String username = "root";

    @SerializedName("password")
    private String password = "password";

    @SerializedName("database_name")
    private String databaseName = "minecraft";

    @SerializedName("installed")
    private boolean installed = false;

    @SerializedName("started")
    private boolean started = false;

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }
}