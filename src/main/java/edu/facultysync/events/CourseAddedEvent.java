package edu.facultysync.events;

import edu.facultysync.model.Course;

/**
 * Domain event emitted after a course is successfully created.
 *
 * @param course the persisted course instance including generated identifiers
 */
public record CourseAddedEvent(Course course) {
}
