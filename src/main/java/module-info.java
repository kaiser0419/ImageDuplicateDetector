module com.imgdupl {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.desktop;

    // CRUCIAL: Allow the core JavaFX graphics engine to see these packages
    opens com.imgdupl.ui   to javafx.fxml, javafx.graphics;
    opens com.imgdupl.core to javafx.fxml, javafx.graphics;

    exports com.imgdupl.ui;
    exports com.imgdupl.core;
    exports com.imgdupl.util;
}