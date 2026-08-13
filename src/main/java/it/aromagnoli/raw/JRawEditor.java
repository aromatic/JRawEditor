package it.aromagnoli.raw;

import it.aromagnoli.raw.ui.RawEditorController;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class JRawEditor extends Application {

    @Override
    public void start(Stage primaryStage) {
        RawEditorController controller = new RawEditorController(primaryStage);

        Scene scene = new Scene(controller.getRootLayout(), 1200, 800);
        
        primaryStage.setTitle("JavaFX RAW Photo Editor - Powered by LibRaw & Java FFM");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
