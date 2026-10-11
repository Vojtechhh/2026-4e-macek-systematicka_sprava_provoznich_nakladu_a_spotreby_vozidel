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

public class FuelingsController {
    @FXML private TextField searchField;
    @FXML private Label countLabel;
    @FXML private Label litersLabel;
    @FXML private Label costLabel;
    @FXML private TableView<FuelingRow> fuelingsTable;
    @FXML private TableColumn<FuelingRow, String> dateColumn;
    @FXML private TableColumn<FuelingRow, String> vehicleColumn;
    @FXML private TableColumn<FuelingRow, String> odometerColumn;
    @FXML private TableColumn<FuelingRow, String> litersColumn;
    @FXML private TableColumn<FuelingRow, String> pricePerLiterColumn;
    @FXML private TableColumn<FuelingRow, String> totalPriceColumn;
    @FXML private TableColumn<FuelingRow, String> stationColumn;
    @FXML private TableColumn<FuelingRow, String> fullTankColumn;

    private final ObservableList<FuelingRow> rows = FXCollections.observableArrayList();
    private FilteredList<FuelingRow> filteredRows;
    private int userId = -1;
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final List<String> STATIONS = List.of(
            "ORLEN", "Shell", "OMV", "MOL", "EuroOil", "Tank ONO", "Robin Oil", "Benzina", "Jiná"
    );

