package com.friendfinder.dao;

import java.sql.*;
import java.util.*;
import com.friendfinder.model.*;

public final class UserDAO {
    public static final String PUBLIC_COLUMNS = "id,full_name,username,email,role,bio,suspended,created_at";
    public Map<String,Object> find(long id) throws SQLException { return Jdbc.first(Jdbc.query("SELECT " + PUBLIC_COLUMNS + " FROM users WHERE id=?", id)); }
    public Map<String,Object> credentials(String login) throws SQLException { return Jdbc.first(Jdbc.query("SELECT * FROM users WHERE username=? OR email=?", login, login)); }
    public long create(Connection connection, Member member, String hash) throws SQLException { return Jdbc.insert(connection, "INSERT INTO users(full_name,username,email,password_hash) VALUES(?,?,?,?)", member.getFullName(), member.getUsername(), member.getEmail(), hash); }
    public List<Map<String,Object>> searchUsers(String keyword) throws SQLException {
        return Jdbc.query("SELECT " + PUBLIC_COLUMNS + " FROM users WHERE full_name ILIKE ? OR username ILIKE ? ORDER BY full_name LIMIT 100", "%" + keyword + "%", "%" + keyword + "%");
    }
    /** Natural overload: optional IT interest filter for the same search operation. */
    public List<Map<String,Object>> searchUsers(String keyword, String interest) throws SQLException {
        return Jdbc.query("SELECT u.id,u.full_name,u.username,u.email,u.role,u.bio,u.suspended,u.created_at FROM users u JOIN user_interests ui ON ui.user_id=u.id JOIN interests i ON i.id=ui.interest_id WHERE (u.full_name ILIKE ? OR u.username ILIKE ?) AND i.name=? AND ui.rating>=4 ORDER BY u.full_name LIMIT 100", "%" + keyword + "%", "%" + keyword + "%", interest);
    }
    public User model(Map<String,Object> row) {
        long id = Jdbc.number(row,"id"); String name = (String)row.get("full_name"), username = (String)row.get("username"), email = (String)row.get("email");
        return switch ((String)row.get("role")) { case "ADMIN" -> new Admin(id,name,username,email); case "COMMUNITY_LEADER" -> new CommunityLeader(id,name,username,email); default -> new Member(id,name,username,email); };
    }
    public List<Map<String,Object>> interests() throws SQLException { return Jdbc.query("SELECT * FROM interests ORDER BY id"); }
    public QuestionnaireResponse questionnaire(long id) throws SQLException {
        Map<String,Object> row = Jdbc.first(Jdbc.query("SELECT * FROM questionnaire_responses WHERE user_id=?", id));
        if (row == null) return null;
        Map<Long,Integer> ratings = new LinkedHashMap<>(); Set<String> goals = new LinkedHashSet<>();
        for (Map<String,Object> item : Jdbc.query("SELECT interest_id,rating FROM user_interests WHERE user_id=?", id)) ratings.put(Jdbc.number(item,"interest_id"), (int)Jdbc.number(item,"rating"));
        for (Map<String,Object> item : Jdbc.query("SELECT goal FROM user_goals WHERE user_id=?", id)) goals.add((String)item.get("goal"));
        return new QuestionnaireResponse(ratings,goals,(String)row.get("experience"),(String)row.get("collaboration"),(String)row.get("activity"));
    }
    public void saveQuestionnaire(Connection connection, long id, QuestionnaireResponse response) throws SQLException {
        Jdbc.update(connection,"INSERT INTO questionnaire_responses(user_id,experience,collaboration,activity) VALUES(?,?::experience_level,?::collaboration_type,?::activity_level) ON CONFLICT (user_id) DO UPDATE SET experience=EXCLUDED.experience,collaboration=EXCLUDED.collaboration,activity=EXCLUDED.activity", id,response.getExperience(),response.getCollaboration(),response.getActivity());
        Jdbc.update(connection,"DELETE FROM user_interests WHERE user_id=?",id); Jdbc.update(connection,"DELETE FROM user_goals WHERE user_id=?",id);
        for (var entry : response.getRatings().entrySet()) Jdbc.update(connection,"INSERT INTO user_interests(user_id,interest_id,rating) VALUES(?,?,?)",id,entry.getKey(),entry.getValue());
        for (String goal : response.getGoals()) Jdbc.update(connection,"INSERT INTO user_goals(user_id,goal) VALUES(?,?::user_goal)",id,goal);
    }
    public void updateProfile(Connection connection,long id,String name,String bio) throws SQLException { Jdbc.update(connection,"UPDATE users SET full_name=?,bio=? WHERE id=?",name,bio,id); }
    public void suspend(Connection connection,long id,boolean suspended) throws SQLException { Jdbc.update(connection,"UPDATE users SET suspended=? WHERE id=?",suspended,id); }
    public List<Map<String,Object>> candidates(long id) throws SQLException {
        return Jdbc.query("SELECT u.id,u.full_name,u.username,u.email,u.role,u.bio,u.suspended,u.created_at FROM users u JOIN questionnaire_responses q ON q.user_id=u.id WHERE u.id<>? AND u.suspended=FALSE AND u.role<>'ADMIN' AND NOT EXISTS(SELECT 1 FROM friend_requests f WHERE f.pair_low=LEAST(?,u.id) AND f.pair_high=GREATEST(?,u.id) AND f.status IN ('PENDING','ACCEPTED')) ORDER BY u.id LIMIT 200",id,id,id);
    }
}
