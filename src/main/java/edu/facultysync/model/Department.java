package edu.facultysync.model;

/**
 * Immutable department data carrier.
 */
public record Department(Integer deptId, String name) {

    /**
     * Creates an empty department instance used by legacy code paths and serializers.
     */
    public Department() {
        this(null, null);
    }

    /**
     * Compatibility getter matching legacy POJO API.
     */
    public Integer getDeptId() {
        return deptId;
    }

    /**
     * Compatibility getter matching legacy POJO API.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns a copy with the supplied department identifier.
     */
    public Department withDeptId(Integer newDeptId) {
        return new Department(newDeptId, name);
    }

    /**
     * Returns a copy with the supplied department name.
     */
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
