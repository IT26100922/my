package com.friendfinder.model;

import java.util.*;

public final class GroupConversation extends Conversation {
    private final String name;
    public GroupConversation(long id, long creatorId, String name, List<Member> participants) {
        super(id, creatorId, participants);
        if (name == null || name.isBlank() || name.length() > 100) throw new IllegalArgumentException("Group name required");
        if (participants.size()<2 || participants.size()>20) throw new IllegalArgumentException("Groups require 2–20 participants");
        this.name = name.trim();
    }
    @Override public String getType() { return "GROUP"; }
    @Override public String getDisplayName(long viewerId) { return name; }
}
