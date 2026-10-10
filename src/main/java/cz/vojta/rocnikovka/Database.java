package cz.vojta.rocnikovka;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private static final String URL = "jdbc:sqlite:vehicle_manager.db";
    private static final String LEGACY_OWNER_USERNAME = "test_user1";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initialize() {
        String usersSql = """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL
                )
                """;
        String vehiclesSql = """
                CREATE TABLE IF NOT EXISTS vehicles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    brand TEXT NOT NULL,
                    model TEXT NOT NULL,
                    registration TEXT NOT NULL,
                    year INTEGER NOT NULL,
                    odometer INTEGER NOT NULL,
                    photo_path TEXT,
                    engine TEXT,
                    fuel_type TEXT,
                    vin TEXT,
                    color TEXT,
                    notes TEXT,
                    user_id INTEGER REFERENCES users(id)
                )
                """;
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute(usersSql);
            statement.execute(vehiclesSql);
            addColumnIfMissing(connection, "photo_path", "TEXT");
            addColumnIfMissing(connection, "engine", "TEXT");
            addColumnIfMissing(connection, "fuel_type", "TEXT");
            addColumnIfMissing(connection, "vin", "TEXT");
            addColumnIfMissing(connection, "color", "TEXT");
            addColumnIfMissing(connection, "notes", "TEXT");
            addColumnIfMissing(connection, "user_id", "INTEGER REFERENCES users(id)");
            assignLegacyVehicles(connection);
            System.out.println("Databáze byla připravena.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void assignLegacyVehicles(Connection connection) throws SQLException {
        String sql = """
                UPDATE vehicles
                SET user_id = (SELECT id FROM users WHERE username = ?)
                WHERE user_id IS NULL
                  AND EXISTS (SELECT 1 FROM users WHERE username = ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, LEGACY_OWNER_USERNAME);
            statement.setString(2, LEGACY_OWNER_USERNAME);
            int updated = statement.executeUpdate();
            if (updated > 0) {
                System.out.println("Stávající vozidla byla přiřazena účtu " + LEGACY_OWNER_USERNAME + ".");
            }
        }
    }

    private static void addColumnIfMissing(Connection connection, String columnName, String columnType) throws SQLException {
        boolean exists = false;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(vehicles)")) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE vehicles ADD COLUMN " + columnName + " " + columnType);
            }
        }
    }
}
