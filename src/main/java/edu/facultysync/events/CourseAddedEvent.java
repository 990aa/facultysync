package edu.facultysync.events;

import edu.facultysync.model.Course;

/**
 * Domain event emitted after a course is successfully created.
 */
public record CourseAddedEvent(Course course) {
}
