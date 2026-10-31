package com.friendfinder.model;

public final class CommunityMembership {
    private final Member member; private final Community community;
    public CommunityMembership(Member member, Community community) { this.member = java.util.Objects.requireNonNull(member); this.community = java.util.Objects.requireNonNull(community); }
    public Member getMember() { return member; } public Community getCommunity() { return community; }
}
