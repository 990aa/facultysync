module edu.facultysync {
    requires javafx.controls;
    requires javafx.graphics;
    requires java.sql;
    requires java.desktop;
    requires org.slf4j;
    requires org.apache.commons.csv;
    requires com.google.common;

    exports edu.facultysync;
    exports edu.facultysync.algo;
    exports edu.facultysync.core;
    exports edu.facultysync.db;
    exports edu.facultysync.events;
    exports edu.facultysync.io;
    exports edu.facultysync.model;
    exports edu.facultysync.service;
    exports edu.facultysync.ui;
    exports edu.facultysync.util;

    opens edu.facultysync.ui to com.google.common;
}
