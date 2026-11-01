module org.example.bloggingplatformi {
    requires javafx.controls;

    // Relational access. java.sql brings DriverManager/DataSource; the
    // PostgreSQL driver is loaded as a service, not referenced directly.
    requires java.sql;
    requires com.zaxxer.hikari;

    // Document access.
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;

    requires org.slf4j;

    exports org.example.bloggingplatformi;
    exports org.example.bloggingplatformi.config;
    exports org.example.bloggingplatformi.model;
    exports org.example.bloggingplatformi.ui;
    exports org.example.bloggingplatformi.view;
}
