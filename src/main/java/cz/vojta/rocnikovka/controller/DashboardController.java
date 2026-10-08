package cz.vojta.rocnikovka.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class DashboardController {

    @FXML
    private Label userNameLabel;


    // nastaveni jmena prihlaseneho uzivatele

    public void setUsername(String username) {
        userNameLabel.setText(username);
    }
}