package com.friendfinder.model;

public final class Message {
    private final long conversationId; private final long senderId; private final String body;
    public Message(long conversationId, long senderId, String body) {
        if (body == null || body.isBlank() || body.length() > 4000) throw new IllegalArgumentException("Message must contain 1–4000 characters");
        this.conversationId = conversationId; this.senderId = senderId; this.body = body.trim();
    }
    public long getConversationId() { return conversationId; } public long getSenderId() { return senderId; } public String getBody() { return body; }
}
