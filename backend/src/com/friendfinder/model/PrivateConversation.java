package com.friendfinder.model;

import java.util.*;

public final class PrivateConversation extends Conversation {
    public PrivateConversation(long id, long creatorId, List<Member> participants) {
        super(id, creatorId, participants);
        if (participants.size() != 2 || participants.get(0).getId() == participants.get(1).getId()) throw new IllegalArgumentException("Private chat needs two distinct members");
    }
    @Override public String getType() { return "PRIVATE"; }
    @Override public String getDisplayName(long viewerId) { return getParticipants().stream().filter(user -> user.getId() != viewerId).findFirst().orElseThrow().getFullName(); }
}
