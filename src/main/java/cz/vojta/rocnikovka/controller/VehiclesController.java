package cz.vojta.rocnikovka.controller;
import cz.vojta.rocnikovka.Database;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
public class VehiclesController {
    @FXML
    private TextField searchField;
    @FXML
    private Label vehicleCountLabel;
    @FXML
    private VBox emptyState;
    @FXML
    private TableView<Vehicle> vehiclesTable;
    @FXML
    private TableColumn<Vehicle, String> brandColumn;
    @FXML
    private TableColumn<Vehicle, String> modelColumn;
    @FXML
    private TableColumn<Vehicle, String> registrationColumn;
    @FXML
    private TableColumn<Vehicle, String> yearColumn;
    @FXML
    private TableColumn<Vehicle, String> odometerColumn;
    // id prihlaseneho uzivatele
    private int userId = -1;
    public void setUserId(int userId) {
        this.userId = userId;
        loadVehicles();
    }
    // seznam vozidel nactenych z databaze
    private final ObservableList<Vehicle> vehicles =
            FXCollections.observableArrayList();
    // seznam pro vyhledavani
    private final FilteredList<Vehicle> filteredVehicles =
            new FilteredList<>(vehicles, vehicle -> true);
    @FXML
    private void initialize() {
        brandColumn.setCellValueFactory(
                new PropertyValueFactory<>("brand"));
        modelColumn.setCellValueFactory(
                new PropertyValueFactory<>("model"));
        registrationColumn.setCellValueFactory(
                new PropertyValueFactory<>("registration"));
        yearColumn.setCellValueFactory(
                new PropertyValueFactory<>("year"));
        odometerColumn.setCellValueFactory(
                new PropertyValueFactory<>("odometer"));
        vehiclesTable.setItems(filteredVehicles);
        // dvojklik na radek otevre detail vozidla
        vehiclesTable.setRowFactory(table -> {
            TableRow<Vehicle> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showVehicleDetail(row.getItem());
                }
            });
            return row;
        });
        // vyhledavani pri psani
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) ->
                        filterVehicles(newValue)
        );
    }
    // otevreni formulare pro pridani vozidla
    @FXML
    private void handleAddVehicle() {
        showVehicleEditor(null);
    }
    // ==========================================
    // DETAIL VOZIDLA
    // ==========================================
    private void showVehicleDetail(Vehicle vehicle) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Detail vozidla");
        dialog.setHeaderText(null);
        ButtonType editButton = new ButtonType(
                "Upravit údaje",
                ButtonBar.ButtonData.LEFT
        );
        ButtonType shareButton = new ButtonType(
                "Sdílet vozidlo",
                ButtonBar.ButtonData.LEFT
        );
        ButtonType deleteButton = new ButtonType(
                "Smazat vozidlo",
                ButtonBar.ButtonData.LEFT
        );
        dialog.getDialogPane().getButtonTypes().addAll(
                editButton,
                shareButton,
                deleteButton,
                ButtonType.CLOSE
        );
        // nacteni stylu aplikace
        var dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("vehicle-detail-dialog");
        var stylesheet = getClass().getResource("/css/style.css");
        if (stylesheet != null) {
            dialogPane.getStylesheets().add(
                    stylesheet.toExternalForm());
        }
        // hlavicka detailu
        Label title = new Label(
                vehicle.getBrand() + " " + vehicle.getModel());
        title.getStyleClass().add("vehicle-detail-title");
        Label registration = new Label(vehicle.getRegistration());
        registration.getStyleClass().add("vehicle-detail-badge");
        Label year = new Label("Rok výroby: " + vehicle.getYear());
        year.getStyleClass().add("vehicle-detail-badge");
        HBox badges = new HBox(8, registration, year);
        badges.setAlignment(Pos.CENTER_LEFT);
        VBox heading = new VBox(9, title, badges);
        heading.getStyleClass().add("vehicle-detail-heading");
        // fotografie vozidla
        ImageView photo = new ImageView();
        photo.setFitWidth(390);
        photo.setFitHeight(260);
        photo.setPreserveRatio(true);
        photo.setSmooth(true);
        StackPane photoFrame = new StackPane();
        photoFrame.setMinHeight(270);
        photoFrame.setAlignment(Pos.CENTER);
        photoFrame.getStyleClass().add("vehicle-photo-frame");
        boolean hasPhoto = false;
        if (vehicle.getPhotoPath() != null
                && !vehicle.getPhotoPath().isBlank()) {
            try {
                Path path = Path.of(vehicle.getPhotoPath());
                if (Files.exists(path)) {
                    Image image = new Image(path.toUri().toString());
                    if (!image.isError()) {
                        photo.setImage(image);
                        photoFrame.getChildren().add(photo);
                        hasPhoto = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (!hasPhoto) {
            Label noPhoto = new Label(
                    "🚗\nFotografie není k dispozici");
            noPhoto.getStyleClass().add("vehicle-no-photo");
            noPhoto.setTextAlignment(TextAlignment.CENTER);
            photoFrame.getChildren().add(noPhoto);
        }
        VBox photoCard = new VBox(15, photoFrame);
        photoCard.getStyleClass().add("vehicle-detail-card");
        photoCard.setPrefWidth(420);
        photoCard.setMinWidth(300);
        // rychly prehled pod fotografii
        Label overviewTitle = new Label("Rychlý přehled");
        overviewTitle.getStyleClass().add("vehicle-detail-section-title");
        GridPane overviewGrid = new GridPane();
        overviewGrid.setHgap(12);
        overviewGrid.setVgap(12);
        overviewGrid.add(
                createOverviewItem(
                        "Tachometr",
                        formatOdometer(vehicle.getOdometer()) + " km"),
                0, 0
        );
        overviewGrid.add(
                createOverviewItem(
                        "Palivo",
                        display(vehicle.getFuelType())),
                1, 0
        );
        overviewGrid.add(
                createOverviewItem(
                        "Motorizace",
                        display(vehicle.getEngine())),
                0, 1
        );
        overviewGrid.add(
                createOverviewItem(
                        "Barva",
                        display(vehicle.getColor())),
                1, 1
        );
        VBox overviewCard = new VBox(16, overviewTitle, overviewGrid);
        overviewCard.getStyleClass().add("vehicle-detail-section");
        VBox leftColumn = new VBox(15, photoCard, overviewCard);
        leftColumn.setPrefWidth(420);
        leftColumn.setMinWidth(300);
        // zakladni informace
        GridPane basicGrid = new GridPane();
        basicGrid.setHgap(15);
        basicGrid.setVgap(0);
        basicGrid.getStyleClass().add("vehicle-detail-grid");
        addDetailRow(basicGrid, 0, "Značka", vehicle.getBrand());
        addDetailRow(basicGrid, 1, "Model", vehicle.getModel());
        addDetailRow(basicGrid, 2, "SPZ", vehicle.getRegistration());
        addDetailRow(basicGrid, 3, "Rok výroby", vehicle.getYear());
        addDetailRow(
                basicGrid,
                4,
                "Stav tachometru",
                formatOdometer(vehicle.getOdometer()) + " km"
        );
        VBox basicSection = createDetailSection(
                "▣",
                "Základní informace",
                basicGrid
        );
        // technicke udaje
        GridPane technicalGrid = new GridPane();
        technicalGrid.setHgap(15);
        technicalGrid.setVgap(0);
        technicalGrid.getStyleClass().add("vehicle-detail-grid");
        addDetailRow(
                technicalGrid, 0, "Motorizace",
                display(vehicle.getEngine()));
        addDetailRow(
                technicalGrid, 1, "Palivo",
                display(vehicle.getFuelType()));
        addDetailRow(
                technicalGrid, 2, "VIN",
                display(vehicle.getVin()));
        addDetailRow(
                technicalGrid, 3, "Barva",
                display(vehicle.getColor()));
        VBox technicalSection = createDetailSection(
                "⚙",
                "Technické údaje",
                technicalGrid
        );
        // poznamky k vozidlu
        Label notes = new Label(display(vehicle.getNotes()));
        notes.setWrapText(true);
        notes.getStyleClass().add("vehicle-detail-notes");
        VBox notesSection = createDetailSection(
                "▤",
                "Poznámky",
                notes
        );
        VBox rightColumn = new VBox(
                15,
                basicSection,
                technicalSection,
                notesSection
        );
        rightColumn.setPrefWidth(420);
        rightColumn.setMinWidth(300);
        // fotografie a informace vedle sebe
        HBox body = new HBox(18, leftColumn, rightColumn);
        body.setAlignment(Pos.TOP_LEFT);
        body.getStyleClass().add("vehicle-detail-body");
        VBox content = new VBox(22, heading, body);
        content.setPadding(new Insets(22));
        content.getStyleClass().add("vehicle-detail-content");
        // scrollovani pri mensi velikosti okna
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("vehicle-detail-scroll");
        scrollPane.setPrefViewportHeight(480);
        dialogPane.setContent(scrollPane);
        dialogPane.setPrefWidth(940);
        dialogPane.setPrefHeight(650);
        // uprava vzhledu tlacitka
        Button editControl = (Button) dialogPane.lookupButton(editButton);
        editControl.getStyleClass().add(
                "vehicle-detail-edit-button");
        // otevreni editace po kliknuti na upravit
        dialog.showAndWait().ifPresent(result -> {
            if (result == editButton) {
                showVehicleEditor(vehicle);
            } else if (result == shareButton) {
                manageVehicleSharing(vehicle);
            } else if (result == deleteButton) {
                if (isVehicleOwner(vehicle.getId())) {
                    confirmDeleteVehicle(vehicle);
                } else {
                    showAlert(
                            Alert.AlertType.WARNING,
                            "Nelze smazat vozidlo",
                            "Sdílené vozidlo může smazat pouze jeho vlastník."
                    );
                }
            }
        });
    }
    // potvrzeni a smazani vozidla prihlaseneho uzivatele
    private void confirmDeleteVehicle(Vehicle vehicle) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Smazat vozidlo");
        confirmation.setHeaderText(
                "Opravdu chcete smazat vozidlo "
                        + vehicle.getBrand() + " " + vehicle.getModel() + "?");
        confirmation.setContentText(
                "Tuto akci nelze vratit zpet.");

        var result = confirmation.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        String sql = "DELETE FROM vehicles WHERE id = ? AND user_id = ?";
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, vehicle.getId());
            statement.setInt(2, userId);
            int deletedRows = statement.executeUpdate();

            if (deletedRows > 0) {
                loadVehicles();
                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Vozidlo smazano");
                success.setHeaderText(null);
                success.setContentText("Vozidlo bylo uspesne smazano.");
                success.showAndWait();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Chyba");
            error.setHeaderText("Vozidlo se nepodarilo smazat.");
            error.setContentText(e.getMessage());
            error.showAndWait();
        }
    }
    // sprava pristupu ke sdilenemu vozidlu
    private void manageVehicleSharing(Vehicle vehicle) {
        if (!isVehicleOwner(vehicle.getId())) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "Sdílení vozidla",
                    "Přístup k vozidlu může spravovat pouze jeho vlastník."
            );
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Sdílení vozidla");
        dialog.setHeaderText("Přístup k vozidlu " + vehicle.getBrand() + " " + vehicle.getModel());

        ButtonType addButton = new ButtonType("Přidat přístup", ButtonBar.ButtonData.LEFT);
        ButtonType removeButton = new ButtonType("Odebrat přístup", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(addButton, removeButton, ButtonType.CLOSE);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Uživatelské jméno");
        Label currentShares = new Label(getSharedUsernames(vehicle.getId()));
        currentShares.setWrapText(true);
        VBox content = new VBox(12,
                new Label("Zadej uživatelské jméno registrovaného účtu:"),
                usernameField,
                new Label("Aktuálně sdíleno s:"),
                currentShares);
        content.setPadding(new Insets(16));
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(result -> {
            String targetUsername = usernameField.getText() == null
                    ? "" : usernameField.getText().trim();

            if (result == addButton) {
                if (targetUsername.isBlank()) {
                    showAlert(Alert.AlertType.WARNING, "Chybí uživatel", "Zadej uživatelské jméno.");
                    return;
                }
                shareVehicleWithUsername(vehicle.getId(), targetUsername);
            } else if (result == removeButton) {
                if (targetUsername.isBlank()) {
                    showAlert(Alert.AlertType.WARNING, "Chybí uživatel", "Zadej uživatelské jméno, kterému chceš přístup odebrat.");
                    return;
                }
                removeVehicleShare(vehicle.getId(), targetUsername);
            }
        });
    }

    private boolean isVehicleOwner(int vehicleId) {
        String sql = "SELECT 1 FROM vehicles WHERE id = ? AND user_id = ?";
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, vehicleId);
            statement.setInt(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getSharedUsernames(int vehicleId) {
        String sql = """
                SELECT users.username
                FROM vehicle_shares
                JOIN users ON users.id = vehicle_shares.user_id
                WHERE vehicle_shares.vehicle_id = ?
                ORDER BY users.username
                """;
        StringBuilder names = new StringBuilder();
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, vehicleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (names.length() > 0) names.append(", ");
                    names.append(resultSet.getString("username"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return names.length() == 0 ? "Zatím není sdíleno s žádným uživatelem." : names.toString();
    }

    private void shareVehicleWithUsername(int vehicleId, String targetUsername) {
        String lookupSql = "SELECT id FROM users WHERE username = ?";
        String insertSql = "INSERT OR IGNORE INTO vehicle_shares (vehicle_id, user_id, granted_by) VALUES (?, ?, ?)";
        try (Connection connection = Database.connect();
             PreparedStatement lookup = connection.prepareStatement(lookupSql)) {
            lookup.setString(1, targetUsername);
            int targetUserId;
            try (ResultSet resultSet = lookup.executeQuery()) {
                if (!resultSet.next()) {
                    showAlert(Alert.AlertType.WARNING, "Uživatel nenalezen", "Účet s tímto uživatelským jménem neexistuje.");
                    return;
                }
                targetUserId = resultSet.getInt("id");
            }
            if (targetUserId == userId) {
                showAlert(Alert.AlertType.WARNING, "Neplatný uživatel", "Vlastník už k vozidlu přístup má.");
                return;
            }
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                insert.setInt(1, vehicleId);
                insert.setInt(2, targetUserId);
                insert.setInt(3, userId);
                int inserted = insert.executeUpdate();
                showAlert(Alert.AlertType.INFORMATION, "Sdílení vozidla",
                        inserted > 0 ? "Uživatel získal přístup k vozidlu." : "Tento uživatel už přístup má.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Chyba databáze", "Přístup se nepodařilo přidat: " + e.getMessage());
        }
    }

    private void removeVehicleShare(int vehicleId, String targetUsername) {
        String sql = """
                DELETE FROM vehicle_shares
                WHERE vehicle_id = ?
                  AND user_id = (SELECT id FROM users WHERE username = ?)
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, vehicleId);
            statement.setString(2, targetUsername);
            int removed = statement.executeUpdate();
            showAlert(Alert.AlertType.INFORMATION, "Sdílení vozidla",
                    removed > 0 ? "Přístup byl odebrán." : "Tento uživatel neměl k vozidlu přístup.");
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Chyba databáze", "Přístup se nepodařilo odebrat: " + e.getMessage());
        }
    }

    // vytvoreni sekce detailu
    private VBox createDetailSection(
            String icon,
            String title,
            Node content) {
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("vehicle-detail-section-icon");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("vehicle-detail-section-title");
        HBox header = new HBox(10, iconLabel, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox section = new VBox(14, header, content);
        section.getStyleClass().add("vehicle-detail-section");
        return section;
    }
    // mala karta s rychlou informaci o vozidle
    private VBox createOverviewItem(String title, String value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("vehicle-detail-label");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("vehicle-detail-value");
        valueLabel.setWrapText(true);
        VBox item = new VBox(6, titleLabel, valueLabel);
        item.getStyleClass().add("vehicle-overview-item");
        return item;
    }
    // jeden radek s udajem
    private void addDetailRow(
            GridPane grid,
            int row,
            String title,
            String value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("vehicle-detail-label");
        titleLabel.setWrapText(true);
        Label valueLabel = new Label(display(value));
        valueLabel.getStyleClass().add("vehicle-detail-value");
        valueLabel.setWrapText(true);
        grid.add(titleLabel, 0, row);
        grid.add(valueLabel, 1, row);
        // nastaveni sirky sloupcu pouze jednou
        if (grid.getColumnConstraints().isEmpty()) {
            ColumnConstraints labelColumn =
                    new ColumnConstraints();
            labelColumn.setPercentWidth(43);
            ColumnConstraints valueColumn =
                    new ColumnConstraints();
            valueColumn.setPercentWidth(57);
            grid.getColumnConstraints().addAll(
                    labelColumn,
                    valueColumn
            );
        }
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        valueLabel.setMaxWidth(Double.MAX_VALUE);
        GridPane.setMargin(
                titleLabel,
                new Insets(10, 0, 10, 0));
        GridPane.setMargin(
                valueLabel,
                new Insets(10, 0, 10, 0));
    }
    // formatovani stavu tachometru
    private String formatOdometer(String value) {
        try {
            return String.format(
                    Locale.forLanguageTag("cs-CZ"),
                    "%,d",
                    Long.parseLong(value)
            ).replace('\u00a0', ' ');
        } catch (Exception e) {
            return value == null ? "0" : value;
        }
    }
    private String display(String value) {
        return value == null || value.isBlank()
                ? "Neuvedeno"
                : value;
    }
    // ==========================================
    // PRIDANI A UPRAVA VOZIDLA
    // ==========================================
    private void showVehicleEditor(Vehicle existing) {
        boolean editing = existing != null;
        Dialog<Vehicle> dialog = new Dialog<>();
        dialog.setTitle(
                editing ? "Upravit vozidlo" : "Přidat vozidlo");
        dialog.setHeaderText(
                editing
                        ? "Upravte údaje o vozidle"
                        : "Zadejte údaje o novém vozidle"
        );
        ButtonType saveButton = new ButtonType(
                "Uložit",
                ButtonBar.ButtonData.OK_DONE
        );
        dialog.getDialogPane().getButtonTypes().addAll(
                saveButton,
                ButtonType.CANCEL
        );
        TextField brand = field(
                "Např. Škoda",
                editing ? existing.getBrand() : "");
        TextField model = field(
                "Např. Octavia",
                editing ? existing.getModel() : "");
        TextField registration = field(
                "Např. 1AB 2345",
                editing ? existing.getRegistration() : "");
        TextField year = field(
                "Např. 2022",
                editing ? existing.getYear() : "");
        TextField odometer = field(
                "Např. 85000",
                editing ? existing.getOdometer() : "");
        TextField engine = field(
                "Např. 2.0 TDI",
                editing ? existing.getEngine() : "");
        TextField fuelType = field(
                "Např. Nafta",
                editing ? existing.getFuelType() : "");
        TextField vin = field(
                "Identifikační číslo VIN",
                editing ? existing.getVin() : "");
        TextField color = field(
                "Např. stříbrná",
                editing ? existing.getColor() : "");
        TextArea notes = new TextArea(
                editing ? safe(existing.getNotes()) : "");
        notes.setPromptText(
                "Servisní poznámky a další informace...");
        notes.setPrefRowCount(3);
        notes.setWrapText(true);
        // nahled fotografie
        ImageView preview = new ImageView();
        preview.setFitWidth(220);
        preview.setFitHeight(125);
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        Label photoStatus = new Label();
        Button choosePhoto = new Button("Vybrat fotografii");
        Button removePhoto = new Button("Odebrat fotografii");
        // stav vybrane fotografie
        final File[] selectedPhoto = {null};
        final boolean[] removeSelectedPhoto = {false};
        String oldPhotoPath = editing
                ? existing.getPhotoPath()
                : null;
        // zobrazeni puvodni fotografie
        if (oldPhotoPath != null && !oldPhotoPath.isBlank()) {
            try {
                Path oldPath = Path.of(oldPhotoPath);
                if (Files.exists(oldPath)) {
                    preview.setImage(
                            new Image(oldPath.toUri().toString()));
                    photoStatus.setText("Aktuální fotografie");
                } else {
                    photoStatus.setText(
                            "Původní fotografie nebyla nalezena");
                }
            } catch (Exception e) {
                photoStatus.setText(
                        "Fotografii se nepodařilo načíst");
            }
        } else {
            photoStatus.setText("Fotografie není vybrána");
        }
        // vyber nove fotografie
        choosePhoto.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Vyberte fotografii vozidla");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            "Obrázky",
                            "*.png",
                            "*.jpg",
                            "*.jpeg",
                            "*.webp"
                    )
            );
            File file = chooser.showOpenDialog(
                    dialog.getDialogPane().getScene().getWindow());
            if (file != null) {
                Image image = new Image(file.toURI().toString());
                if (image.isError()) {
                    showAlert(
                            Alert.AlertType.WARNING,
                            "Neplatný obrázek",
                            "Vybraný obrázek se nepodařilo načíst."
                    );
                    return;
                }
                selectedPhoto[0] = file;
                removeSelectedPhoto[0] = false;
                preview.setImage(image);
                photoStatus.setText(file.getName());
            }
        });
        // odebrani fotografie
        removePhoto.setOnAction(event -> {
            selectedPhoto[0] = null;
            removeSelectedPhoto[0] = true;
            preview.setImage(null);
            photoStatus.setText("Fotografie bude odebrána");
        });
        HBox photoButtons = new HBox(
                8,
                choosePhoto,
                removePhoto
        );
        VBox photoBox = new VBox(
                8,
                preview,
                photoButtons,
                photoStatus
        );
        // rozlozeni formulare
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        addField(grid, 0, "Značka:", brand);
        addField(grid, 1, "Model:", model);
        addField(grid, 2, "SPZ:", registration);
        addField(grid, 3, "Rok výroby:", year);
        addField(grid, 4, "Tachometr (km):", odometer);
        addField(grid, 5, "Motorizace:", engine);
        addField(grid, 6, "Palivo:", fuelType);
        addField(grid, 7, "VIN:", vin);
        addField(grid, 8, "Barva:", color);
        addField(grid, 9, "Poznámky:", notes);
        addField(grid, 10, "Fotografie:", photoBox);
        VBox form = new VBox(grid);
        form.setPrefWidth(600);
        dialog.getDialogPane().setContent(form);
        // kontrola a sestaveni vysledku formulare
        dialog.setResultConverter(button -> {
            if (button != saveButton) {
                return null;
            }
            String brandValue = brand.getText().trim();
            String modelValue = model.getText().trim();
            String registrationValue = registration.getText()
                    .trim()
                    .toUpperCase();
            String yearValue = year.getText().trim();
            String odometerValue = odometer.getText().trim();
            if (brandValue.isEmpty()
                    || modelValue.isEmpty()
                    || registrationValue.isEmpty()
                    || yearValue.isEmpty()
                    || odometerValue.isEmpty()) {
                showAlert(
                        Alert.AlertType.WARNING,
                        "Chybějící údaje",
                        "Vyplňte značku, model, SPZ, rok výroby a tachometr."
                );
                return null;
            }
            try {
                int yearNumber = Integer.parseInt(yearValue);
                int odometerNumber = Integer.parseInt(odometerValue);
                if (yearNumber < 1886
                        || yearNumber > 2100
                        || odometerNumber < 0) {
                    showAlert(
                            Alert.AlertType.WARNING,
                            "Neplatné údaje",
                            "Zkontrolujte rok výroby a stav tachometru."
                    );
                    return null;
                }
            } catch (NumberFormatException e) {
                showAlert(
                        Alert.AlertType.WARNING,
                        "Neplatný formát",
                        "Rok výroby a tachometr musí být čísla."
                );
                return null;
            }
            String photoPath;
            if (removeSelectedPhoto[0]) {
                photoPath = null;
            } else if (selectedPhoto[0] != null) {
                photoPath = selectedPhoto[0].getAbsolutePath();
            } else {
                photoPath = oldPhotoPath;
            }
            return new Vehicle(
                    editing ? existing.getId() : 0,
                    brandValue,
                    modelValue,
                    registrationValue,
                    yearValue,
                    odometerValue,
                    photoPath,
                    engine.getText().trim(),
                    fuelType.getText().trim(),
                    vin.getText().trim(),
                    color.getText().trim(),
                    notes.getText().trim()
            );
        });
        Optional<Vehicle> result = dialog.showAndWait();
        result.ifPresent(vehicle -> {
            if (editing) {
                updateVehicle(vehicle, selectedPhoto[0],
                        removeSelectedPhoto[0]);
            } else {
                insertVehicle(vehicle, selectedPhoto[0]);
            }
        });
    }
    private TextField field(String prompt, String value) {
        TextField field = new TextField(
                value == null ? "" : value);
        field.setPromptText(prompt);
        return field;
    }
    private void addField(
            GridPane grid,
            int row,
            String label,
            Node node) {
        grid.add(new Label(label), 0, row);
        grid.add(node, 1, row);
    }
    // ==========================================
    // DATABAZE
    // ==========================================
    // ulozeni noveho vozidla
    private void insertVehicle(Vehicle vehicle, File photoFile) {
        String photoPath = copyPhoto(photoFile);
        if (photoFile != null && photoPath == null) {
            return;
        }
        String sql = """
                INSERT INTO vehicles
                    (brand, model, registration, year, odometer,
                     photo_path, engine, fuel_type, vin, color, notes, user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            if (userId <= 0) {
                showAlert(Alert.AlertType.ERROR, "Chyba účtu", "Nelze určit přihlášeného uživatele.");
                return;
            }
            fillStatement(statement, vehicle, photoPath);
            statement.setInt(12, userId);
            statement.executeUpdate();
            loadVehicles();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(
                    Alert.AlertType.ERROR,
                    "Chyba databáze",
                    "Vozidlo se nepodařilo uložit."
            );
        }
    }
    // ulozeni upraveneho vozidla
    private void updateVehicle(
            Vehicle vehicle,
            File photoFile,
            boolean removePhoto) {
        String photoPath = vehicle.getPhotoPath();
        if (removePhoto) {
            photoPath = null;
        } else if (photoFile != null) {
            photoPath = copyPhoto(photoFile);
            if (photoPath == null) {
                return;
            }
        }
        String sql = """
                UPDATE vehicles
                SET brand = ?, model = ?, registration = ?, year = ?,
                    odometer = ?, photo_path = ?, engine = ?, fuel_type = ?,
                    vin = ?, color = ?, notes = ?
                WHERE id = ?
                  AND (user_id = ? OR id IN (
                      SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?
                  ))
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            fillStatement(statement, vehicle, photoPath);
            statement.setInt(12, vehicle.getId());
            statement.setInt(13, userId);
            statement.setInt(14, userId);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                showAlert(Alert.AlertType.WARNING, "Změna nebyla provedena", "Vozidlo nepatří přihlášenému uživateli.");
                return;
            }
            loadVehicles();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(
                    Alert.AlertType.ERROR,
                    "Chyba databáze",
                    "Změny vozidla se nepodařilo uložit."
            );
        }
    }
    private void fillStatement(
            PreparedStatement statement,
            Vehicle vehicle,
            String photoPath) throws SQLException {
        statement.setString(1, vehicle.getBrand());
        statement.setString(2, vehicle.getModel());
        statement.setString(3, vehicle.getRegistration());
        statement.setInt(
                4, Integer.parseInt(vehicle.getYear()));
        statement.setInt(
                5, Integer.parseInt(vehicle.getOdometer()));
        statement.setString(6, photoPath);
        statement.setString(7, vehicle.getEngine());
        statement.setString(8, vehicle.getFuelType());
        statement.setString(9, vehicle.getVin());
        statement.setString(10, vehicle.getColor());
        statement.setString(11, vehicle.getNotes());
    }
    // zkopirovani fotografie do slozky aplikace
    private String copyPhoto(File file) {
        if (file == null) {
            return null;
        }
        try {
            Path directory = Path.of("vehicle_photos");
            Files.createDirectories(directory);
            String originalName = file.getName();
            int dot = originalName.lastIndexOf('.');
            String extension = dot >= 0
                    ? originalName.substring(dot)
                    : "";
            Path destination = directory.resolve(
                    UUID.randomUUID() + extension);
            Files.copy(
                    file.toPath(),
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
            return destination.toString();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(
                    Alert.AlertType.ERROR,
                    "Chyba fotografie",
                    "Fotografii se nepodařilo uložit."
            );
            return null;
        }
    }
    // nacteni vozidel z databaze
    private void loadVehicles() {
        vehicles.clear();
        String sql = """
                SELECT id, brand, model, registration, year, odometer,
                       photo_path, engine, fuel_type, vin, color, notes
                FROM vehicles
                WHERE user_id = ?
                   OR id IN (
                       SELECT vehicle_id FROM vehicle_shares WHERE user_id = ?
                   )
                ORDER BY id DESC
                """;
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId <= 0) {
                updateView();
                return;
            }
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                vehicles.add(new Vehicle(
                        resultSet.getInt("id"),
                        resultSet.getString("brand"),
                        resultSet.getString("model"),
                        resultSet.getString("registration"),
                        resultSet.getString("year"),
                        resultSet.getString("odometer"),
                        resultSet.getString("photo_path"),
                        resultSet.getString("engine"),
                        resultSet.getString("fuel_type"),
                        resultSet.getString("vin"),
                        resultSet.getString("color"),
                        resultSet.getString("notes")
                ));
            }
            }
            updateView();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(
                    Alert.AlertType.ERROR,
                    "Chyba databáze",
                    "Nepodařilo se načíst vozidla."
            );
        }
    }
    // ==========================================
    // VYHLEDAVANI A ZOBRAZENI
    // ==========================================
    private void filterVehicles(String searchText) {
        String query = searchText == null
                ? ""
                : searchText.trim().toLowerCase(Locale.ROOT);
        filteredVehicles.setPredicate(vehicle ->
                vehicle.getBrand().toLowerCase(Locale.ROOT)
                        .contains(query)
                || vehicle.getModel().toLowerCase(Locale.ROOT)
                        .contains(query)
                || vehicle.getRegistration().toLowerCase(Locale.ROOT)
                        .contains(query)
        );
    }
    private void updateView() {
        boolean isEmpty = vehicles.isEmpty();
        emptyState.setVisible(isEmpty);
        emptyState.setManaged(isEmpty);
        vehiclesTable.setVisible(!isEmpty);
        vehiclesTable.setManaged(!isEmpty);
        vehicleCountLabel.setText(
                vehicles.size() == 1
                        ? "1 vozidlo"
                        : vehicles.size() + " vozidel"
        );
    }
    private String safe(String value) {
        return value == null ? "" : value;
    }
    // zobrazeni upozorneni
    private void showAlert(
            Alert.AlertType type,
            String title,
            String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    // ==========================================
    // DATOVA TRIDA VOZIDLA
    // ==========================================
    public static class Vehicle {
        private final int id;
        private final String brand;
        private final String model;
        private final String registration;
        private final String year;
        private final String odometer;
        private final String photoPath;
        private final String engine;
        private final String fuelType;
        private final String vin;
        private final String color;
        private final String notes;
        public Vehicle(
                int id,
                String brand,
                String model,
                String registration,
                String year,
                String odometer,
                String photoPath,
                String engine,
                String fuelType,
                String vin,
                String color,
                String notes) {
            this.id = id;
            this.brand = brand;
            this.model = model;
            this.registration = registration;
            this.year = year;
            this.odometer = odometer;
            this.photoPath = photoPath;
            this.engine = engine;
            this.fuelType = fuelType;
            this.vin = vin;
            this.color = color;
            this.notes = notes;
        }
        public int getId() {
            return id;
        }
        public String getBrand() {
            return brand;
        }
        public String getModel() {
            return model;
        }
        public String getRegistration() {
            return registration;
        }
        public String getYear() {
            return year;
        }
        public String getOdometer() {
            return odometer;
        }
        public String getPhotoPath() {
            return photoPath;
        }
        public String getEngine() {
            return engine;
        }
        public String getFuelType() {
            return fuelType;
        }
        public String getVin() {
            return vin;
        }
        public String getColor() {
            return color;
        }
        public String getNotes() {
            return notes;
        }
    }
}
