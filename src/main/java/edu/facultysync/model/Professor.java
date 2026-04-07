package edu.facultysync.model;

/**
 * Immutable professor data carrier.
 */
public record Professor(Integer profId, String name, Integer deptId) {

    /**
     * Creates an empty professor instance used by legacy code paths and serializers.
     */
    public Professor() {
        this(null, null, null);
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
    public String getName() {
        return name;
    }

    /**
     * Compatibility getter matching legacy POJO API.
     */
    public Integer getDeptId() {
        return deptId;
    }

    /**
     * Returns a copy with the supplied professor identifier.
     */
    public Professor withProfId(Integer newProfId) {
        return new Professor(newProfId, name, deptId);
    }

    /**
     * Returns a copy with the supplied professor display name.
     */
    public Professor withName(String newName) {
        return new Professor(profId, newName, deptId);
    }

    /**
     * Returns a copy associated with a different department.
     */
    public Professor withDeptId(Integer newDeptId) {
        return new Professor(profId, name, newDeptId);
    }

    @Override
    public String toString() {
        return name != null ? name : "Professor#" + profId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Professor that)) return false;
        return profId != null && profId.equals(that.profId);
    }

    @Override
    public int hashCode() {
        return profId != null ? profId.hashCode() : 0;
    }
}
