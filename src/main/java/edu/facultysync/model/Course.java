package edu.facultysync.model;

/**
 * Immutable course data carrier.
 */
public record Course(Integer courseId, String courseCode, Integer profId, Integer enrollmentCount) {

    public Course() {
        this(null, null, null, null);
    }

    public Integer getCourseId() {
        return courseId;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public Integer getProfId() {
        return profId;
    }

    public Integer getEnrollmentCount() {
        return enrollmentCount;
    }

    public Course withCourseId(Integer newCourseId) {
        return new Course(newCourseId, courseCode, profId, enrollmentCount);
    }

    public Course withCourseCode(String newCourseCode) {
        return new Course(courseId, newCourseCode, profId, enrollmentCount);
    }

    public Course withProfId(Integer newProfId) {
        return new Course(courseId, courseCode, newProfId, enrollmentCount);
    }

    public Course withEnrollmentCount(Integer newEnrollmentCount) {
        return new Course(courseId, courseCode, profId, newEnrollmentCount);
    }

    @Override
    public String toString() {
        return courseCode != null ? courseCode : "Course#" + courseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Course that)) return false;
        return courseId != null && courseId.equals(that.courseId);
    }

    @Override
    public int hashCode() {
        return courseId != null ? courseId.hashCode() : 0;
    }
}
