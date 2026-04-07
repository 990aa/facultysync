package edu.facultysync.model;

/**
 * Immutable course data carrier.
 */
public record Course(Integer courseId, String courseCode, Integer profId, Integer enrollmentCount) {

    /**
     * Creates an empty course instance used by legacy code paths and serializers.
     */
    public Course() {
        this(null, null, null, null);
    }

    /**
     * Compatibility getter matching legacy POJO API.
     */
    public Integer getCourseId() {
        return courseId;
    }

    /**
     * Compatibility getter matching legacy POJO API.
     */
    public String getCourseCode() {
        return courseCode;
    }

    /**
     * Compatibility getter matching legacy POJO API.
     */
    public Integer getProfId() {
        return profId;
    }

    /**
     * Compatibility getter matching legacy POJO API.
     */
    public Integer getEnrollmentCount() {
        return enrollmentCount;
    }

    /**
     * Returns a copy with the supplied course identifier.
     */
    public Course withCourseId(Integer newCourseId) {
        return new Course(newCourseId, courseCode, profId, enrollmentCount);
    }

    /**
     * Returns a copy with the supplied course code.
     */
    public Course withCourseCode(String newCourseCode) {
        return new Course(courseId, newCourseCode, profId, enrollmentCount);
    }

    /**
     * Returns a copy associated with a different professor.
     */
    public Course withProfId(Integer newProfId) {
        return new Course(courseId, courseCode, newProfId, enrollmentCount);
    }

    /**
     * Returns a copy with an updated expected enrollment count.
     */
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
