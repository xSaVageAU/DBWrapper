package savage.dbwrapper.config;

import com.google.gson.annotations.SerializedName;

public class DBWrapperConfig {
    @SerializedName("auto_start")
    private boolean autoStart = true;

    @SerializedName("debug_logging")
    private boolean debugLogging = false;

    @SerializedName("mariadb")
    private MariaDBConfig mariadb = new MariaDBConfig();

    @SerializedName("redis")
    private RedisConfig redis = new RedisConfig();

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

    public MariaDBConfig getMariadb() {
        return mariadb;
    }

    public void setMariadb(MariaDBConfig mariadb) {
        this.mariadb = mariadb;
    }

    public RedisConfig getRedis() {
        return redis;
    }

    public void setRedis(RedisConfig redis) {
        this.redis = redis;
    }

    public static class MariaDBConfig {
        @SerializedName("enabled")
        private boolean enabled = true;

        @SerializedName("port")
        private int port = 3307;

        @SerializedName("username")
        private String username = "root";

        @SerializedName("password")
        private String password = "password";

        @SerializedName("database_name")
        private String databaseName = "minecraft";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
    }

    public static class RedisConfig {
        @SerializedName("enabled")
        private boolean enabled = false;

        @SerializedName("port")
        private int port = 6379;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }
}