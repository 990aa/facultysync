package edu.facultysync.model;

/**
 * Interface for objects that represent a time interval.
 * Used by IntervalTree for O(log N) overlap detection.
 */
public interface Schedulable {
    long getStart();
    long getEnd();
}
