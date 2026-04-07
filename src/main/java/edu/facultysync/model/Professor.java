package edu.facultysync.model;

/**
 * Immutable professor data carrier.
 */
public record Professor(Integer profId, String name, Integer deptId) {

    public Professor() {
        this(null, null, null);
    }

    public Integer getProfId() {
        return profId;
    }

    public String getName() {
        return name;
    }

    public Integer getDeptId() {
        return deptId;
    }

    public Professor withProfId(Integer newProfId) {
        return new Professor(newProfId, name, deptId);
    }

    public Professor withName(String newName) {
        return new Professor(profId, newName, deptId);
    }

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
