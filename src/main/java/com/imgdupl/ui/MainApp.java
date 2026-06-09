package com.imgdupl.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.URL;

public class MainApp extends Application {

    public static final String APP_TITLE   = "Image Duplicate Detector";
    public static final double MIN_WIDTH   = 1100;
    public static final double MIN_HEIGHT  = 720;

    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView(stage);
        Scene scene = new Scene(mainView.getRoot(), MIN_WIDTH, MIN_HEIGHT);

        // Safe resource loading for Modular JavaFX
        try {
            // The leading slash '/' explicitly tells Java to look at the root of the resources folder
            URL cssUrl = MainApp.class.getResource("/styles.css");

            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                System.out.println("✅ Dark industrial UI stylesheet applied successfully.");
            } else {
                System.err.println("❌ Error: Could not find '/styles.css' at the root of resources folder.");
            }
        } catch (Exception e) {
            System.err.println("💥 Exception occurred while applying stylesheet:");
            e.printStackTrace();
        }

        stage.setTitle(APP_TITLE);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}