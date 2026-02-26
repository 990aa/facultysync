package edu.facultysync.model;

/**
 * Represents a physical location (room) in the university.
 * has_projector uses Integer (0/1) to act as Boolean in SQLite.
 */
public class Location {
    private Integer locId;
    private String building;
    private String roomNumber;
    private Integer capacity;
    private Integer hasProjector; // 0 or 1, nullable

    public Location() {}

    public Location(Integer locId, String building, String roomNumber, Integer capacity, Integer hasProjector) {
        this.locId = locId;
        this.building = building;
        this.roomNumber = roomNumber;
        this.capacity = capacity;
        this.hasProjector = hasProjector;
    }

    public Integer getLocId() { return locId; }
    public void setLocId(Integer locId) { this.locId = locId; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getHasProjector() { return hasProjector; }
    public void setHasProjector(Integer hasProjector) { this.hasProjector = hasProjector; }

    /** Friendly display name for UI. */
    public String getDisplayName() {
        return (building != null ? building : "?") + " " + (roomNumber != null ? roomNumber : "?");
    }

    @Override
    public String toString() { return getDisplayName(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location that = (Location) o;
        return locId != null && locId.equals(that.locId);
    }

    @Override
    public int hashCode() { return locId != null ? locId.hashCode() : 0; }
}
