package nl.crosshare.jSonny;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // Load the FXML file
        URL layout = getClass().getResource("/layout.fxml");
        Parent root = FXMLLoader.load(layout);
        stage.setTitle("jSonny - JSON API Client");
        stage.setScene(new Scene(root, 1400, 800));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
