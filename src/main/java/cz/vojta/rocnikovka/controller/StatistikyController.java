package cz.vojta.rocnikovka.controller;

import cz.vojta.rocnikovka.Database;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class StatistikyController {

    @FXML private Label consumptionLabel;
    @FXML private Label costsLabel;
    @FXML private Label distanceLabel;
    @FXML private Label fuelingCostsLabel;
    @FXML private Label serviceCostsLabel;
    @FXML private Label otherCostsLabel;
    @FXML private Label fuelingCountLabel;
    @FXML private Label vehicleCountLabel;
    @FXML private BarChart<String, Number> monthlyChart;
    @FXML private PieChart categoryChart;
    @FXML private BarChart<String, Number> vehicleChart;
    @FXML private Label monthlyHint;
    @FXML private Label categoryHint;
    @FXML private Label vehicleHint;

    private int userId = -1;

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MM/yy");

    public void setUserId(int userId) {
        this.userId = userId;
        refreshStatistics();
    }

    @FXML
    private void initialize() {
        monthlyChart.setAnimated(false);
        categoryChart.setAnimated(false);
        vehicleChart.setAnimated(false);
        monthlyChart.setLegendVisible(false);
        vehicleChart.setLegendVisible(false);
        monthlyChart.setCategoryGap(12);
        vehicleChart.setCategoryGap(14);
        showEmptyState();
    }

    @FXML
    private void handleRefresh() {
        refreshStatistics();
    }

    private void refreshStatistics() {
        if (userId <= 0) {
            showEmptyState();
            return;
        }

        try (Connection connection = Database.connect()) {
            ensureOtherCostsTable(connection);

            List<FuelingData> fuelings = loadFuelings(connection);
            List<ServiceData> services = loadServices(connection);
            List<OtherCostData> otherCosts = loadOtherCosts(connection);
            List<VehicleData> vehicles = loadVehicles(connection);

            double fuelCosts = fuelings.stream().mapToDouble(f -> f.totalPrice).sum();
            double serviceCosts = services.stream()
                    .filter(s -> "Probíhá".equals(s.status) || "Dokončeno".equals(s.status))
                    .mapToDouble(s -> s.totalCost).sum();
            double otherCostsTotal = otherCosts.stream().mapToDouble(c -> c.amount).sum();
            double totalCosts = fuelCosts + serviceCosts + otherCostsTotal;

            int distance = calculateDistance(vehicles, fuelings, services);
            double consumption = calculateConsumption(fuelings);

            consumptionLabel.setText(consumption > 0 ? decimal(consumption) + " l / 100 km" : "—");
            costsLabel.setText(money(totalCosts) + " Kč");
            distanceLabel.setText(number(distance) + " km");
            fuelingCostsLabel.setText(money(fuelCosts) + " Kč");
            serviceCostsLabel.setText(money(serviceCosts) + " Kč");
            otherCostsLabel.setText(money(otherCostsTotal) + " Kč");
            fuelingCountLabel.setText(String.valueOf(fuelings.size()));
            vehicleCountLabel.setText(String.valueOf(vehicles.size()));

            buildMonthlyChart(fuelings, services, otherCosts);
            buildCategoryChart(fuelCosts, serviceCosts, otherCosts);
            buildVehicleChart(vehicles, fuelings, services, otherCosts);

            monthlyHint.setText(fuelings.isEmpty() && services.isEmpty() && otherCosts.isEmpty()
                    ? "Po přidání záznamů se zde zobrazí vývoj nákladů."
                    : "Zahrnuje tankování, probíhající a dokončený servis a ostatní náklady.");
            categoryHint.setText(totalCosts <= 0
                    ? "Zatím nejsou zaznamenané žádné náklady."
                    : "Rozdělení skutečně evidovaných nákladů.");
            vehicleHint.setText(vehicles.isEmpty()
                    ? "Nejdřív přidej vozidlo."
                    : "Porovnání nákladů u vozidel, ke kterým máš přístup.");

        } catch (SQLException e) {
            e.printStackTrace();
            showDatabaseError();
        }
    }

    private List<FuelingData> loadFuelings(Connection connection) throws SQLException {
        String sql = """
                SELECT f.vehicle_id, f.fueling_date, f.odometer, f.liters, f.total_price, f.full_tank
                FROM fuelings f
                JOIN vehicles v ON v.id = f.vehicle_id
                WHERE v.user_id = ?
                   OR v.id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)
                ORDER BY f.vehicle_id, f.fueling_date, f.id
                """;

        List<FuelingData> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new FuelingData(
                            rs.getInt("vehicle_id"),
                            safe(rs.getString("fueling_date")),
                            rs.getInt("odometer"),
                            rs.getDouble("liters"),
                            rs.getDouble("total_price"),
                            rs.getInt("full_tank") == 1
                    ));
                }
            }
        }
        return result;
    }

    private List<ServiceData> loadServices(Connection connection) throws SQLException {
        String sql = """
                SELECT s.vehicle_id, s.status, s.total_cost, s.odometer,
                       COALESCE(s.completed_date, s.started_date, s.planned_start, date('now')) AS cost_date
                FROM services s
                JOIN vehicles v ON v.id = s.vehicle_id
                WHERE v.user_id = ?
                   OR v.id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)
                """;

        List<ServiceData> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new ServiceData(
                            rs.getInt("vehicle_id"),
                            safe(rs.getString("status")),
                            rs.getDouble("total_cost"),
                            rs.getInt("odometer"),
                            safe(rs.getString("cost_date"))
                    ));
                }
            }
        }
        return result;
    }

    private List<OtherCostData> loadOtherCosts(Connection connection) throws SQLException {
        String sql = """
                SELECT o.vehicle_id, o.category, o.cost_date, o.amount
                FROM other_costs o
                JOIN vehicles v ON v.id = o.vehicle_id
                WHERE v.user_id = ?
                   OR v.id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)
                """;

        List<OtherCostData> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new OtherCostData(
                            rs.getInt("vehicle_id"),
                            safe(rs.getString("category")),
                            safe(rs.getString("cost_date")),
                            rs.getDouble("amount")
                    ));
                }
            }
        }
        return result;
    }

    private List<VehicleData> loadVehicles(Connection connection) throws SQLException {
        String sql = """
                SELECT id, brand, model, registration, odometer
                FROM vehicles
                WHERE user_id = ?
                   OR id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)
                ORDER BY brand, model
                """;

        List<VehicleData> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new VehicleData(
                            rs.getInt("id"),
                            rs.getString("brand"),
                            rs.getString("model"),
                            safe(rs.getString("registration")),
                            rs.getInt("odometer")
                    ));
                }
            }
        }
        return result;
    }

    private int calculateDistance(List<VehicleData> vehicles, List<FuelingData> fuelings,
                                  List<ServiceData> services) {
        Map<Integer, Integer> minimum = new HashMap<>();
        Map<Integer, Integer> maximum = new HashMap<>();

        for (VehicleData vehicle : vehicles) {
            maximum.put(vehicle.id, vehicle.odometer);
        }
        for (FuelingData fueling : fuelings) {
            if (fueling.odometer > 0) {
                minimum.merge(fueling.vehicleId, fueling.odometer, Math::min);
                maximum.merge(fueling.vehicleId, fueling.odometer, Math::max);
            }
        }
        for (ServiceData service : services) {
            if (service.odometer > 0) {
                minimum.merge(service.vehicleId, service.odometer, Math::min);
                maximum.merge(service.vehicleId, service.odometer, Math::max);
            }
        }

        int total = 0;
        for (VehicleData vehicle : vehicles) {
            Integer min = minimum.get(vehicle.id);
            Integer max = maximum.get(vehicle.id);
            if (min != null && max != null && max >= min) {
                total += max - min;
            }
        }
        return total;
    }

    private double calculateConsumption(List<FuelingData> fuelings) {
        Map<Integer, List<FuelingData>> byVehicle = new HashMap<>();
        for (FuelingData fueling : fuelings) {
            byVehicle.computeIfAbsent(fueling.vehicleId, ignored -> new ArrayList<>()).add(fueling);
        }

        double totalLiters = 0;
        int totalDistance = 0;

        for (List<FuelingData> list : byVehicle.values()) {
            list.sort(Comparator.comparing((FuelingData f) -> f.date)
                    .thenComparingInt(f -> f.odometer));

            FuelingData previousFull = null;
            double litersSinceFull = 0;

            for (FuelingData fueling : list) {
                litersSinceFull += fueling.liters;
                if (fueling.fullTank && previousFull != null) {
                    int distance = fueling.odometer - previousFull.odometer;
                    if (distance > 0 && litersSinceFull > 0) {
                        totalLiters += litersSinceFull;
                        totalDistance += distance;
                    }
                    litersSinceFull = 0;
                    previousFull = fueling;
                } else if (fueling.fullTank) {
                    previousFull = fueling;
                    litersSinceFull = 0;
                }
            }
        }

        if (totalDistance <= 0) {
            return 0;
        }
        return totalLiters / totalDistance * 100.0;
    }

    private void buildMonthlyChart(List<FuelingData> fuelings, List<ServiceData> services,
                                   List<OtherCostData> otherCosts) {
        Map<YearMonth, Double> monthly = new TreeMap<>();

        for (FuelingData fueling : fuelings) {
            addMonthly(monthly, fueling.date, fueling.totalPrice);
        }
        for (ServiceData service : services) {
            if ("Probíhá".equals(service.status) || "Dokončeno".equals(service.status)) {
                addMonthly(monthly, service.date, service.totalCost);
            }
        }
        for (OtherCostData cost : otherCosts) {
            addMonthly(monthly, cost.date, cost.amount);
        }

        monthlyChart.getData().clear();
        if (monthly.isEmpty()) {
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/yy");
        monthly.forEach((month, amount) ->
                series.getData().add(new XYChart.Data<>(month.format(formatter), amount)));
        monthlyChart.getData().add(series);
    }

    private void buildCategoryChart(double fuelCosts, double serviceCosts, List<OtherCostData> otherCosts) {
        Map<String, Double> categories = new LinkedHashMap<>();
        if (fuelCosts > 0) {
            categories.put("Tankování", fuelCosts);
        }
        if (serviceCosts > 0) {
            categories.put("Servis", serviceCosts);
        }
        for (OtherCostData cost : otherCosts) {
            if (cost.amount > 0) {
                categories.merge(cost.category.isBlank() ? "Ostatní" : cost.category,
                        cost.amount, Double::sum);
            }
        }

        categoryChart.setData(FXCollections.observableArrayList());
        categories.forEach((name, amount) ->
                categoryChart.getData().add(new PieChart.Data(name + " (" + money(amount) + " Kč)", amount)));
    }

    private void buildVehicleChart(List<VehicleData> vehicles, List<FuelingData> fuelings,
                                   List<ServiceData> services, List<OtherCostData> otherCosts) {
        Map<Integer, Double> costs = new HashMap<>();

        for (FuelingData fueling : fuelings) {
            costs.merge(fueling.vehicleId, fueling.totalPrice, Double::sum);
        }
        for (ServiceData service : services) {
            if ("Probíhá".equals(service.status) || "Dokončeno".equals(service.status)) {
                costs.merge(service.vehicleId, service.totalCost, Double::sum);
            }
        }
        for (OtherCostData cost : otherCosts) {
            costs.merge(cost.vehicleId, cost.amount, Double::sum);
        }

        vehicleChart.getData().clear();
        if (vehicles.isEmpty()) {
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (VehicleData vehicle : vehicles) {
            String label = vehicle.brand + " " + vehicle.model;
            series.getData().add(new XYChart.Data<>(label, costs.getOrDefault(vehicle.id, 0.0)));
        }
        vehicleChart.getData().add(series);
    }

    private void addMonthly(Map<YearMonth, Double> monthly, String date, double amount) {
        try {
            YearMonth month = YearMonth.from(LocalDate.parse(date));
            monthly.merge(month, amount, Double::sum);
        } catch (Exception ignored) {
            // neplatne datum se do mesicniho grafu nezahrne
        }
    }

    private void ensureOtherCostsTable(Connection connection) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS other_costs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
                    title TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT 'Ostatní',
                    cost_date TEXT NOT NULL,
                    amount REAL NOT NULL DEFAULT 0,
                    description TEXT,
                    created_by INTEGER REFERENCES users(id)
                )
                """;
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void showEmptyState() {
        consumptionLabel.setText("—");
        costsLabel.setText("0 Kč");
        distanceLabel.setText("0 km");
        fuelingCostsLabel.setText("0 Kč");
        serviceCostsLabel.setText("0 Kč");
        otherCostsLabel.setText("0 Kč");
        fuelingCountLabel.setText("0");
        vehicleCountLabel.setText("0");
        monthlyChart.getData().clear();
        categoryChart.getData().clear();
        vehicleChart.getData().clear();
        if (monthlyHint != null) monthlyHint.setText("Po přidání záznamů se zde zobrazí vývoj nákladů.");
        if (categoryHint != null) categoryHint.setText("Zatím nejsou zaznamenané žádné náklady.");
        if (vehicleHint != null) vehicleHint.setText("Zatím nejsou dostupná žádná vozidla.");
    }

    private void showDatabaseError() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Chyba databáze");
        alert.setHeaderText("Statistiky se nepodařilo načíst");
        alert.setContentText("Zkontroluj databázové tabulky a konzoli aplikace.");
        alert.showAndWait();
    }

    private static String money(double value) {
        return String.format(Locale.forLanguageTag("cs-CZ"), "%,.2f", value).replace('\u00a0', ' ');
    }

    private static String decimal(double value) {
        return String.format(Locale.forLanguageTag("cs-CZ"), "%,.2f", value).replace('\u00a0', ' ');
    }

    private static String number(int value) {
        return String.format(Locale.forLanguageTag("cs-CZ"), "%,d", value).replace('\u00a0', ' ');
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static class FuelingData {
        final int vehicleId;
        final String date;
        final int odometer;
        final double liters;
        final double totalPrice;
        final boolean fullTank;

        FuelingData(int vehicleId, String date, int odometer, double liters, double totalPrice, boolean fullTank) {
            this.vehicleId = vehicleId;
            this.date = date;
            this.odometer = odometer;
            this.liters = liters;
            this.totalPrice = totalPrice;
            this.fullTank = fullTank;
        }
    }

    private static class ServiceData {
        final int vehicleId;
        final String status;
        final double totalCost;
        final int odometer;
        final String date;

        ServiceData(int vehicleId, String status, double totalCost, int odometer, String date) {
            this.vehicleId = vehicleId;
            this.status = status;
            this.totalCost = totalCost;
            this.odometer = odometer;
            this.date = date;
        }
    }

    private static class OtherCostData {
        final int vehicleId;
        final String category;
        final String date;
        final double amount;

        OtherCostData(int vehicleId, String category, String date, double amount) {
            this.vehicleId = vehicleId;
            this.category = category;
            this.date = date;
            this.amount = amount;
        }
    }

    private static class VehicleData {
        final int id;
        final String brand;
        final String model;
        final String registration;
        final int odometer;

        VehicleData(int id, String brand, String model, String registration, int odometer) {
            this.id = id;
            this.brand = brand;
            this.model = model;
            this.registration = registration;
            this.odometer = odometer;
        }
    }
}