    @FXML private void initialize() {
        dateColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().date));
        vehicleColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().vehicle));
        odometerColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(number(d.getValue().odometer) + " km"));
        litersColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(decimal(d.getValue().liters) + " l"));
        pricePerLiterColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(money(d.getValue().pricePerLiter) + " Kč"));
        totalPriceColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(money(d.getValue().totalPrice) + " Kč"));
        stationColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().station));
        fullTankColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().fullTank ? "Ano" : "Ne"));
        filteredRows = new FilteredList<>(rows, row -> true);
        fuelingsTable.setItems(filteredRows);
        fuelingsTable.setRowFactory(table -> {
            TableRow<FuelingRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    showFuelingDetails(row.getItem());
                }
            });
            return row;
        });
        searchField.textProperty().addListener((obs, oldText, newText) -> applySearch(newText));
    }

    public void setUserId(int userId) {
        this.userId = userId;
        loadFuelings();
    }

    @FXML private void handleAddFueling() { showEditor(null); }

    @FXML private void handleEditFueling() {
        FuelingRow row = fuelingsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            alert(Alert.AlertType.INFORMATION, "Není vybráno tankování", "Nejdřív vyber záznam, který chceš upravit.");
            return;
        }
        showEditor(row);
    }

    @FXML private void handleDeleteFueling() {
        FuelingRow row = fuelingsTable.getSelectionModel().getSelectedItem();
        if (row == null) {
            alert(Alert.AlertType.INFORMATION, "Není vybráno tankování", "Nejdřív vyber záznam, který chceš smazat.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Opravdu chceš smazat vybrané tankování?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Smazat tankování");
        confirm.setHeaderText(row.vehicle + " • " + row.date);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        String sql = """
                DELETE FROM fuelings
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
            loadFuelings();
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Tankování se nepodařilo smazat.");
        }
    }

    private void loadFuelings() {
        rows.clear();
        if (userId <= 0) { updateStats(); return; }
        String sql = """
                SELECT f.id, f.vehicle_id, f.fueling_date, f.odometer, f.liters,
                       f.price_per_liter, f.total_price, f.full_tank, f.station, f.notes,
                       v.brand, v.model
                FROM fuelings f JOIN vehicles v ON v.id = f.vehicle_id
                WHERE v.user_id = ? OR v.id IN (
                    SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?
                )
                ORDER BY f.fueling_date DESC, f.id DESC
                """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new FuelingRow(
                            rs.getInt("id"), rs.getInt("vehicle_id"),
                            displayDate(rs.getString("fueling_date")),
                            rs.getString("brand") + " " + rs.getString("model"),
                            rs.getInt("odometer"), rs.getDouble("liters"),
                            rs.getDouble("price_per_liter"), rs.getDouble("total_price"),
                            rs.getInt("full_tank") == 1, safe(rs.getString("station")), safe(rs.getString("notes"))
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Tankování se nepodařilo načíst. Zkontroluj tabulku fuelings.");
        }
        updateStats();
        applySearch(searchField.getText());
    }

    private void showFuelingDetails(FuelingRow row) {
        if (row == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Detail tankování");
        dialog.setHeaderText(row.vehicle + " • " + row.date);

        ButtonType editButton = new ButtonType("Upravit", ButtonBar.ButtonData.LEFT);
        ButtonType deleteButton = new ButtonType("Smazat", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(editButton, deleteButton, ButtonType.CLOSE);

        GridPane details = new GridPane();
        details.setHgap(22);
        details.setVgap(12);
        details.setPadding(new Insets(18));
        details.setPrefWidth(440);

        addDetail(details, 0, "Vozidlo", row.vehicle);
        addDetail(details, 1, "Datum", row.date);
        addDetail(details, 2, "Stav tachometru", number(row.odometer) + " km");
        addDetail(details, 3, "Natankováno", decimal(row.liters) + " l");
        addDetail(details, 4, "Cena za litr", money(row.pricePerLiter) + " Kč/l");
        addDetail(details, 5, "Celková cena", money(row.totalPrice) + " Kč");
        addDetail(details, 6, "Čerpací stanice", row.station.isBlank() ? "Neuvedena" : row.station);
        addDetail(details, 7, "Plná nádrž", row.fullTank ? "Ano" : "Ne");
        addDetail(details, 8, "Poznámka", row.notes.isBlank() ? "Bez poznámky" : row.notes);

        dialog.getDialogPane().setContent(details);
        dialog.showAndWait().ifPresent(result -> {
            if (result == editButton) {
                showEditor(row);
            } else if (result == deleteButton) {
                fuelingsTable.getSelectionModel().select(row);
                handleDeleteFueling();
            }
        });
    }

    private void addDetail(GridPane grid, int row, String label, String value) {
        Label key = new Label(label);
        key.setStyle("-fx-text-fill: #718096;");
        Label text = new Label(value);
        text.setWrapText(true);
        text.setStyle("-fx-font-weight: bold;");
        grid.add(key, 0, row);
        grid.add(text, 1, row);
    }

    private void showEditor(FuelingRow old) {
        List<Vehicle> vehicles = loadVehicles();
        if (vehicles.isEmpty()) {
            alert(Alert.AlertType.INFORMATION, "Žádná vozidla", "Nejdřív přidej vozidlo nebo požádej vlastníka o sdílení.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(old == null ? "Přidat tankování" : "Upravit tankování");
        dialog.setHeaderText("Vyplň údaje o tankování");
        ButtonType save = new ButtonType("Uložit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<Vehicle> vehicleBox = new ComboBox<>(FXCollections.observableArrayList(vehicles));
        vehicleBox.setMaxWidth(Double.MAX_VALUE);
        DatePicker date = new DatePicker(old == null ? LocalDate.now() : parseDate(old.date));
        TextField odometer = new TextField(old == null ? "" : String.valueOf(old.odometer));
        TextField liters = new TextField(old == null ? "" : inputNumber(old.liters));
        TextField price = new TextField(old == null ? "" : inputNumber(old.pricePerLiter));
        ComboBox<String> station = new ComboBox<>(FXCollections.observableArrayList(STATIONS));
        station.setMaxWidth(Double.MAX_VALUE);
        TextField customStation = new TextField();
        customStation.setPromptText("Název čerpací stanice");
        CheckBox fullTank = new CheckBox("Natankováno do plna");
        TextField notes = new TextField();
        notes.setPromptText("Volitelná poznámka");

        if (old == null) {
            vehicleBox.getSelectionModel().selectFirst();
            station.setValue("ORLEN");
        } else {
            vehicles.stream().filter(v -> v.id == old.vehicleId).findFirst().ifPresent(vehicleBox::setValue);
            if (STATIONS.contains(old.station)) station.setValue(old.station);
            else if (old.station.isBlank()) station.setValue("ORLEN");
            else { station.setValue("Jiná"); customStation.setText(old.station); }
            fullTank.setSelected(old.fullTank);
            notes.setText(old.notes);
        }
        Runnable toggleCustom = () -> {
            boolean visible = "Jiná".equals(station.getValue());
            customStation.setVisible(visible);
            customStation.setManaged(visible);
        };
        station.valueProperty().addListener((obs, a, b) -> toggleCustom.run());
        toggleCustom.run();

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(12));
        int r = 0;
        grid.addRow(r++, new Label("Vozidlo"), vehicleBox);
        grid.addRow(r++, new Label("Datum"), date);
        grid.addRow(r++, new Label("Stav tachometru (km)"), odometer);
        grid.addRow(r++, new Label("Natankováno (l)"), liters);
        grid.addRow(r++, new Label("Cena za litr (Kč)"), price);
        grid.addRow(r++, new Label("Čerpací stanice"), station);
        grid.addRow(r++, new Label("Vlastní název"), customStation);
        grid.addRow(r++, new Label("Poznámka"), notes);
        grid.addRow(r, new Label(""), fullTank);
        grid.setPrefWidth(490);
        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                validate(date, odometer, liters, price, station, customStation, vehicleBox);
            } catch (IllegalArgumentException e) {
                event.consume();
                alert(Alert.AlertType.WARNING, "Zkontroluj údaje", e.getMessage());
            }
        });

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;
        try {
            int odo = parseInteger(odometer.getText(), "Stav tachometru");
            double litreCount = parseDouble(liters.getText(), "Počet litrů");
            double priceValue = parseDouble(price.getText(), "Cena za litr");
            String stationName = "Jiná".equals(station.getValue()) ? customStation.getText().trim() : station.getValue();
            double total = Math.round(litreCount * priceValue * 100.0) / 100.0;
            Vehicle selected = vehicleBox.getValue();
            if (old == null) insert(selected.id, date.getValue(), odo, litreCount, priceValue, total, fullTank.isSelected(), stationName, notes.getText().trim());
            else update(old.id, selected.id, date.getValue(), odo, litreCount, priceValue, total, fullTank.isSelected(), stationName, notes.getText().trim());
            loadFuelings();
        } catch (IllegalArgumentException e) {
            alert(Alert.AlertType.WARNING, "Zkontroluj údaje", e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Tankování se nepodařilo uložit.");
        }
    }

    private void validate(DatePicker date, TextField odometer, TextField liters, TextField price,
                          ComboBox<String> station, TextField customStation, ComboBox<Vehicle> vehicle) {
        if (date.getValue() == null) throw new IllegalArgumentException("Vyber datum.");
        if (vehicle.getValue() == null) throw new IllegalArgumentException("Vyber vozidlo.");
        parseInteger(odometer.getText(), "Stav tachometru");
        parseDouble(liters.getText(), "Počet litrů");
        parseDouble(price.getText(), "Cena za litr");
        if (station.getValue() == null) throw new IllegalArgumentException("Vyber čerpací stanici.");
        if ("Jiná".equals(station.getValue()) && customStation.getText().trim().isEmpty())
            throw new IllegalArgumentException("Zadej název jiné čerpací stanice.");
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
        } catch (SQLException e) {
            e.printStackTrace();
            alert(Alert.AlertType.ERROR, "Chyba databáze", "Nepodařilo se načíst vozidla.");
        }
        return result;
    }

    private void insert(int vehicleId, LocalDate date, int odo, double liters, double price, double total,
                        boolean full, String station, String notes) throws SQLException {
        String sql = """
                INSERT INTO fuelings(vehicle_id, fueling_date, odometer, liters, price_per_liter,
                                     total_price, full_tank, station, notes, created_by)
                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                WHERE EXISTS (SELECT 1 FROM vehicles WHERE id = ? AND
                    (user_id = ? OR id IN (SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?)))
                """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId); ps.setString(2, date.toString()); ps.setInt(3, odo);
            ps.setDouble(4, liters); ps.setDouble(5, price); ps.setDouble(6, total);
            ps.setInt(7, full ? 1 : 0); ps.setString(8, station); ps.setString(9, notes); ps.setInt(10, userId);
            ps.setInt(11, vehicleId); ps.setInt(12, userId); ps.setInt(13, userId);
            if (ps.executeUpdate() == 0) throw new SQLException("K vozidlu nemáš přístup.");
        }
    }

    private void update(int id, int vehicleId, LocalDate date, int odo, double liters, double price, double total,
                        boolean full, String station, String notes) throws SQLException {
        String sql = """
                UPDATE fuelings SET vehicle_id=?, fueling_date=?, odometer=?, liters=?, price_per_liter=?,
                    total_price=?, full_tank=?, station=?, notes=?
                WHERE id=? AND vehicle_id IN (
                    SELECT id FROM vehicles WHERE user_id=? OR id IN
                    (SELECT vehicle_id FROM vehicle_shares WHERE user_id=?))
                """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, vehicleId); ps.setString(2, date.toString()); ps.setInt(3, odo);
            ps.setDouble(4, liters); ps.setDouble(5, price); ps.setDouble(6, total);
            ps.setInt(7, full ? 1 : 0); ps.setString(8, station); ps.setString(9, notes);
            ps.setInt(10, id); ps.setInt(11, userId); ps.setInt(12, userId);
            ps.executeUpdate();
        }
    }

    private void applySearch(String text) {
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (filteredRows != null) filteredRows.setPredicate(row ->
                q.isEmpty() || row.vehicle.toLowerCase(Locale.ROOT).contains(q)
                        || row.station.toLowerCase(Locale.ROOT).contains(q)
                        || row.notes.toLowerCase(Locale.ROOT).contains(q)
                        || row.date.toLowerCase(Locale.ROOT).contains(q));
    }

    private void updateStats() {
        if (countLabel != null) countLabel.setText(String.valueOf(rows.size()));
        double litres = rows.stream().mapToDouble(r -> r.liters).sum();
        double cost = rows.stream().mapToDouble(r -> r.totalPrice).sum();
        if (litersLabel != null) litersLabel.setText(decimal(litres) + " l");
        if (costLabel != null) costLabel.setText(money(cost) + " Kč");
    }

    private static int parseInteger(String text, String label) {
        try {
            int value = Integer.parseInt(text.trim().replace(" ", ""));
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (Exception e) { throw new IllegalArgumentException(label + " musí být celé nezáporné číslo."); }
    }

    private static double parseDouble(String text, String label) {
        try {
            double value = Double.parseDouble(text.trim().replace(" ", "").replace(',', '.'));
            if (!Double.isFinite(value) || value <= 0) throw new NumberFormatException();
            return value;
        } catch (Exception e) { throw new IllegalArgumentException(label + " musí být kladné číslo."); }
    }

    private static String money(double value) { return String.format(Locale.forLanguageTag("cs-CZ"), "%,.2f", value).replace('\u00a0', ' '); }
    private static String decimal(double value) { return String.format(Locale.forLanguageTag("cs-CZ"), "%,.2f", value).replace('\u00a0', ' '); }
    private static String number(int value) { return String.format(Locale.forLanguageTag("cs-CZ"), "%,d", value).replace('\u00a0', ' '); }
    private static String inputNumber(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static String displayDate(String value) {
        try { return LocalDate.parse(value).format(DISPLAY_DATE); } catch (Exception e) { return value == null ? "" : value; }
    }
    private static LocalDate parseDate(String value) {
        try { return LocalDate.parse(value, DISPLAY_DATE); } catch (Exception e) {
            try { return LocalDate.parse(value); } catch (Exception ignored) { return LocalDate.now(); }
        }
    }
    private static String safe(String value) { return value == null ? "" : value; }

    private void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }

    private static class Vehicle {
        final int id;
        final String brand, model, registration;
        Vehicle(int id, String brand, String model, String registration) {
            this.id = id; this.brand = brand; this.model = model; this.registration = registration;
        }
        @Override public String toString() { return brand + " " + model + " (" + registration + ")"; }
    }

    private static class FuelingRow {
        final int id, vehicleId, odometer;
        final String date, vehicle, station, notes;
        final double liters, pricePerLiter, totalPrice;
        final boolean fullTank;
        FuelingRow(int id, int vehicleId, String date, String vehicle, int odometer, double liters,
                   double pricePerLiter, double totalPrice, boolean fullTank, String station, String notes) {
            this.id = id; this.vehicleId = vehicleId; this.date = date; this.vehicle = vehicle;
            this.odometer = odometer; this.liters = liters; this.pricePerLiter = pricePerLiter;
            this.totalPrice = totalPrice; this.fullTank = fullTank; this.station = station; this.notes = notes;
        }
    }
}
