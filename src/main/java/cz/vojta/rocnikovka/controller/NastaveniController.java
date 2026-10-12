package cz.vojta.rocnikovka.controller;

import cz.vojta.rocnikovka.Database;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class NastaveniController {
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label accountMessageLabel;
    @FXML private Label passwordMessageLabel;

    private int userId = -1;

    @FXML
    private void initialize() {
        ensureEmailColumn();
        accountMessageLabel.setText("");
        passwordMessageLabel.setText("");
    }

    public void setUserId(int userId) {
        this.userId = userId;
        loadUser();
    }

    private void ensureEmailColumn() {
        try (Connection connection = Database.connect()) {
            boolean exists = false;
            try (var statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("PRAGMA table_info(users)")) {
                while (resultSet.next()) {
                    if ("email".equalsIgnoreCase(resultSet.getString("name"))) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                try (var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE users ADD COLUMN email TEXT");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadUser() {
        if (userId <= 0) return;

        String sql = "SELECT username, COALESCE(email, '') AS email FROM users WHERE id = ?";
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    usernameField.setText(resultSet.getString("username"));
                    emailField.setText(resultSet.getString("email"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showMessage(Alert.AlertType.ERROR, "Chyba databáze",
                    "Nepodařilo se načíst údaje uživatelského účtu.");
        }
    }

    @FXML
    private void handleSaveAccount() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();

        if (userId <= 0) {
            showMessage(Alert.AlertType.ERROR, "Účet není dostupný", "Nepodařilo se určit přihlášeného uživatele.");
            return;
        }
        if (username.isBlank()) {
            showMessage(Alert.AlertType.WARNING, "Chybí uživatelské jméno", "Zadej uživatelské jméno.");
            return;
        }
        if (!email.isBlank() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            showMessage(Alert.AlertType.WARNING, "Neplatný e-mail", "Zkontroluj formát e-mailové adresy.");
            return;
        }

        String sql = "UPDATE users SET username = ?, email = ? WHERE id = ?";
        try (Connection connection = Database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, email.isBlank() ? null : email);
            statement.setInt(3, userId);
            statement.executeUpdate();
            accountMessageLabel.setText("Změny byly úspěšně uloženy.");
            accountMessageLabel.setStyle("-fx-text-fill: #16803c;");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                showMessage(Alert.AlertType.WARNING, "Jméno už existuje",
                        "Toto uživatelské jméno už používá jiný účet.");
            } else {
                e.printStackTrace();
                showMessage(Alert.AlertType.ERROR, "Chyba při ukládání",
                        "Údaje se nepodařilo uložit.");
            }
        }
    }

    @FXML
    private void handleChangePassword() {
        String current = currentPasswordField.getText();
        String next = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (userId <= 0) {
            showMessage(Alert.AlertType.ERROR, "Účet není dostupný", "Nepodařilo se určit přihlášeného uživatele.");
            return;
        }
        if (current.isBlank() || next.isBlank() || confirm.isBlank()) {
            showMessage(Alert.AlertType.WARNING, "Vyplň všechna pole", "Pro změnu hesla vyplň všechna tři pole.");
            return;
        }
        if (next.length() < 8) {
            showMessage(Alert.AlertType.WARNING, "Slabé heslo", "Nové heslo musí mít alespoň 8 znaků.");
            return;
        }
        if (!next.equals(confirm)) {
            showMessage(Alert.AlertType.WARNING, "Hesla se neshodují", "Potvrzení nového hesla není stejné.");
            return;
        }

        try (Connection connection = Database.connect();
             PreparedStatement check = connection.prepareStatement("SELECT password FROM users WHERE id = ?")) {
            check.setInt(1, userId);
            try (ResultSet resultSet = check.executeQuery()) {
                if (!resultSet.next() || !current.equals(resultSet.getString("password"))) {
                    showMessage(Alert.AlertType.WARNING, "Nesprávné heslo", "Současné heslo není správné.");
                    return;
                }
            }

            try (PreparedStatement update = connection.prepareStatement("UPDATE users SET password = ? WHERE id = ?")) {
                update.setString(1, next);
                update.setInt(2, userId);
                update.executeUpdate();
            }

            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
            passwordMessageLabel.setText("Heslo bylo změněno.");
            passwordMessageLabel.setStyle("-fx-text-fill: #16803c;");
        } catch (SQLException e) {
            e.printStackTrace();
            showMessage(Alert.AlertType.ERROR, "Chyba databáze", "Heslo se nepodařilo změnit.");
        }
    }

    private void showMessage(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
