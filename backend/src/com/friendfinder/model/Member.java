package com.friendfinder.model;

public class Member extends User {
    public Member(String fullName, String username, String email) { this(0, fullName, username, email); }
    public Member(long id, String fullName, String username, String email) { super(id, fullName, username, email); }
    @Override public String getRole() { return "MEMBER"; }
    @Override public String getDashboard() { return "/dashboard.html"; }
    public boolean canCreateGroup() { return true; }
}
