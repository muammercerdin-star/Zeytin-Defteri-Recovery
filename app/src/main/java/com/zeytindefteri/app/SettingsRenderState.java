package com.zeytindefteri.app;

/** Per-view request state, not a role cache. No static/user-shared authorization state. */
final class SettingsRenderState {
    private long generation;
    private String owner;
    private boolean active, pending;

    void activate() { active = true; }
    void invalidate() { active = false; pending = false; generation++; }
    boolean active() { return active; }
    long begin(String currentOwner) {
        if (!active || (pending && currentOwner.equals(owner))) return -1;
        owner = currentOwner;
        pending = true;
        return ++generation;
    }
    boolean accept(long request, String currentOwner) {
        if (!active || !pending || request != generation || !currentOwner.equals(owner)) return false;
        pending = false;
        return true;
    }
}
