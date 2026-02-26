package edu.facultysync.model;

/**
 * Represents a university department.
 */
public class Department {
    private Integer deptId;
    private String name;

    public Department() {}

    public Department(Integer deptId, String name) {
        this.deptId = deptId;
        this.name = name;
    }

    public Integer getDeptId() { return deptId; }
    public void setDeptId(Integer deptId) { this.deptId = deptId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public String toString() { return name != null ? name : "Department#" + deptId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Department that = (Department) o;
        return deptId != null && deptId.equals(that.deptId);
    }

    @Override
    public int hashCode() { return deptId != null ? deptId.hashCode() : 0; }
}
