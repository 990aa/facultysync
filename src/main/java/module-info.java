module edu.facultysync {
    requires javafx.controls;
    requires javafx.graphics;
    requires java.sql;
    requires java.desktop;
    requires org.slf4j;
    requires org.apache.commons.csv;
    requires com.google.common;

    exports edu.facultysync;

    opens edu.facultysync.ui to com.google.common;
}
