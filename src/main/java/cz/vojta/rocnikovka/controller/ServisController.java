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

public class ServisController {

    @FXML private TextField searchField;

    @FXML private Label countLabel, costLabel, ongoingLabel, plannedLabel;

    @FXML private TableView<ServiceRow> servicesTable;

    @FXML private TableColumn<ServiceRow, String> titleColumn, vehicleColumn, statusColumn,
    startColumn, expectedColumn, odometerColumn, costColumn;

    private final ObservableList<ServiceRow> rows = FXCollections.observableArrayList();
    private FilteredList<ServiceRow> filteredRows;
    private int userId = -1;

    private static final List<String> STATUSES = List.of("Plánováno", "Probíhá", "Dokončeno", "Zrušeno");

    private static final List<String> TYPES = List.of(
    "Pravidelná údržba", "Výměna oleje", "Brzdy", "Pneumatiky", "Rozvody",
    "Motor", "Převodovka", "Diagnostika", "Oprava", "Jiné"
    );

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML private void initialize() {
        titleColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().title));
        vehicleColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().vehicle));
        statusColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().status));
        startColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().startDate()));
        expectedColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().expectedDate));
        odometerColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().odometer == null ? "—" : formatNumber(d.getValue().odometer) + " km"));
        costColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(money(d.getValue().totalCost) + " Kč"));
        filteredRows = new FilteredList<>(rows, row -> true);
        servicesTable.setItems(filteredRows);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applySearch(newValue));
        servicesTable.setRowFactory(table -> {
            TableRow<ServiceRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) showDetails(row.getItem());
            });
            return row;
        });
    }

    public void setUserId(int userId) {
        this.userId = userId;
        loadServices();
    }

    @FXML private void handleAddService() { showEditor(null); }

    @FXML private void handleEditService() {
        ServiceRow row = servicesTable.getSelectionModel().getSelectedItem();
        if (row == null) { alert(Alert.AlertType.INFORMATION, "Není vybrán servis", "Nejdřív vyber servisní záznam."); return; }
        showEditor(row);
    }

    @FXML private void handleDeleteService() {
        ServiceRow row = servicesTable.getSelectionModel().getSelectedItem();
        if (row == null) { alert(Alert.AlertType.INFORMATION, "Není vybrán servis", "Nejdřív vyber servisní záznam."); return; }
        deleteService(row);
    }

    private void loadServices() {
        rows.clear();
        if (userId <= 0) { updateStats(); return; }
        String sql = """
        SELECT s.*, v.brand, v.model
        FROM services s JOIN vehicles v ON v.id = s.vehicle_id
        WHERE v.user_id = ? OR v.id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)
        ORDER BY COALESCE(s.planned_start, s.started_date, s.completed_date) DESC, s.id DESC
        """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId); ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ServiceRow(rs.getInt("id"), rs.getInt("vehicle_id"),
                    rs.getString("title"), rs.getString("service_type"),
                    safe(rs.getString("description")), rs.getString("status"),
                    rs.getString("planned_start"), rs.getString("planned_end"),
                    rs.getString("started_date"), rs.getString("expected_completion"),
                    rs.getString("completed_date"), (Integer) rs.getObject("odometer"),
                    rs.getDouble("labor_cost"), rs.getDouble("parts_cost"), rs.getDouble("total_cost"),
                    safe(rs.getString("workshop")), safe(rs.getString("notes")),
                    rs.getString("brand") + " " + rs.getString("model")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Servisní záznamy se nepodařilo načíst.");
        }
        updateStats();
        applySearch(searchField.getText());
    }

    private void showEditor(ServiceRow old) {
        List<Vehicle> vehicles = loadVehicles();
        if (vehicles.isEmpty()) { alert(Alert.AlertType.INFORMATION, "Žádná vozidla", "Nejdřív přidej vozidlo nebo požádej vlastníka o sdílení."); return; }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(old == null ? "Přidat servis" : "Upravit servis");
        dialog.setHeaderText("Údaje o servisním úkonu");
        ButtonType save = new ButtonType("Uložit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        ComboBox<Vehicle> vehicleBox = new ComboBox<>(FXCollections.observableArrayList(vehicles));
        vehicleBox.setMaxWidth(Double.MAX_VALUE);
        TextField title = new TextField(old == null ? "" : old.title);
        title.setPromptText("Např. výměna motorového oleje");
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(TYPES));
        type.setEditable(true); type.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList(STATUSES));
        status.setMaxWidth(Double.MAX_VALUE);
        TextArea description = new TextArea(old == null ? "" : old.description);
        description.setPromptText("Popis práce");
        description.setPrefRowCount(2);
        DatePicker plannedStart = datePicker(old == null ? null : old.plannedStart);
        DatePicker plannedEnd = datePicker(old == null ? null : old.plannedEnd);
        DatePicker started = datePicker(old == null ? null : old.startedDate);
        DatePicker expected = datePicker(old == null ? null : old.expectedDate);
        DatePicker completed = datePicker(old == null ? null : old.completedDate);
        TextField odometer = new TextField(old == null || old.odometer == null ? "" : old.odometer.toString());
        TextField labor = new TextField(old == null ? "0" : inputNumber(old.laborCost));
        TextField parts = new TextField(old == null ? "0" : inputNumber(old.partsCost));
        TextField workshop = new TextField(old == null ? "" : old.workshop);
        TextArea notes = new TextArea(old == null ? "" : old.notes);
        notes.setPromptText("Doplňující poznámky"); notes.setPrefRowCount(2);
        if (old == null) {
            vehicleBox.getSelectionModel().selectFirst();
            type.setValue(TYPES.get(0));
            status.setValue("Plánováno");
        } else {
            vehicles.stream().filter(v -> v.id == old.vehicleId).findFirst().ifPresent(vehicleBox::setValue);
            type.setValue(old.type);
            status.setValue(old.status);
        }
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(8); grid.setPadding(new Insets(12));
        int r = 0;
        grid.addRow(r++, new Label("Vozidlo"), vehicleBox);
        grid.addRow(r++, new Label("Název úkonu"), title);
        grid.addRow(r++, new Label("Typ servisu"), type);
        grid.addRow(r++, new Label("Stav"), status);
        grid.addRow(r++, new Label("Popis"), description);
        grid.addRow(r++, new Label("Plánovaný začátek"), plannedStart);
        grid.addRow(r++, new Label("Plánovaný konec"), plannedEnd);
        grid.addRow(r++, new Label("Skutečný začátek"), started);
        grid.addRow(r++, new Label("Očekávané dokončení"), expected);
        grid.addRow(r++, new Label("Skutečné dokončení"), completed);
        grid.addRow(r++, new Label("Tachometr (km)"), odometer);
        grid.addRow(r++, new Label("Cena práce (Kč)"), labor);
        grid.addRow(r++, new Label("Cena dílů (Kč)"), parts);
        grid.addRow(r++, new Label("Servis / dílna"), workshop);
        grid.addRow(r, new Label("Poznámky"), notes);
        grid.setPrefWidth(570);
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(520);
        dialog.getDialogPane().setContent(scroll);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try { validate(title, type, status, plannedStart, plannedEnd, started, expected, completed, odometer, labor, parts, vehicleBox); }
            catch (IllegalArgumentException e) {
                event.consume();
                alert(Alert.AlertType.WARNING, "Zkontroluj údaje", e.getMessage());
            }
        });
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;
        try {
            String serviceTitle = title.getText().trim();
            String serviceType = type.getEditor().getText().trim().isEmpty() ? safe(type.getValue()) : type.getEditor().getText().trim();
            int odo = odometer.getText().trim().isEmpty() ? -1 : parseInteger(odometer.getText(), "Tachometr");
            double laborValue = parseNonNegative(labor.getText(), "Cena práce");
            double partsValue = parseNonNegative(parts.getText(), "Cena dílů");
            double total = Math.round((laborValue + partsValue) * 100.0) / 100.0;
            Vehicle selected = vehicleBox.getValue();
            if (old == null) {
                insert(selected.id, serviceTitle, serviceType, description.getText().trim(), status.getValue(),
                plannedStart.getValue(), plannedEnd.getValue(), started.getValue(), expected.getValue(),
                completed.getValue(), odo, laborValue, partsValue, total, workshop.getText().trim(), notes.getText().trim());
            } else {
                update(old.id, selected.id, serviceTitle, serviceType, description.getText().trim(), status.getValue(),
                plannedStart.getValue(), plannedEnd.getValue(), started.getValue(), expected.getValue(),
                completed.getValue(), odo, laborValue, partsValue, total, workshop.getText().trim(), notes.getText().trim());
            }
            loadServices();
        } catch (IllegalArgumentException e) {
            alert(Alert.AlertType.WARNING, "Zkontroluj údaje", e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Servisní záznam se nepodařilo uložit.");
        }
    }

    private void showDetails(ServiceRow row) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Detail servisu");
        dialog.setHeaderText(row.title);
        ButtonType edit = new ButtonType("Upravit", ButtonBar.ButtonData.LEFT);
        ButtonType delete = new ButtonType("Smazat", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(edit, delete, ButtonType.CLOSE);
        GridPane grid = new GridPane();
        grid.setHgap(18); grid.setVgap(10); grid.setPadding(new Insets(16));
        int r = 0;
        addDetail(grid, r++, "Vozidlo", row.vehicle);
        addDetail(grid, r++, "Typ servisu", row.type);
        addDetail(grid, r++, "Stav", row.status);
        addDetail(grid, r++, "Popis", row.description.isBlank() ? "—" : row.description);
        addDetail(grid, r++, "Plánovaný termín od", dateDisplay(row.plannedStart));
        addDetail(grid, r++, "Plánovaný termín do", dateDisplay(row.plannedEnd));
        addDetail(grid, r++, "Skutečný začátek", dateDisplay(row.startedDate));
        addDetail(grid, r++, "Očekávané dokončení", dateDisplay(row.expectedDate));
        addDetail(grid, r++, "Skutečné dokončení", dateDisplay(row.completedDate));
        addDetail(grid, r++, "Stav tachometru", row.odometer == null ? "—" : formatNumber(row.odometer) + " km");
        addDetail(grid, r++, "Cena práce", money(row.laborCost) + " Kč");
        addDetail(grid, r++, "Náhradní díly", money(row.partsCost) + " Kč");
        addDetail(grid, r++, "Celkové náklady", money(row.totalCost) + " Kč");
        addDetail(grid, r++, "Servis / dílna", row.workshop.isBlank() ? "—" : row.workshop);
        addDetail(grid, r, "Poznámky", row.notes.isBlank() ? "—" : row.notes);
        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(result -> {
            if (result == edit) showEditor(row);
            else if (result == delete) deleteService(row);
        });
    }

    private void deleteService(ServiceRow row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Opravdu chceš smazat tento servisní záznam?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Smazat servis"); confirm.setHeaderText(row.title);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        String sql = """
        DELETE FROM services WHERE id = ? AND vehicle_id IN (
        SELECT id FROM vehicles WHERE user_id = ?
        UNION SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?
        )
        """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, row.id); ps.setInt(2, userId); ps.setInt(3, userId);
            ps.executeUpdate(); loadServices();
        } catch (SQLException e) {
            e.printStackTrace(); alert(Alert.AlertType.ERROR, "Chyba databáze", "Servis se nepodařilo smazat.");
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
            ps.setInt(1, userId); ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new Vehicle(rs.getInt("id"), rs.getString("brand"), rs.getString("model"), rs.getString("registration")));
            }
        } catch (SQLException e) { e.printStackTrace(); alert(Alert.AlertType.ERROR, "Chyba databáze", "Nepodařilo se načíst vozidla."); }
        return result;
    }

    private void insert(int vehicleId, String title, String type, String description, String status,
    LocalDate plannedStart, LocalDate plannedEnd, LocalDate started, LocalDate expected,
    LocalDate completed, int odometer, double labor, double parts, double total,
    String workshop, String notes) throws SQLException {
        String sql = """
        INSERT INTO services(vehicle_id,title,service_type,description,status,planned_start,planned_end,
        started_date,expected_completion,completed_date,odometer,labor_cost,parts_cost,total_cost,workshop,notes,created_by)
        SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?
        WHERE EXISTS (SELECT 1 FROM vehicles WHERE id = ? AND
        (user_id = ? OR id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)))
        """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, vehicleId); ps.setString(i++, title); ps.setString(i++, type); ps.setString(i++, description);
            ps.setString(i++, status); ps.setString(i++, str(plannedStart)); ps.setString(i++, str(plannedEnd));
            ps.setString(i++, str(started)); ps.setString(i++, str(expected)); ps.setString(i++, str(completed));
            if (odometer < 0) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, odometer);
            ps.setDouble(i++, labor); ps.setDouble(i++, parts); ps.setDouble(i++, total);
            ps.setString(i++, workshop); ps.setString(i++, notes); ps.setInt(i++, userId);
            ps.setInt(i++, vehicleId); ps.setInt(i++, userId); ps.setInt(i, userId);
            if (ps.executeUpdate() == 0) throw new SQLException("K vozidlu nemáš přístup.");
        }
    }

    private void update(int id, int vehicleId, String title, String type, String description, String status,
    LocalDate plannedStart, LocalDate plannedEnd, LocalDate started, LocalDate expected,
    LocalDate completed, int odometer, double labor, double parts, double total,
    String workshop, String notes) throws SQLException {
        String sql = """
        UPDATE services SET vehicle_id=?,title=?,service_type=?,description=?,status=?,planned_start=?,planned_end=?,
        started_date=?,expected_completion=?,completed_date=?,odometer=?,labor_cost=?,parts_cost=?,total_cost=?,
        workshop=?,notes=?
        WHERE id=? AND vehicle_id IN (SELECT id FROM vehicles WHERE user_id=? OR id IN
        (SELECT vehicle_id FROM vehicle_shares WHERE user_id=?))
        """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, vehicleId); ps.setString(i++, title); ps.setString(i++, type); ps.setString(i++, description);
            ps.setString(i++, status); ps.setString(i++, str(plannedStart)); ps.setString(i++, str(plannedEnd));
            ps.setString(i++, str(started)); ps.setString(i++, str(expected)); ps.setString(i++, str(completed));
            if (odometer < 0) ps.setNull(i++, Types.INTEGER); else ps.setInt(i++, odometer);
            ps.setDouble(i++, labor); ps.setDouble(i++, parts); ps.setDouble(i++, total);
            ps.setString(i++, workshop); ps.setString(i++, notes);
            ps.setInt(i++, id); ps.setInt(i++, userId); ps.setInt(i, userId);
            ps.executeUpdate();
        }
    }

    private void validate(TextField title, ComboBox<String> type, ComboBox<String> status,
    DatePicker plannedStart, DatePicker plannedEnd, DatePicker started,
    DatePicker expected, DatePicker completed, TextField odometer,
    TextField labor, TextField parts, ComboBox<Vehicle> vehicle) {
        if (vehicle.getValue() == null) throw new IllegalArgumentException("Vyber vozidlo.");
        if (title.getText().trim().isEmpty()) throw new IllegalArgumentException("Vyplň název servisního úkonu.");
        String typeText = type.getEditor().getText().trim().isEmpty() ? safe(type.getValue()) : type.getEditor().getText().trim();
        if (typeText.isBlank()) throw new IllegalArgumentException("Vyber typ servisu.");
        if (status.getValue() == null) throw new IllegalArgumentException("Vyber stav servisu.");
        if (plannedStart.getValue() != null && plannedEnd.getValue() != null && plannedEnd.getValue().isBefore(plannedStart.getValue()))
        throw new IllegalArgumentException("Plánovaný konec nesmí být před plánovaným začátkem.");
        if (odometer.getText() != null && !odometer.getText().trim().isEmpty()) parseInteger(odometer.getText(), "Tachometr");
        parseNonNegative(labor.getText(), "Cena práce"); parseNonNegative(parts.getText(), "Cena dílů");
    }

    private void updateStats() {
        if (countLabel != null) countLabel.setText(String.valueOf(rows.size()));
        double cost = rows.stream().mapToDouble(r -> r.totalCost).sum();
        if (costLabel != null) costLabel.setText(money(cost) + " Kč");
        if (ongoingLabel != null) ongoingLabel.setText(String.valueOf(rows.stream().filter(r -> "Probíhá".equals(r.status)).count()));
        if (plannedLabel != null) plannedLabel.setText(String.valueOf(rows.stream().filter(r -> "Plánováno".equals(r.status)).count()));
    }

    private void applySearch(String text) {
        if (filteredRows == null) return;
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        filteredRows.setPredicate(r -> q.isEmpty() || r.title.toLowerCase(Locale.ROOT).contains(q)
        || r.vehicle.toLowerCase(Locale.ROOT).contains(q) || r.status.toLowerCase(Locale.ROOT).contains(q)
        || r.type.toLowerCase(Locale.ROOT).contains(q) || r.workshop.toLowerCase(Locale.ROOT).contains(q));
    }

    private static void addDetail(GridPane grid, int row, String label, String value) {
        Label key = new Label(label + ":"); key.setStyle("-fx-font-weight: bold;");
        Label val = new Label(value); val.setWrapText(true);
        grid.add(key, 0, row); grid.add(val, 1, row);
    }

    private static DatePicker datePicker(String value) {
        DatePicker picker = new DatePicker();
        if (value != null && !value.isBlank()) {
            try { picker.setValue(LocalDate.parse(value)); } catch (Exception ignored) { }
        }
        return picker;
    }

    private static String str(LocalDate value) { return value == null ? null : value.toString(); }

    private static String dateDisplay(String value) {
        if (value == null || value.isBlank()) return "—";
        try { return LocalDate.parse(value).format(DISPLAY_DATE); } catch (Exception e) { return value; }
    }

    private static int parseInteger(String value, String field) {
        try { int n = Integer.parseInt(value.trim().replace(" ", "")); if (n < 0) throw new NumberFormatException(); return n; }
        catch (Exception e) { throw new IllegalArgumentException(field + " musí být nezáporné celé číslo."); }
    }

    private static double parseNonNegative(String value, String field) {
        try { double n = Double.parseDouble(value.trim().replace(" ", "").replace(',', '.')); if (!Double.isFinite(n) || n < 0) throw new NumberFormatException(); return n; }
        catch (Exception e) { throw new IllegalArgumentException(field + " musí být nezáporné číslo."); }
    }

    private static String money(double value) { return String.format(Locale.forLanguageTag("cs-CZ"), "%,.2f", value).replace("\u00a0", " "); }

    private static String formatNumber(int value) { return String.format(Locale.forLanguageTag("cs-CZ"), "%,d", value).replace("\u00a0", " "); }

    private static String inputNumber(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    private static String safe(String value) { return value == null ? "" : value; }

    private void alert(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type); a.setTitle(title); a.setHeaderText(null); a.setContentText(message); a.showAndWait();
    }
    private static class Vehicle {
        final int id; final String brand, model, registration;
        Vehicle(int id, String brand, String model, String registration) { this.id=id; this.brand=brand; this.model=model; this.registration=registration; }
        @Override public String toString() { return brand + " " + model + " (" + registration + ")"; }
    }
    private static class ServiceRow {
        final int id, vehicleId;
        final String title, type, description, status, plannedStart, plannedEnd, startedDate, expectedDate, completedDate;
        final Integer odometer;
        final double laborCost, partsCost, totalCost;
        final String workshop, notes, vehicle;
        ServiceRow(int id, int vehicleId, String title, String type, String description, String status,
        String plannedStart, String plannedEnd, String startedDate, String expectedDate, String completedDate,
        Integer odometer, double laborCost, double partsCost, double totalCost, String workshop, String notes, String vehicle) {
            this.id=id; this.vehicleId=vehicleId; this.title=title; this.type=type; this.description=description; this.status=status;
            this.plannedStart=plannedStart; this.plannedEnd=plannedEnd; this.startedDate=startedDate; this.expectedDate=expectedDate;
            this.completedDate=completedDate; this.odometer=odometer; this.laborCost=laborCost; this.partsCost=partsCost;
            this.totalCost=totalCost; this.workshop=workshop; this.notes=notes; this.vehicle=vehicle;
        }
        String startDate() { return dateDisplay(plannedStart != null ? plannedStart : startedDate); }
    }
}
