package edu.facultysync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {

    @Test
    void windowTitle_doesNotIncludeVersion() {
        assertEquals("FacultySync", App.windowTitle());
    }
}
