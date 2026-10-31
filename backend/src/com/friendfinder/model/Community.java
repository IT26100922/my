package com.friendfinder.model;

public final class Community {
    private final long id; private final String name; private final long leaderId;
    public Community(long id, String name, long leaderId) { if (name == null || name.isBlank()) throw new IllegalArgumentException("Community name required"); this.id = id; this.name = name.trim(); this.leaderId = leaderId; }
    public long getId() { return id; }
    public String getName() { return name; }
    public long getLeaderId() { return leaderId; }
}
