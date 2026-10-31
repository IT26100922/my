package com.friendfinder.model;

public final class Interest {
    private final long id; private final String name;
    public Interest(long id, String name) { if (name == null || name.isBlank()) throw new IllegalArgumentException("Interest name required"); this.id = id; this.name = name.trim(); }
    public long getId() { return id; }
    public String getName() { return name; }
}
