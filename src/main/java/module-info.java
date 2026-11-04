module org.example.bloggingplatformi {
    requires javafx.controls;
    requires javafx.fxml;

    requires java.sql;                        // JDBC
    requires org.postgresql.jdbc;             // the PostgreSQL driver
    requires org.mongodb.driver.sync.client;  // MongoDB
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;

    exports org.example.bloggingplatformi;

    // FXMLLoader fills the @FXML fields of the controllers, so it needs access to them.
    opens org.example.bloggingplatformi.controller to javafx.fxml;
}
