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

    private String username;
    private int userId = -1;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void openDashboard() {
        handleDashboard();
    }

    public void showRegistrationSuccess() {
        if (successMessage == null) {
            return;
        }

        successMessage.setVisible(true);
        successMessage.setManaged(true);

        PauseTransition pause = new PauseTransition(Duration.seconds(2));

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

    @FXML
    private void handleDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/DashboardView.fxml")
            );

            Parent view = loader.load();

            DashboardController controller = loader.getController();
            controller.setUsername(username);
            controller.setOnAddVehicle(this::handleVehicles);
            controller.setUserId(userId);

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleVehicles() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/VehiclesView.fxml")
            );

            Parent view = loader.load();

            VehiclesController controller = loader.getController();
            controller.setUserId(userId);

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleFuelings() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/FuelingsView.fxml")
            );

            Parent view = loader.load();

            FuelingsController controller = loader.getController();
            controller.setUserId(userId);

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleService() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/ServisView.fxml")
            );

            Parent view = loader.load();

            ServisController controller = loader.getController();
            controller.setUserId(userId);

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCosts() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/NakladyView.fxml")
            );

            Parent view = loader.load();

            NakladyController controller = loader.getController();
            controller.setUserId(userId);

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleStatistics() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/StatistikyView.fxml")
            );

            Parent view = loader.load();

            StatistikyController controller = loader.getController();
            controller.setUserId(userId);

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/NastaveniView.fxml")
            );

            Parent view = loader.load();

            NastaveniController controller = loader.getController();
            controller.setUserId(userId);

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/LoginView.fxml")
            );

            Parent root = loader.load();

            Stage stage = (Stage) contentArea.getScene().getWindow();

            stage.setTitle("Vehicle Manager - Přihlášení");
            stage.setScene(new Scene(root, 1100, 700));
            stage.centerOnScreen();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
