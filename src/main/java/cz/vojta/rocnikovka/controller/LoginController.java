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
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label messageLabel;


    @FXML
    private void handleLogin() {

        String username = usernameField.getText().trim();
        String password = passwordField.getText();


        // kontrola prazdnych poli

        if (username.isEmpty() || password.isEmpty()) {

            messageLabel.setText(
                    "Vyplňte uživatelské jméno a heslo."
            );

            return;
        }


        // hledani uzivatele v databazi

        String sql = """
                SELECT id, username, password
                FROM users
                WHERE username = ?
                """;


        try (Connection connection = Database.connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);

            ResultSet resultSet = statement.executeQuery();


            // uzivatel existuje

            if (resultSet.next()) {

                String databasePassword =
                        resultSet.getString("password");


                // kontrola hesla

                if (password.equals(databasePassword)) {

                    openDashboard(username);

                } else {

                    messageLabel.setText(
                            "Nesprávné uživatelské jméno nebo heslo."
                    );
                }

            } else {

                messageLabel.setText(
                        "Nesprávné uživatelské jméno nebo heslo."
                );
            }


        } catch (SQLException e) {

            messageLabel.setText(
                    "Při přihlašování nastala chyba."
            );

            e.printStackTrace();
        }
    }


    // otevreni dashboardu

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


    // otevreni registrace

    @FXML
    private void handleOpenRegister() {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/fxml/RegisterView.fxml"
                    )
            );

            Parent root = loader.load();

            Stage stage =
                    (Stage) usernameField.getScene().getWindow();

            Scene scene =
                    new Scene(root, 1100, 700);

            stage.setTitle(
                    "Vehicle Manager - Registrace"
            );

            stage.setScene(scene);

            stage.centerOnScreen();


        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}