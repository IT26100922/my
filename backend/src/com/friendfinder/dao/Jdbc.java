package com.friendfinder.dao;

import java.sql.*;
import java.util.*;
import com.friendfinder.util.DatabaseConnection;

/** Shared mechanics only; each DAO owns its SQL. Values always use placeholders. */
public final class Jdbc {
    private Jdbc() {}
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }
    public static <T> T transaction(Work<T> work) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try { T result = work.run(connection); connection.commit(); return result; }
            catch (SQLException | RuntimeException e) { connection.rollback(); throw e; }
        }
    }
    public static List<Map<String,Object>> query(String sql, Object... values) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) { return query(connection, sql, values); }
    }
    public static List<Map<String,Object>> query(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet results = statement.executeQuery()) {
                List<Map<String,Object>> rows = new ArrayList<>(); ResultSetMetaData metadata = results.getMetaData();
                while (results.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) row.put(metadata.getColumnLabel(i), results.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    public static int update(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) { bind(statement, values); return statement.executeUpdate(); }
    }
    public static long insert(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values); statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Missing generated key"); return keys.getLong(1); }
        }
    }
    private static void bind(PreparedStatement statement, Object... values) throws SQLException { for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]); }
    public static Map<String,Object> first(List<Map<String,Object>> rows) { return rows.isEmpty() ? null : rows.get(0); }
    public static long number(Map<String,Object> row, String key) { Object value = row.get(key); return value == null ? 0 : ((Number)value).longValue(); }
    public static boolean bool(Map<String,Object> row, String key) { Object value = row.get(key); return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0; }
}
