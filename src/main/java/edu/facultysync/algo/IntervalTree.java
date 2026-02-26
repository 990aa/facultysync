package edu.facultysync.algo;

import edu.facultysync.model.Schedulable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A generic Interval Tree for O(log N) overlap detection.
 * <p>
 * Works with any type that implements {@link Schedulable}.
 * The tree is built statically from a list of intervals (balanced via median).
 *
 * @param <T> the type of schedulable items stored
 */
public class IntervalTree<T extends Schedulable> {

    private Node root;

    /** Internal node of the augmented BST. */
    private class Node {
        T interval;
        long maxEnd;   // maximum end value in this subtree
        Node left, right;

        Node(T interval) {
            this.interval = interval;
            this.maxEnd = interval.getEnd();
        }
    }

    /**
     * Build a balanced interval tree from an unsorted list.
     */
    public IntervalTree(List<T> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            root = null;
            return;
        }
        List<T> sorted = new ArrayList<>(intervals);
        sorted.sort((a, b) -> Long.compare(a.getStart(), b.getStart()));
        root = buildBalanced(sorted, 0, sorted.size() - 1);
    }

    /** Empty tree constructor. */
    public IntervalTree() {
        root = null;
    }

    private Node buildBalanced(List<T> sorted, int lo, int hi) {
        if (lo > hi) return null;
        int mid = lo + (hi - lo) / 2;
        Node node = new Node(sorted.get(mid));
        node.left = buildBalanced(sorted, lo, mid - 1);
        node.right = buildBalanced(sorted, mid + 1, hi);
        updateMax(node);
        return node;
    }

    private void updateMax(Node n) {
        if (n == null) return;
        n.maxEnd = n.interval.getEnd();
        if (n.left != null) n.maxEnd = Math.max(n.maxEnd, n.left.maxEnd);
        if (n.right != null) n.maxEnd = Math.max(n.maxEnd, n.right.maxEnd);
    }

    /**
     * Insert a single interval into the tree.
     */
    public void insert(T interval) {
        root = insert(root, interval);
    }

    private Node insert(Node node, T interval) {
        if (node == null) return new Node(interval);
        if (interval.getStart() <= node.interval.getStart()) {
            node.left = insert(node.left, interval);
        } else {
            node.right = insert(node.right, interval);
        }
        updateMax(node);
        return node;
    }

    /**
     * Query all intervals that overlap with the given range [start, end).
     * Two intervals overlap when: a.start < b.end AND a.end > b.start
     */
    public List<T> queryOverlaps(long start, long end) {
        List<T> results = new ArrayList<>();
        queryOverlaps(root, start, end, results);
        return results;
    }

    private void queryOverlaps(Node node, long start, long end, List<T> results) {
        if (node == null) return;
        // Prune: if the max end in this subtree is <= query start, no overlap possible
        if (node.maxEnd <= start) return;

        // Search left subtree
        queryOverlaps(node.left, start, end, results);

        // Check current node: overlap if node.start < end AND node.end > start
        if (node.interval.getStart() < end && node.interval.getEnd() > start) {
            results.add(node.interval);
        }

        // Prune right: if node's start >= end, no right children can overlap
        if (node.interval.getStart() >= end) return;

        // Search right subtree
        queryOverlaps(node.right, start, end, results);
    }

    /**
     * Detect ALL pairwise overlapping intervals in the tree.
     * Returns pairs as two-element lists.
     */
    public List<List<T>> findAllOverlaps() {
        List<T> all = inOrder();
        List<List<T>> conflicts = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            T a = all.get(i);
            List<T> overlaps = queryOverlaps(a.getStart(), a.getEnd());
            for (T b : overlaps) {
                if (a != b && a.getStart() <= b.getStart()) {
                    List<T> pair = new ArrayList<>();
                    pair.add(a);
                    pair.add(b);
                    conflicts.add(pair);
                }
            }
        }
        return conflicts;
    }

    /** In-order traversal to retrieve all intervals sorted by start. */
    public List<T> inOrder() {
        List<T> result = new ArrayList<>();
        inOrder(root, result);
        return result;
    }

    private void inOrder(Node node, List<T> result) {
        if (node == null) return;
        inOrder(node.left, result);
        result.add(node.interval);
        inOrder(node.right, result);
    }

    /** Returns true if the tree has no intervals. */
    public boolean isEmpty() {
        return root == null;
    }

    /** Returns the number of intervals (O(n) walk). */
    public int size() {
        return inOrder().size();
    }
}
