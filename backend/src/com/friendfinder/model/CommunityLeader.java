package com.friendfinder.model;

public class CommunityLeader extends Member {
    public CommunityLeader(long id, String fullName, String username, String email) { super(id, fullName, username, email); }
    @Override public String getRole() { return "COMMUNITY_LEADER"; }
    @Override public String getDashboard() { return "/leader/dashboard.html"; }
    public boolean canModerate(Community community) { return community.getLeaderId() == getId(); }
}
