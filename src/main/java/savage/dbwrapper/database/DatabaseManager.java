package savage.dbwrapper.database;

public interface DatabaseManager {
    void initialize();
    void installDatabase();
    void startDatabase();
    void stopDatabase();
    boolean isDatabaseRunning();
    String getDatabaseType();
}