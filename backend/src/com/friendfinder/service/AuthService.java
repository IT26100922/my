package com.friendfinder.service;

import java.sql.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import com.friendfinder.dao.*;
import com.friendfinder.model.*;
import com.friendfinder.util.*;

public final class AuthService {
    private record Session(long userId,long expiresAt) {}
    private final Map<String,Session> sessions = new ConcurrentHashMap<>();
    private final UserDAO users = new UserDAO(); private final ActivityDAO activity = new ActivityDAO();
    public Map<String,Object> register(Map<String,Object> data) throws SQLException {
        Member member = new Member(Input.text(data,"fullName",1,100),Input.text(data,"username",3,30),Input.text(data,"email",5,160));
        String password = Input.password(data,"password",8);
        ApiException.require(password.equals(Input.password(data,"confirmPassword",8)),400,"Passwords do not match");
        String hash = Passwords.hash(password);
        long id = Jdbc.transaction(connection -> {
            long created = users.create(connection,member,hash); activity.log(connection,new ActivityLog(created,"REGISTER",null)); return created;
        });
        return Map.of("user", profile(id), "token", token(id));
    }
    public Map<String,Object> login(Map<String,Object> data) throws SQLException {
        var row = users.credentials(Input.text(data,"login",1,160)); String password = Input.password(data,"password",1);
        ApiException.require(row != null && Passwords.verify(password,(String)row.get("password_hash")),401,"Invalid username/email or password");
        ApiException.require(!Jdbc.bool(row,"suspended"),403,"This account is suspended");
        long id = Jdbc.number(row,"id");
        Jdbc.transaction(connection -> { activity.log(connection,new ActivityLog(id,"LOGIN",null)); return null; });
        return Map.of("user",profile(id),"token",token(id));
    }
    private String token(long id) {
        long now = System.currentTimeMillis(); sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token,new Session(id,now + 8 * 60 * 60 * 1000L)); return token;
    }
    public User authenticate(String token) throws SQLException {
        Session session = token == null ? null : sessions.get(token);
        ApiException.require(session != null && session.expiresAt > System.currentTimeMillis(),401,"Please log in again");
        var row = users.find(session.userId);
        ApiException.require(row != null && !Jdbc.bool(row,"suspended"),403,"This account is suspended or unavailable");
        return users.model(row);
    }
    public void logout(String token) { if (token != null) sessions.remove(token); }
    public Map<String,Object> profile(long id) throws SQLException {
        var row = users.find(id); ApiException.require(row != null,404,"User not found");
        row.put("dashboard",users.model(row).getDashboard());
        row.remove("email"); // Email is never necessary in another member's profile.
        QuestionnaireResponse questionnaire = users.questionnaire(id);
        row.put("onboarded",questionnaire != null);
        row.put("interests",Jdbc.query("SELECT i.id,i.name,ui.rating FROM user_interests ui JOIN interests i ON i.id=ui.interest_id WHERE ui.user_id=? ORDER BY ui.rating DESC,i.id",id));
        row.put("goals", questionnaire == null ? List.of() : questionnaire.getGoals());
        row.put("experience",questionnaire == null ? "Not set" : questionnaire.getExperience());
        row.put("collaboration",questionnaire == null ? "Both" : questionnaire.getCollaboration());
        row.put("activity",questionnaire == null ? "Casual" : questionnaire.getActivity());
        row.put("communities",Jdbc.query("SELECT c.id,c.name FROM community_members m JOIN communities c ON c.id=m.community_id WHERE m.user_id=? AND c.active=TRUE",id));
        return row;
    }
    public static void member(User user) { ApiException.require(user instanceof Member,403,"This feature is for members and community leaders"); }
    public static void admin(User user) { ApiException.require(user instanceof Admin,403,"Administrator permission required"); }
}
