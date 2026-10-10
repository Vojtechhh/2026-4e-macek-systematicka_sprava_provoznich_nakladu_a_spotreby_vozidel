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

public class RegisterController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label messageLabel;

    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            messageLabel.setText("Vyplňte všechna pole.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            messageLabel.setText("Hesla se neshodují.");
            return;
        }
        if (password.length() < 6) {
            messageLabel.setText("Heslo musí mít alespoň 6 znaků.");
            return;
        }
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, password);
            statement.executeUpdate();
            int userId = findUserId(connection, username);
            if (userId <= 0) {
                messageLabel.setText("Účet byl vytvořen, ale nepodařilo se načíst jeho ID.");
                return;
            }
            openDashboard(userId, username);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                messageLabel.setStyle("-fx-text-fill: #f87171;");
                messageLabel.setText("Toto uživatelské jméno již existuje.");
            } else {
                messageLabel.setStyle("-fx-text-fill: #f87171;");
                messageLabel.setText("Při registraci nastala chyba.");
                e.printStackTrace();
            }
        }
    }

    private int findUserId(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("id") : -1;
            }
        }
    }

    private void openDashboard(int userId, String username) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
            Parent root = loader.load();
            MainController mainController = loader.getController();
            mainController.setUserId(userId);
            mainController.setUsername(username);
            mainController.openDashboard();
            mainController.showRegistrationSuccess();
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setTitle("Vehicle Manager");
            stage.setScene(new Scene(root, 1200, 750));
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setTitle("Vehicle Manager - Přihlášení");
            stage.setScene(new Scene(root, 1100, 700));
            stage.centerOnScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
