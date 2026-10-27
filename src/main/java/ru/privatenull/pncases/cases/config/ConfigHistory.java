package ru.privatenull.pncases.cases.config;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** The last few saved states of each case file, newest first, kept in memory for undo. */
public final class ConfigHistory {

    private final int limit;
    private final Map<String, Deque<Map<String, Object>>> snapshots = new HashMap<>();

    public ConfigHistory(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit");
        this.limit = limit;
    }

    /** Remembers the state a case had before a change. The oldest snapshot is dropped past the limit. */
    public synchronized void push(String caseId, Map<String, Object> snapshot) {
        Deque<Map<String, Object>> stack = snapshots.computeIfAbsent(caseId, id -> new ArrayDeque<>());
        stack.addFirst(snapshot);
        while (stack.size() > limit) stack.removeLast();
    }

    /** Removes and returns the most recent snapshot, or null when there is nothing to undo. */
    public synchronized Map<String, Object> pop(String caseId) {
        Deque<Map<String, Object>> stack = snapshots.get(caseId);
        if (stack == null || stack.isEmpty()) return null;
        Map<String, Object> snapshot = stack.removeFirst();
        if (stack.isEmpty()) snapshots.remove(caseId);
        return snapshot;
    }

    /** How many changes can still be undone. */
    public synchronized int depth(String caseId) {
        Deque<Map<String, Object>> stack = snapshots.get(caseId);
        return stack == null ? 0 : stack.size();
    }

    public synchronized void clear(String caseId) {
        snapshots.remove(caseId);
    }
}
