package cz.vojta.rocnikovka;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        Database.initialize();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/LoginView.fxml")
        );

        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 700);

        stage.setTitle("Vehicle Manager - Přihlášení");
        stage.setScene(scene);

        // min velikost okna
        stage.setMinWidth(900);
        stage.setMinHeight(600);

        // otevre okno uprostred obrazovky
        stage.centerOnScreen();

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}