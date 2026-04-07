package edu.facultysync.algo;

import edu.facultysync.model.Schedulable;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the generic IntervalTree.
 */
class IntervalTreeTest {

    /** Simple Schedulable implementation for testing. */
    static class Interval implements Schedulable {
        final long start, end;
        final String label;

        Interval(long start, long end) { this(start, end, null); }
        Interval(long start, long end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }

        @Override public long getStart() { return start; }
        @Override public long getEnd() { return end; }
        @Override public String toString() { return "[" + start + "," + end + (label != null ? " " + label : "") + ")"; }
    }

    // ===== Constructor =====

    @Test
    void emptyTree() {
        IntervalTree<Interval> tree = new IntervalTree<>();
        assertTrue(tree.isEmpty());
        assertEquals(0, tree.size());
        assertTrue(tree.queryOverlaps(0, 100).isEmpty());
        assertTrue(tree.findAllOverlaps().isEmpty());
        assertTrue(tree.inOrder().isEmpty());
    }

    @Test
    void nullListConstructor() {
        IntervalTree<Interval> tree = new IntervalTree<>(null);
        assertTrue(tree.isEmpty());
    }

    @Test
    void emptyListConstructor() {
        IntervalTree<Interval> tree = new IntervalTree<>(Collections.emptyList());
        assertTrue(tree.isEmpty());
    }

