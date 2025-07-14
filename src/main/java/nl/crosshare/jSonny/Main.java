package nl.crosshare.jSonny;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;

public class Main extends Application {

    private Stage primaryStage;
    private SplitPane splitPane;
    private File configFile;

//    @Override
//    public void start(Stage stage) throws Exception {
//        // Load the FXML file
//        URL layout = getClass().getResource("/layout.fxml");
//        Parent root = FXMLLoader.load(layout);
//        stage.setTitle("jSonny - JSON API Client");
//        stage.setScene(new Scene(root, 1400, 800));
//        stage.show();
//    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load config
        File configFile = new File("src/main/resources/window-config.xml");
        WindowConfig config = WindowConfig.load(configFile);

        // Load FXML and get SplitPane reference
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout.fxml"));
        Parent root = loader.load();
        SplitPane splitPane = (SplitPane) root.lookup("#splitPaneMain");

        // Set window size and position
        primaryStage.setX(config.x);
        primaryStage.setY(config.y);
        primaryStage.setWidth(config.width);
        primaryStage.setHeight(config.height);

        // Set SplitPane dividers
        splitPane.setDividerPositions(config.dividers);

        primaryStage.setScene(new Scene(root));
        primaryStage.show();
        primaryStage.setTitle("jSonny - JSON API Client");

        // Store references for stop()
        this.primaryStage = primaryStage;
        this.splitPane = splitPane;
        this.configFile = configFile;
    }

    @Override
    public void stop() throws Exception {
        // Save current window and SplitPane state
        WindowConfig config = new WindowConfig();
        config.x = primaryStage.getX();
        config.y = primaryStage.getY();
        config.width = primaryStage.getWidth();
        config.height = primaryStage.getHeight();
        config.dividers = splitPane.getDividerPositions();
        config.save(configFile);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
