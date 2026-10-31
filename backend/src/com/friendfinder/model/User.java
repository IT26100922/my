package com.friendfinder.model;

import java.util.*;

public abstract class User {
    private final long id;
    private String fullName;
    private String username;
    private String email;
    private QuestionnaireResponse questionnaire;
    protected User(long id, String fullName, String username, String email) {
        this.id = id; setFullName(fullName); setUsername(username); setEmail(email);
    }
    public long getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String value) { if (value == null || value.isBlank() || value.length() > 100) throw new IllegalArgumentException("Invalid full name"); fullName = value.trim(); }
    public String getUsername() { return username; }
    public void setUsername(String value) { if (value == null || !value.matches("[A-Za-z0-9_]{3,30}")) throw new IllegalArgumentException("Username must be 3–30 letters, digits or underscores"); username = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { if (value == null || value.length() > 160 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Invalid email address"); email = value.toLowerCase(Locale.ROOT); }
    public QuestionnaireResponse getQuestionnaire() { return questionnaire; }
    public void setQuestionnaire(QuestionnaireResponse value) { questionnaire = Objects.requireNonNull(value); }
    public abstract String getRole();
    public abstract String getDashboard();
}
