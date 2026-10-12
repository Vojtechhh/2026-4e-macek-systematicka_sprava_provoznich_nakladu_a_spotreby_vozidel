package cz.vojta.rocnikovka.controller;

import cz.vojta.rocnikovka.Database;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DashboardController {

    @FXML
    private Label userNameLabel;

    @FXML
    private Label vehicleCountLabel;

    @FXML
    private Label fuelingCountLabel;

    @FXML
    private Label totalCostsLabel;

    @FXML
    private VBox vehiclesContainer;

    @FXML
    private VBox emptyState;

    private String username;
    private int userId = -1;
    private Runnable onAddVehicle;

    public void setUsername(String username) {
        this.username = username;

        if (userNameLabel != null) {
            userNameLabel.setText(
                    username == null || username.isBlank()
                            ? "Uživatel"
                            : username
            );
        }
    }

    public void setUserId(int userId) {
        this.userId = userId;
        loadDashboard();
    }

    public void setOnAddVehicle(Runnable onAddVehicle) {
        this.onAddVehicle = onAddVehicle;
    }

    @FXML
    private void handleAddVehicle() {
        if (onAddVehicle != null) {
            onAddVehicle.run();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Přidat vozidlo");
        alert.setHeaderText(null);
        alert.setContentText("Otevři sekci Vozidla a přidej nové vozidlo.");
        alert.showAndWait();
    }

    private void loadDashboard() {
        if (userId <= 0) {
            showEmptyDashboard();
            return;
        }

        try (Connection connection = Database.connect()) {
            ensureOtherCostsTable(connection);

            List<VehicleCard> vehicles = loadVehicles(connection);
            int fuelingCount = countFuelings(connection);

            double fuelCosts = sumQuery(connection, """
                    SELECT COALESCE(SUM(f.total_price), 0)
                    FROM fuelings f
                    JOIN vehicles v ON v.id = f.vehicle_id
                    LEFT JOIN vehicle_shares vs ON vs.vehicle_id = v.id
                    WHERE v.user_id = ? OR vs.user_id = ?
                    """);

            double serviceCosts = sumQuery(connection, """
                    SELECT COALESCE(SUM(s.total_cost), 0)
                    FROM services s
                    JOIN vehicles v ON v.id = s.vehicle_id
                    LEFT JOIN vehicle_shares vs ON vs.vehicle_id = v.id
                    WHERE (v.user_id = ? OR vs.user_id = ?)
                      AND s.status IN ('Probíhá', 'Dokončeno')
                    """);

            double otherCosts = sumQuery(connection, """
                    SELECT COALESCE(SUM(oc.amount), 0)
                    FROM other_costs oc
                    JOIN vehicles v ON v.id = oc.vehicle_id
                    LEFT JOIN vehicle_shares vs ON vs.vehicle_id = v.id
                    WHERE v.user_id = ? OR vs.user_id = ?
                    """);

            vehicleCountLabel.setText(String.valueOf(vehicles.size()));
            fuelingCountLabel.setText(String.valueOf(fuelingCount));
            totalCostsLabel.setText(
                    formatMoney(fuelCosts + serviceCosts + otherCosts) + " Kč"
            );

            showVehicleCards(vehicles);

        } catch (SQLException e) {
            e.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Chyba databáze");
            alert.setHeaderText("Dashboard se nepodařilo načíst");
            alert.setContentText(
                    "Podrobnosti najdeš ve výpisu v terminálu."
            );
            alert.showAndWait();
        }
    }

    private List<VehicleCard> loadVehicles(Connection connection)
            throws SQLException {
        String sql = """
                SELECT DISTINCT
                    v.id,
                    v.brand,
                    v.model,
                    v.registration,
                    v.year,
                    v.odometer
                FROM vehicles v
                LEFT JOIN vehicle_shares vs ON vs.vehicle_id = v.id
                WHERE v.user_id = ? OR vs.user_id = ?
                ORDER BY v.id DESC
                """;

        List<VehicleCard> vehicles = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    vehicles.add(new VehicleCard(
                            result.getInt("id"),
                            result.getString("brand"),
                            result.getString("model"),
                            result.getString("registration"),
                            result.getString("year"),
                            result.getString("odometer")
                    ));
                }
            }
        }

        return vehicles;
    }

    private int countFuelings(Connection connection) throws SQLException {
        String sql = """
                SELECT COUNT(DISTINCT f.id)
                FROM fuelings f
                JOIN vehicles v ON v.id = f.vehicle_id
                LEFT JOIN vehicle_shares vs ON vs.vehicle_id = v.id
                WHERE v.user_id = ? OR vs.user_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private double sumQuery(Connection connection, String sql)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getDouble(1) : 0;
            }
        }
    }

    private void ensureOtherCostsTable(Connection connection)
            throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS other_costs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    vehicle_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT 'Ostatní',
                    amount REAL NOT NULL DEFAULT 0,
                    cost_date TEXT NOT NULL,
                    description TEXT,
                    created_by INTEGER,
                    FOREIGN KEY (vehicle_id) REFERENCES vehicles(id)
                )
                """;

        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void showVehicleCards(List<VehicleCard> vehicles) {
        vehiclesContainer.getChildren().clear();

        boolean noVehicles = vehicles.isEmpty();
        emptyState.setVisible(noVehicles);
        emptyState.setManaged(noVehicles);

        if (noVehicles) {
            return;
        }

        for (VehicleCard vehicle : vehicles) {
            Label title = new Label(
                    value(vehicle.brand) + " " + value(vehicle.model)
            );
            title.getStyleClass().add("section-title");

            Label details = new Label(
                    "SPZ: " + value(vehicle.registration)
                            + "   •   Rok: " + value(vehicle.year)
                            + "   •   Tachometr: " + value(vehicle.odometer) + " km"
            );
            details.getStyleClass().add("stat-description");

            VBox card = new VBox(8, title, details);
            card.getStyleClass().add("stat-card");
            card.setMaxWidth(Double.MAX_VALUE);

            // dvojklikem otevri sekci vozidel v hlavnim menu
            card.setCursor(Cursor.HAND);
            card.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && onAddVehicle != null) {
                    onAddVehicle.run();
                }
            });

            vehiclesContainer.getChildren().add(card);
        }
    }

    private void showEmptyDashboard() {
        if (vehicleCountLabel != null) {
            vehicleCountLabel.setText("0");
        }

        if (fuelingCountLabel != null) {
            fuelingCountLabel.setText("0");
        }

        if (totalCostsLabel != null) {
            totalCostsLabel.setText("0 Kč");
        }

        if (vehiclesContainer != null) {
            vehiclesContainer.getChildren().clear();
        }

        if (emptyState != null) {
            emptyState.setVisible(true);
            emptyState.setManaged(true);
        }
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String formatMoney(double value) {
        return String.format(Locale.forLanguageTag("cs-CZ"), "%,.2f", value)
                .replace(',', '#')
                .replace('.', ',')
                .replace('#', ' ');
    }

    private static class VehicleCard {

        private final int id;
        private final String brand;
        private final String model;
        private final String registration;
        private final String year;
        private final String odometer;

        private VehicleCard(
                int id,
                String brand,
                String model,
                String registration,
                String year,
                String odometer
        ) {
            this.id = id;
            this.brand = brand;
            this.model = model;
            this.registration = registration;
            this.year = year;
            this.odometer = odometer;
        }
    }
}