    @Test
    void singleElement() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(new Interval(10, 20)));
        assertFalse(tree.isEmpty());
        assertEquals(1, tree.size());
    }

    // ===== Insert =====

    @Test
    void insertIntoEmptyTree() {
        IntervalTree<Interval> tree = new IntervalTree<>();
        tree.insert(new Interval(5, 15));
        assertEquals(1, tree.size());
        assertFalse(tree.isEmpty());
    }

    @Test
    void insertMultiple() {
        IntervalTree<Interval> tree = new IntervalTree<>();
        tree.insert(new Interval(10, 20));
        tree.insert(new Interval(5, 15));
        tree.insert(new Interval(25, 35));
        assertEquals(3, tree.size());
    }

    // ===== Query Overlaps =====

    @Test
    void noOverlap_queryBeforeAll() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(100, 200),
                new Interval(300, 400)
        ));
        assertTrue(tree.queryOverlaps(0, 50).isEmpty());
    }

    @Test
    void noOverlap_queryAfterAll() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(100, 200),
                new Interval(300, 400)
        ));
        assertTrue(tree.queryOverlaps(500, 600).isEmpty());
    }

    @Test
    void noOverlap_queryBetween() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(100, 200),
                new Interval(300, 400)
        ));
        assertTrue(tree.queryOverlaps(200, 300).isEmpty());
    }

    @Test
    void exactBoundaryNoOverlap() {
        // [10,20) and query [20,30) – touch but don't overlap
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(new Interval(10, 20)));
        assertTrue(tree.queryOverlaps(20, 30).isEmpty());
    }

    @Test
    void overlap_partial() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(new Interval(10, 20)));
        List<Interval> result = tree.queryOverlaps(15, 25);
        assertEquals(1, result.size());
    }

    @Test
    void overlap_contained() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(new Interval(10, 50)));
        List<Interval> result = tree.queryOverlaps(20, 30);
        assertEquals(1, result.size());
    }

    @Test
    void overlap_containing() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(new Interval(20, 30)));
        List<Interval> result = tree.queryOverlaps(10, 50);
        assertEquals(1, result.size());
    }

    @Test
    void overlap_exact() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(new Interval(10, 20)));
        List<Interval> result = tree.queryOverlaps(10, 20);
        assertEquals(1, result.size());
    }

    @Test
    void overlap_multiple() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(10, 20, "A"),
                new Interval(15, 25, "B"),
                new Interval(30, 40, "C"),
                new Interval(35, 45, "D")
        ));
        List<Interval> result = tree.queryOverlaps(12, 22);
        assertEquals(2, result.size());
    }

    @Test
    void overlap_allElements() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(10, 20),
                new Interval(15, 25),
                new Interval(18, 30)
        ));
        List<Interval> result = tree.queryOverlaps(0, 100);
        assertEquals(3, result.size());
    }

    // ===== Find All Overlaps =====

    @Test
    void findAllOverlaps_noOverlaps() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(10, 20),
                new Interval(30, 40),
                new Interval(50, 60)
        ));
        assertTrue(tree.findAllOverlaps().isEmpty());
    }

    @Test
    void findAllOverlaps_singlePair() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(10, 25),
                new Interval(20, 35),
                new Interval(50, 60)
        ));
        List<List<Interval>> pairs = tree.findAllOverlaps();
        assertEquals(1, pairs.size());
    }

    @Test
    void findAllOverlaps_multiplePairs() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(10, 30, "A"),
                new Interval(20, 40, "B"),
                new Interval(25, 50, "C")
        ));
        List<List<Interval>> pairs = tree.findAllOverlaps();
        // A-B, A-C, B-C = 3 pairs
        assertEquals(3, pairs.size());
    }

    @Test
    void findAllOverlaps_identicalIntervals() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(10, 20, "A"),
                new Interval(10, 20, "B")
        ));
        List<List<Interval>> pairs = tree.findAllOverlaps();
        // Identical intervals overlap – at least 1 pair detected
        assertTrue(pairs.size() >= 1, "Expected at least 1 overlap pair for identical intervals");
    }

    // ===== In-Order Traversal =====

    @Test
    void inOrder_sorted() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(30, 40),
                new Interval(10, 20),
                new Interval(50, 60),
                new Interval(20, 30)
        ));
        List<Interval> sorted = tree.inOrder();
        assertEquals(4, sorted.size());
        for (int i = 0; i < sorted.size() - 1; i++) {
            assertTrue(sorted.get(i).getStart() <= sorted.get(i + 1).getStart());
        }
    }

    // ===== Large Dataset Stress Test =====

    @Test
    void stressTest_1000Intervals() {
        List<Interval> intervals = new ArrayList<>();
        Random rng = new Random(42);
        for (int i = 0; i < 1000; i++) {
            long start = rng.nextInt(100000);
            long end = start + rng.nextInt(1000) + 1;
            intervals.add(new Interval(start, end));
        }
        IntervalTree<Interval> tree = new IntervalTree<>(intervals);
        assertEquals(1000, tree.size());

        // Query should not throw and should be fast
        List<Interval> overlaps = tree.queryOverlaps(50000, 51000);
        assertNotNull(overlaps);
    }

    @Test
    void stressTest_insertThenQuery() {
        IntervalTree<Interval> tree = new IntervalTree<>();
        for (int i = 0; i < 500; i++) {
            tree.insert(new Interval(i * 10, i * 10 + 5));
        }
        assertEquals(500, tree.size());
        // Query middle range
        List<Interval> results = tree.queryOverlaps(2500, 2505);
        assertTrue(results.size() >= 1);
    }

    // ===== Edge Cases =====

    @Test
    void zeroLengthInterval_notIncluded() {
        // [10,10) has zero length – should not overlap with [10,20)
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(new Interval(10, 10)));
        List<Interval> result = tree.queryOverlaps(10, 20);
        // start < end (10 < 20) but end > start (10 > 10)? 10 > 10 is false, so no overlap
        assertTrue(result.isEmpty());
    }

    @Test
    void singlePointQuery() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(new Interval(10, 20)));
        // Query [15,16) – should overlap
        List<Interval> result = tree.queryOverlaps(15, 16);
        assertEquals(1, result.size());
    }

    @Test
    void negativeValues() {
        IntervalTree<Interval> tree = new IntervalTree<>(List.of(
                new Interval(-100, -50),
                new Interval(-60, -10)
        ));
        List<Interval> result = tree.queryOverlaps(-70, -55);
        assertEquals(2, result.size());
    }
}
