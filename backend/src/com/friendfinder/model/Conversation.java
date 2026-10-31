package com.friendfinder.model;

import java.util.*;

public abstract class Conversation {
    private final long id; private final long creatorId; private final List<Member> participants;
    protected Conversation(long id, long creatorId, List<Member> participants) { this.id = id; this.creatorId = creatorId; this.participants = List.copyOf(participants); }
    public long getId() { return id; } public long getCreatorId() { return creatorId; }
    public List<Member> getParticipants() { return participants; }
    public abstract String getType();
    public abstract String getDisplayName(long viewerId);
}
