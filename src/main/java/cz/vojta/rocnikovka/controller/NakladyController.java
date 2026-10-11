package cz.vojta.rocnikovka.controller;

import cz.vojta.rocnikovka.Database;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class NakladyController {
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilter;
    @FXML private Label countLabel;
    @FXML private Label totalLabel;
    @FXML private Label fuelingsLabel;
    @FXML private Label serviceLabel;
    @FXML private TableView<CostRow> costsTable;
    @FXML private TableColumn<CostRow, String> dateColumn;
    @FXML private TableColumn<CostRow, String> typeColumn;
    @FXML private TableColumn<CostRow, String> titleColumn;
    @FXML private TableColumn<CostRow, String> vehicleColumn;
    @FXML private TableColumn<CostRow, String> amountColumn;

    private final ObservableList<CostRow> rows = FXCollections.observableArrayList();
    private FilteredList<CostRow> filteredRows;
    private int userId = -1;

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<String> TYPES = List.of(
            "Všechny typy", "Tankování", "Servis", "Detailing", "Pojištění",
            "Poplatky", "Příslušenství", "Oprava", "Ostatní"
    );

    @FXML
    private void initialize() {
        dateColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().date));
        typeColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().type));
        titleColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().title));
        vehicleColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().vehicle));
        amountColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(money(d.getValue().amount) + " Kč"));

        filteredRows = new FilteredList<>(rows, row -> true);
        costsTable.setItems(filteredRows);

        typeFilter.setItems(FXCollections.observableArrayList(TYPES));
        typeFilter.getSelectionModel().selectFirst();
        typeFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        costsTable.setRowFactory(table -> {
            TableRow<CostRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    showDetails(row.getItem());
                }
            });
            return row;
        });

        ensureOtherCostsTable();
    }

    public void setUserId(int userId) {
        this.userId = userId;
        loadCosts();
    }

    @FXML
    private void handleAddCost() {
        showEditor(null);
    }

    @FXML
    private void handleEditCost() {
        CostRow row = costsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            alert(Alert.AlertType.INFORMATION, "Není vybrán náklad", "Nejdřív vyber náklad.");
            return;
        }
        if (!row.custom) {
            alert(Alert.AlertType.INFORMATION, "Automatický záznam", "Tankování a servis se upravují ve svých vlastních sekcích.");
            return;
        }
        showEditor(row);
    }

    @FXML
    private void handleDeleteCost() {
        CostRow row = costsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            alert(Alert.AlertType.INFORMATION, "Není vybrán náklad", "Nejdřív vyber náklad.");
            return;
        }
        if (!row.custom) {
            alert(Alert.AlertType.INFORMATION, "Automatický záznam", "Tankování a servis se mažou ve svých vlastních sekcích.");
            return;
        }
        deleteCost(row);
    }

    private void ensureOtherCostsTable() {
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
        try (Connection c = Database.connect(); Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Nepodařilo se připravit tabulku ostatních nákladů.");
        }
    }

    private void loadCosts() {
        rows.clear();
        if (userId <= 0) {
            updateStats();
            return;
        }

        String access = "(v.user_id = ? OR v.id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?))";

        String fuelSql = """
                SELECT f.id, f.fueling_date AS cost_date, f.total_price AS amount,
                       COALESCE(f.station, 'Tankování') AS title,
                       COALESCE(f.notes, '') AS description,
                       v.brand || ' ' || v.model || ' (' || COALESCE(v.registration, '') || ')' AS vehicle
                FROM fuelings f JOIN vehicles v ON v.id = f.vehicle_id
                WHERE v.user_id = ? OR v.id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)
                """;
        String serviceSql = """
                SELECT s.id,
                       COALESCE(s.completed_date, s.started_date, s.planned_start, date('now')) AS cost_date,
                       s.total_cost AS amount, s.title AS title,
                       COALESCE(s.description, '') AS description,
                       v.brand || ' ' || v.model || ' (' || COALESCE(v.registration, '') || ')' AS vehicle
                FROM services s JOIN vehicles v ON v.id = s.vehicle_id
                WHERE (v.user_id = ? OR v.id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?))
                  AND s.status IN ('Probíhá', 'Dokončeno')
                """;
        String otherSql = """
                SELECT o.id, o.cost_date, o.amount, o.title,
                       COALESCE(o.description, '') AS description,
                       o.category, v.brand || ' ' || v.model || ' (' || COALESCE(v.registration, '') || ')' AS vehicle
                FROM other_costs o JOIN vehicles v ON v.id = o.vehicle_id
                WHERE v.user_id = ? OR v.id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)
                """;

        try (Connection c = Database.connect()) {
            try (PreparedStatement ps = c.prepareStatement(fuelSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new CostRow(rs.getInt("id"), false, "Tankování",
                                displayDate(rs.getString("cost_date")), safe(rs.getString("title")),
                                safe(rs.getString("vehicle")), rs.getDouble("amount"),
                                safe(rs.getString("description")), "fueling"));
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(serviceSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new CostRow(rs.getInt("id"), false, "Servis",
                                displayDate(rs.getString("cost_date")), safe(rs.getString("title")),
                                safe(rs.getString("vehicle")), rs.getDouble("amount"),
                                safe(rs.getString("description")), "service"));
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(otherSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new CostRow(rs.getInt("id"), true, safe(rs.getString("category")),
                                displayDate(rs.getString("cost_date")), safe(rs.getString("title")),
                                safe(rs.getString("vehicle")), rs.getDouble("amount"),
                                safe(rs.getString("description")), "other"));
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Náklady se nepodařilo načíst. Zkontroluj tabulky fuelings a services.");
        }

        rows.sort((a, b) -> parseDate(b.date).compareTo(parseDate(a.date)));
        updateStats();
        applyFilters();
    }

    private void showEditor(CostRow old) {
        List<Vehicle> vehicles = loadVehicles();
        if (vehicles.isEmpty()) {
            alert(Alert.AlertType.INFORMATION, "Žádná vozidla", "Nejdřív přidej vozidlo nebo požádej vlastníka o sdílení.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(old == null ? "Přidat jiný náklad" : "Upravit náklad");
        dialog.setHeaderText("Zadej údaje o nákladu");
        ButtonType save = new ButtonType("Uložit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<Vehicle> vehicleBox = new ComboBox<>(FXCollections.observableArrayList(vehicles));
        vehicleBox.setMaxWidth(Double.MAX_VALUE);
        TextField title = new TextField(old == null ? "" : old.title);
        title.setPromptText("Např. detailing interiéru");
        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(
                "Detailing", "Pojištění", "Poplatky", "Příslušenství", "Oprava", "Ostatní"));
        category.setEditable(true);
        category.setMaxWidth(Double.MAX_VALUE);
        DatePicker date = new DatePicker(old == null ? LocalDate.now() : parseDate(old.date));
        TextField amount = new TextField(old == null ? "" : inputNumber(old.amount));
        amount.setPromptText("Např. 2500");
        TextArea description = new TextArea(old == null ? "" : old.description);
        description.setPromptText("Volitelný popis nebo poznámka");
        description.setPrefRowCount(3);

        if (old == null) {
            vehicleBox.getSelectionModel().selectFirst();
            category.setValue("Detailing");
        } else {
            vehicles.stream().filter(v -> v.label.equals(old.vehicle)).findFirst().ifPresent(vehicleBox::setValue);
            category.setValue(old.type);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        grid.addRow(0, new Label("Vozidlo"), vehicleBox);
        grid.addRow(1, new Label("Název nákladu"), title);
        grid.addRow(2, new Label("Kategorie"), category);
        grid.addRow(3, new Label("Datum"), date);
        grid.addRow(4, new Label("Částka (Kč)"), amount);
        grid.addRow(5, new Label("Popis"), description);
        grid.setPrefWidth(500);
        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                validate(vehicleBox, title, category, date, amount);
            } catch (IllegalArgumentException e) {
                event.consume();
                alert(Alert.AlertType.WARNING, "Zkontroluj údaje", e.getMessage());
            }
        });

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) {
            return;
        }

        try {
            Vehicle vehicle = vehicleBox.getValue();
            String costTitle = title.getText().trim();
            String costCategory = category.getEditor().getText().trim().isEmpty()
                    ? safe(category.getValue()) : category.getEditor().getText().trim();
            LocalDate costDate = date.getValue();
            double costAmount = parseAmount(amount.getText());

            if (old == null) {
                insertCost(vehicle.id, costTitle, costCategory, costDate, costAmount, description.getText().trim());
            } else {
                updateCost(old.id, vehicle.id, costTitle, costCategory, costDate, costAmount, description.getText().trim());
            }
            loadCosts();
        } catch (IllegalArgumentException e) {
            alert(Alert.AlertType.WARNING, "Zkontroluj údaje", e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Náklad se nepodařilo uložit.");
        }
    }

    private void showDetails(CostRow row) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Detail nákladu");
        dialog.setHeaderText(row.title);
        ButtonType edit = new ButtonType("Upravit", ButtonBar.ButtonData.LEFT);
        ButtonType delete = new ButtonType("Smazat", ButtonBar.ButtonData.LEFT);
        if (row.custom) {
            dialog.getDialogPane().getButtonTypes().addAll(edit, delete, ButtonType.CLOSE);
        } else {
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        }

        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        addDetail(grid, 0, "Vozidlo", row.vehicle);
        addDetail(grid, 1, "Kategorie", row.type);
        addDetail(grid, 2, "Datum", row.date);
        addDetail(grid, 3, "Částka", money(row.amount) + " Kč");
        addDetail(grid, 4, "Popis", row.description.isBlank() ? "Bez popisu" : row.description);
        if (!row.custom) {
            addDetail(grid, 5, "Zdroj", row.source.equals("fueling")
                    ? "Automaticky z tankování" : "Automaticky ze servisu");
            Label hint = new Label("Tento záznam je synchronizovaný. Uprav ho v sekci Tankování nebo Servis.");
            hint.setWrapText(true);
            grid.add(hint, 0, 6, 2, 1);
        }
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(result -> {
            if (result == edit) {
                showEditor(row);
            } else if (result == delete) {
                deleteCost(row);
            }
        });
    }

    private void insertCost(int vehicleId, String title, String category, LocalDate date,
                            double amount, String description) throws SQLException {
        String sql = """
                INSERT INTO other_costs(vehicle_id, title, category, cost_date, amount, description, created_by)
                SELECT ?, ?, ?, ?, ?, ?, ?
                WHERE EXISTS (
                    SELECT 1 FROM vehicles
                    WHERE id = ? AND (user_id = ? OR id IN
                        (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?))
                )
                """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setString(2, title);
            ps.setString(3, category);
            ps.setString(4, date.toString());
            ps.setDouble(5, amount);
            ps.setString(6, description);
            ps.setInt(7, userId);
            ps.setInt(8, vehicleId);
            ps.setInt(9, userId);
            ps.setInt(10, userId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("K vozidlu nemáš přístup.");
            }
        }
    }

    private void updateCost(int id, int vehicleId, String title, String category, LocalDate date,
                            double amount, String description) throws SQLException {
        String sql = """
                UPDATE other_costs
                SET vehicle_id = ?, title = ?, category = ?, cost_date = ?, amount = ?, description = ?
                WHERE id = ? AND vehicle_id IN (
                    SELECT id FROM vehicles WHERE user_id = ?
                    UNION SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?
                )
                """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ps.setString(2, title);
            ps.setString(3, category);
            ps.setString(4, date.toString());
            ps.setDouble(5, amount);
            ps.setString(6, description);
            ps.setInt(7, id);
            ps.setInt(8, userId);
            ps.setInt(9, userId);
            ps.executeUpdate();
        }
    }

    private void deleteCost(CostRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Opravdu chceš smazat tento náklad?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Smazat náklad");
        confirm.setHeaderText(row.title);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        String sql = """
                DELETE FROM other_costs
                WHERE id = ? AND vehicle_id IN (
                    SELECT id FROM vehicles WHERE user_id = ?
                    UNION SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?
                )
                """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, row.id);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ps.executeUpdate();
            loadCosts();
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Náklad se nepodařilo smazat.");
        }
    }

    private List<Vehicle> loadVehicles() {
        List<Vehicle> result = new ArrayList<>();
        String sql = """
                SELECT id, brand, model, registration FROM vehicles
                WHERE user_id = ? OR id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)
                ORDER BY brand, model
                """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Vehicle(rs.getInt("id"), rs.getString("brand"),
                            rs.getString("model"), rs.getString("registration")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Nepodařilo se načíst vozidla.");
        }
        return result;
    }

    private void validate(ComboBox<Vehicle> vehicle, TextField title, ComboBox<String> category,
                          DatePicker date, TextField amount) {
        if (vehicle.getValue() == null) {
            throw new IllegalArgumentException("Vyber vozidlo.");
        }
        if (title.getText() == null || title.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Vyplň název nákladu.");
        }
        String categoryText = category.getEditor().getText().trim().isEmpty()
                ? safe(category.getValue()) : category.getEditor().getText().trim();
        if (categoryText.isBlank()) {
            throw new IllegalArgumentException("Vyber nebo zadej kategorii.");
        }
        if (date.getValue() == null) {
            throw new IllegalArgumentException("Vyber datum.");
        }
        parseAmount(amount.getText());
    }

    private void applyFilters() {
        if (filteredRows == null) {
            return;
        }
        String query = searchField.getText() == null
                ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedType = typeFilter.getValue();
        filteredRows.setPredicate(row -> {
            boolean typeMatches = selectedType == null || selectedType.equals("Všechny typy")
                    || selectedType.equals(row.type)
                    || (selectedType.equals("Ostatní") && row.type.equals("Ostatní"));
            String text = (row.title + " " + row.type + " " + row.vehicle + " " + row.description)
                    .toLowerCase(Locale.ROOT);
            return typeMatches && (query.isEmpty() || text.contains(query));
        });
    }

    private void updateStats() {
        if (countLabel != null) {
            countLabel.setText(String.valueOf(rows.size()));
        }
        double total = rows.stream().mapToDouble(row -> row.amount).sum();
        double fuelings = rows.stream().filter(row -> row.source.equals("fueling"))
                .mapToDouble(row -> row.amount).sum();
        double services = rows.stream().filter(row -> row.source.equals("service"))
                .mapToDouble(row -> row.amount).sum();
        if (totalLabel != null) totalLabel.setText(money(total) + " Kč");
        if (fuelingsLabel != null) fuelingsLabel.setText(money(fuelings) + " Kč");
        if (serviceLabel != null) serviceLabel.setText(money(services) + " Kč");
    }

    private static void addDetail(GridPane grid, int row, String title, String value) {
        Label key = new Label(title + ":");
        key.setStyle("-fx-font-weight: bold;");
        Label val = new Label(value);
        val.setWrapText(true);
        grid.add(key, 0, row);
        grid.add(val, 1, row);
    }

    private static String displayDate(String value) {
        try {
            return LocalDate.parse(value).format(DISPLAY_DATE);
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DISPLAY_DATE);
        } catch (Exception e) {
            try {
                return LocalDate.parse(value);
            } catch (Exception ignored) {
                return LocalDate.now();
            }
        }
    }

    private static double parseAmount(String value) {
        try {
            double amount = Double.parseDouble(value.trim().replace(" ", "").replace(',', '.'));
            if (!Double.isFinite(amount) || amount < 0) {
                throw new NumberFormatException();
            }
            return Math.round(amount * 100.0) / 100.0;
        } catch (Exception e) {
            throw new IllegalArgumentException("Částka musí být nezáporné číslo.");
        }
    }

    private static String money(double value) {
        return String.format(Locale.forLanguageTag("cs-CZ"), "%,.2f", value).replace("\u00a0", " ");
    }

    private static String inputNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class Vehicle {
        final int id;
        final String label;

        Vehicle(int id, String brand, String model, String registration) {
            this.id = id;
            this.label = brand + " " + model + " (" + safe(registration) + ")";
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class CostRow {
        final int id;
        final boolean custom;
        final String type, date, title, vehicle, description, source;
        final double amount;

        CostRow(int id, boolean custom, String type, String date, String title,
                String vehicle, double amount, String description, String source) {
            this.id = id;
            this.custom = custom;
            this.type = type;
            this.date = date;
            this.title = title;
            this.vehicle = vehicle;
            this.amount = amount;
            this.description = description;
            this.source = source;
        }
    }
}
