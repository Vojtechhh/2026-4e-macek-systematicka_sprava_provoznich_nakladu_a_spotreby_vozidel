package cz.vojta.rocnikovka.controller;

import cz.vojta.rocnikovka.Database;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class RegisterController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label messageLabel;


    @FXML
    private void handleRegister() {

        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();


        // kontrola prazdnych poli

        if (username.isEmpty() ||
                password.isEmpty() ||
                confirmPassword.isEmpty()) {

            messageLabel.setText("Vyplňte všechna pole.");
            return;
        }


        // kontrola hesel

        if (!password.equals(confirmPassword)) {

            messageLabel.setText("Hesla se neshodují.");
            return;
        }


        // kontrola delky hesla

        if (password.length() < 6) {

            messageLabel.setText(
                    "Heslo musí mít alespoň 6 znaků."
            );

            return;
        }


        // ulozeni uzivatele do databaze

        String sql = """
                INSERT INTO users (username, password)
                VALUES (?, ?)
                """;


        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);
            statement.setString(2, password);

            statement.executeUpdate();


            // ucet byl vytvoren

            openDashboard(username);


        } catch (SQLException e) {

            if (e.getMessage().contains("UNIQUE")) {

                messageLabel.setStyle(
                        "-fx-text-fill: #f87171;"
                );

                messageLabel.setText(
                        "Toto uživatelské jméno již existuje."
                );

            } else {

                messageLabel.setStyle(
                        "-fx-text-fill: #f87171;"
                );

                messageLabel.setText(
                        "Při registraci nastala chyba."
                );

                e.printStackTrace();
            }
        }
    }


    // otevreni dashboardu po registraci

    private void openDashboard(String username) {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/MainView.fxml"
                    )
            );

            Parent root = loader.load();


            // predani uzivatelskeho jmena dashboardu

            MainController mainController =
                    loader.getController();

            mainController.setUsername(username);
            mainController.openDashboard();


            // zobrazeni potvrzeni o uspesne registraci

            mainController.showRegistrationSuccess();


            Stage stage =
                    (Stage) usernameField.getScene().getWindow();

            Scene scene =
                    new Scene(root, 1200, 750);

            stage.setTitle("Vehicle Manager");

            stage.setScene(scene);

            stage.centerOnScreen();


        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    // navrat na prihlaseni

    @FXML
    private void handleBackToLogin() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/LoginView.fxml"
                    )
            );

            Parent root = loader.load();

            Stage stage =
                    (Stage) usernameField.getScene().getWindow();

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