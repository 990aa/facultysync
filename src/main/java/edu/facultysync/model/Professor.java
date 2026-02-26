package edu.facultysync.model;

/**
 * Represents a professor belonging to a department.
 */
public class Professor {
    private Integer profId;
    private String name;
    private Integer deptId;

    public Professor() {}

    public Professor(Integer profId, String name, Integer deptId) {
        this.profId = profId;
        this.name = name;
        this.deptId = deptId;
    }

    public Integer getProfId() { return profId; }
    public void setProfId(Integer profId) { this.profId = profId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getDeptId() { return deptId; }
    public void setDeptId(Integer deptId) { this.deptId = deptId; }

    @Override
    public String toString() { return name != null ? name : "Professor#" + profId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Professor that = (Professor) o;
        return profId != null && profId.equals(that.profId);
    }

    @Override
    public int hashCode() { return profId != null ? profId.hashCode() : 0; }
}
