package com.friendfinder.util;

import java.sql.*;

public final class DatabaseConnection {
    private DatabaseConnection() {}
    public static Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(Config.get("db.url", "jdbc:postgresql://127.0.0.1:5432/it_friend_finder"),
            Config.get("db.user", "postgres"), Config.get("db.password", ""));
        try (Statement statement = connection.createStatement()) { statement.execute("SET TIME ZONE 'UTC'"); }
        catch (SQLException e) { connection.close(); throw e; }
        return connection;
    }
}
