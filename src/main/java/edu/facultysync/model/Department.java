package edu.facultysync.model;

/**
 * Immutable department data carrier.
 */
public record Department(Integer deptId, String name) {

    public Department() {
        this(null, null);
    }

    public Integer getDeptId() {
        return deptId;
    }

    public String getName() {
        return name;
    }

    public Department withDeptId(Integer newDeptId) {
        return new Department(newDeptId, name);
    }

    public Department withName(String newName) {
        return new Department(deptId, newName);
    }

    @Override
    public String toString() {
        return name != null ? name : "Department#" + deptId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Department that)) return false;
        return deptId != null && deptId.equals(that.deptId);
    }

    @Override
    public int hashCode() {
        return deptId != null ? deptId.hashCode() : 0;
    }
}
