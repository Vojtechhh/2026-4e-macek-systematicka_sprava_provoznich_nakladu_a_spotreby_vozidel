package cz.vojta.rocnikovka.controller;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MainController {

    @FXML
    private Label successMessage;

    @FXML
    private StackPane contentArea;

    // prihlasene uzivatelske jmeno
    private String username;

    // nastaveni jmena prihlaseneho uzivatele
    public void setUsername(String username) {
        this.username = username;
    }

    // automaticke otevreni dashboardu
    public void openDashboard() {
        handleDashboard();
    }

    // zobrazeni zpravy po registraci
    public void showRegistrationSuccess() {

        successMessage.setVisible(true);
        successMessage.setManaged(true);

        PauseTransition pause = new PauseTransition(
                Duration.seconds(2)
        );

        pause.setOnFinished(event -> {

            FadeTransition fade = new FadeTransition(
                    Duration.millis(400),
                    successMessage
            );

            fade.setFromValue(1);
            fade.setToValue(0);

            fade.setOnFinished(fadeEvent -> {

                successMessage.setVisible(false);
                successMessage.setManaged(false);
                successMessage.setOpacity(1);

            });

            fade.play();
        });

        pause.play();
    }

    // otevreni dashboardu
    @FXML
    private void handleDashboard() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/DashboardView.fxml"
                    )
            );

            Parent dashboardView = loader.load();

            DashboardController dashboardController =
                    loader.getController();

            dashboardController.setUsername(username);

            contentArea.getChildren().setAll(
                    dashboardView
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // otevreni zalozky vozidla
    @FXML
    private void handleVehicles() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/VehiclesView.fxml"
                    )
            );

            Parent vehiclesView = loader.load();

            contentArea.getChildren().setAll(
                    vehiclesView
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // otevreni zalozky tankovani
    @FXML
    private void handleFuelings() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/FuelingsView.fxml"
                    )
            );

            Parent fuelingsView = loader.load();

            contentArea.getChildren().setAll(
                    fuelingsView
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // otevreni zalozky servis
    @FXML
    private void handleService() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/ServisView.fxml"
                    )
            );

            Parent serviceView = loader.load();

            contentArea.getChildren().setAll(
                    serviceView
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // otevreni zalozky naklady
    @FXML
    private void handleCosts() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/NakladyView.fxml"
                    )
            );

            Parent costsView = loader.load();

            contentArea.getChildren().setAll(
                    costsView
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // otevreni zalozky statistiky
    @FXML
    private void handleStatistics() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/StatistikyView.fxml"
                    )
            );

            Parent statisticsView = loader.load();

            contentArea.getChildren().setAll(
                    statisticsView
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // otevreni zalozky nastaveni
    @FXML
    private void handleSettings() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/NastaveniView.fxml"
                    )
            );

            Parent settingsView = loader.load();

            contentArea.getChildren().setAll(
                    settingsView
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // odhlaseni uzivatele
    @FXML
    private void handleLogout() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/LoginView.fxml"
                    )
            );

            Parent root = loader.load();

            Stage stage =
                    (Stage) contentArea.getScene().getWindow();

            Scene scene =
                    new Scene(root, 1100, 700);

            stage.setTitle(
                    "Vehicle Manager - Přihlášení"
            );

            stage.setScene(scene);
            stage.centerOnScreen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}