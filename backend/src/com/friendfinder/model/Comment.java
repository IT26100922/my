package com.friendfinder.model;

public final class Comment {
    private final long postId; private final long authorId; private final String body;
    public Comment(long postId, long authorId, String body) { if (body == null || body.isBlank() || body.length() > 2000) throw new IllegalArgumentException("Comment must contain 1–2000 characters"); this.postId = postId; this.authorId = authorId; this.body = body.trim(); }
    public long getPostId() { return postId; } public long getAuthorId() { return authorId; } public String getBody() { return body; }
}
