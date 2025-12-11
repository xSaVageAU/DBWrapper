package savage.dbwrapper.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import savage.dbwrapper.config.DBWrapperConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBWrapperCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, DBWrapperConfig config, Logger logger) {
        dispatcher.register(CommandManager.literal("dbwrapper")
                .requires(source -> source.hasPermissionLevel(4))
                .then(CommandManager.literal("database")
                        .then(CommandManager.literal("mariadb")
                                .then(CommandManager.literal("create")
                                        .then(CommandManager.argument("database_name", StringArgumentType.word())
                                                .executes(context -> createDatabase(context.getSource(), StringArgumentType.getString(context, "database_name"), config, logger))
                                        )
                                )
                        )
                )
        );
    }

    private static int createDatabase(ServerCommandSource source, String databaseName, DBWrapperConfig config, Logger logger) {
        if (!isValidDatabaseName(databaseName)) {
            source.sendError(Text.literal("Invalid database name. Only alphanumeric characters and underscores are allowed."));
            return 0;
        }

        DBWrapperConfig.MariaDBConfig mariaDBConfig = config.getMariadb();

        String url = "jdbc:mariadb://localhost:" + mariaDBConfig.getPort() + "/";
        String user = mariaDBConfig.getUsername();
        String password = mariaDBConfig.getPassword();

        Connection conn = null;
        Statement stmt = null;
        try {
            source.sendFeedback(() -> Text.literal("Connecting to MariaDB..."), false);
            conn = DriverManager.getConnection(url, user, password);
            source.sendFeedback(() -> Text.literal("Connection successful. Creating database '" + databaseName + "'..."), false);

            stmt = conn.createStatement();
            String sql = "CREATE DATABASE `" + databaseName + "`";
            stmt.executeUpdate(sql);

            source.sendFeedback(() -> Text.literal("Database '" + databaseName + "' created successfully."), true);
            return 1;
        } catch (SQLException se) {
            source.sendError(Text.literal("Error creating database: " + se.getMessage()));
            logger.error("Error creating database: " + se.getMessage(), se);
            return 0;
        } catch (Exception e) {
            source.sendError(Text.literal("An unexpected error occurred: " + e.getMessage()));
            logger.error("An unexpected error occurred: " + e.getMessage(), e);
            return 0;
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException se2) {
                logger.error("Error closing statement: " + se2.getMessage(), se2);
            }
            try {
                if (conn != null) conn.close();
            } catch (SQLException se) {
                logger.error("Error closing connection: " + se.getMessage(), se);
            }
        }
    }

    private static boolean isValidDatabaseName(String databaseName) {
        return databaseName.matches("^[a-zA-Z0-9_]+$");
    }
}
