module org.example.bloggingplatformi {
    requires javafx.controls;
    requires javafx.fxml;

    // PostgreSQL access. The driver is found at runtime, so it is not listed here.
    requires java.sql;
    requires com.zaxxer.hikari;

    // MongoDB access.
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;

    requires org.slf4j;

    exports org.example.bloggingplatformi;
    exports org.example.bloggingplatformi.config;
    exports org.example.bloggingplatformi.model;
    exports org.example.bloggingplatformi.ui;
    exports org.example.bloggingplatformi.controller;

    // FXMLLoader fills the @FXML fields of the controllers, so it needs access to them.
    opens org.example.bloggingplatformi.controller to javafx.fxml;
    opens org.example.bloggingplatformi.ui to javafx.fxml;
}
