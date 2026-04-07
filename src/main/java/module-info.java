module edu.facultysync {
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires java.sql;
    requires java.desktop;
    requires org.slf4j;
    requires org.apache.commons.csv;
    requires com.google.common;

    exports edu.facultysync;

    opens edu.facultysync.ui to com.google.common;
}
