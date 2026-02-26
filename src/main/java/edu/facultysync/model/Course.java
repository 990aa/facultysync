package edu.facultysync.model;

/**
 * Represents a course taught by a professor.
 */
public class Course {
    private Integer courseId;
    private String courseCode;
    private Integer profId;
    private Integer enrollmentCount;

    public Course() {}

    public Course(Integer courseId, String courseCode, Integer profId, Integer enrollmentCount) {
        this.courseId = courseId;
        this.courseCode = courseCode;
        this.profId = profId;
        this.enrollmentCount = enrollmentCount;
    }

    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public Integer getProfId() { return profId; }
    public void setProfId(Integer profId) { this.profId = profId; }

    public Integer getEnrollmentCount() { return enrollmentCount; }
    public void setEnrollmentCount(Integer enrollmentCount) { this.enrollmentCount = enrollmentCount; }

    @Override
    public String toString() { return courseCode != null ? courseCode : "Course#" + courseId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Course that = (Course) o;
        return courseId != null && courseId.equals(that.courseId);
    }

    @Override
    public int hashCode() { return courseId != null ? courseId.hashCode() : 0; }
}
