package com.friendfinder.model;

public class Admin extends User {
    public Admin(long id, String fullName, String username, String email) { super(id, fullName, username, email); }
    @Override public String getRole() { return "ADMIN"; }
    @Override public String getDashboard() { return "/admin/dashboard.html"; }
}
