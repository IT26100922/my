package com.friendfinder.model;

public final class ActivityLog {
    private final long userId; private final String event; private final Long communityId;
    public ActivityLog(long userId, String event, Long communityId) { this.userId = userId; this.event = event; this.communityId = communityId; }
    public long getUserId() { return userId; } public String getEvent() { return event; } public Long getCommunityId() { return communityId; }
}
