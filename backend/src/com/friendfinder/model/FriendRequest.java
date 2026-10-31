package com.friendfinder.model;

public final class FriendRequest {
    private final long id; private final long senderId; private final long receiverId; private final String status;
    public FriendRequest(long id, long senderId, long receiverId, String status) {
        if (senderId == receiverId || !java.util.List.of("PENDING","ACCEPTED","REJECTED").contains(status)) throw new IllegalArgumentException("Invalid friend request");
        this.id = id; this.senderId = senderId; this.receiverId = receiverId; this.status = status;
    }
    public long getId() { return id; } public long getSenderId() { return senderId; } public long getReceiverId() { return receiverId; } public String getStatus() { return status; }
}
