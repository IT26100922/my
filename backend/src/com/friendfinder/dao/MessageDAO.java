package com.friendfinder.dao;

import java.sql.*;
import java.util.*;
import com.friendfinder.model.*;

public final class MessageDAO {
    public boolean member(Connection connection,long userId,long conversationId) throws SQLException { return !Jdbc.query(connection,"SELECT user_id FROM conversation_members WHERE user_id=? AND conversation_id=?",userId,conversationId).isEmpty(); }
    public List<Map<String,Object>> list(long userId) throws SQLException { return Jdbc.query("SELECT c.id,c.type,c.name,c.creator_id,c.created_at FROM conversations c JOIN conversation_members m ON m.conversation_id=c.id WHERE m.user_id=? ORDER BY c.id DESC",userId); }
    public Conversation model(Connection connection,Map<String,Object> row) throws SQLException {
        List<Member> members = new ArrayList<>();
        for (var user : participants(connection,Jdbc.number(row,"id"))) {
            User model = new UserDAO().model(user); if (model instanceof Member member) members.add(member);
        }
        return row.get("type").equals("PRIVATE") ? new PrivateConversation(Jdbc.number(row,"id"),Jdbc.number(row,"creator_id"),members) : new GroupConversation(Jdbc.number(row,"id"),Jdbc.number(row,"creator_id"),(String)row.get("name"),members);
    }
    public List<Map<String,Object>> participants(Connection connection,long conversationId) throws SQLException { return Jdbc.query(connection,"SELECT u.id,u.full_name,u.username,u.email,u.role,u.suspended FROM conversation_members m JOIN users u ON u.id=m.user_id WHERE m.conversation_id=? ORDER BY u.id",conversationId); }
    public long create(Connection connection,String type,String name,long creator,Long low,Long high) throws SQLException { return Jdbc.insert(connection,"INSERT INTO conversations(type,name,creator_id,private_low,private_high) VALUES(?::conversation_type,?,?,?,?)",type,name,creator,low,high); }
    public Map<String,Object> privatePair(Connection connection,long first,long second) throws SQLException { return Jdbc.first(Jdbc.query(connection,"SELECT id FROM conversations WHERE private_low=LEAST(?,?) AND private_high=GREATEST(?,?)",first,second,first,second)); }
    public void add(Connection connection,long conversationId,long userId) throws SQLException { Jdbc.update(connection,"INSERT INTO conversation_members(conversation_id,user_id) VALUES(?,?)",conversationId,userId); }
    public List<Map<String,Object>> messages(Connection connection,long conversationId) throws SQLException {
        return Jdbc.query(connection,"SELECT recent.* FROM (SELECT m.id,m.sender_id,m.body,m.created_at,u.full_name sender_name FROM messages m JOIN users u ON u.id=m.sender_id WHERE m.conversation_id=? ORDER BY m.id DESC LIMIT 200) recent ORDER BY recent.id",conversationId);
    }
    public long send(Connection connection,Message message) throws SQLException { return Jdbc.insert(connection,"INSERT INTO messages(conversation_id,sender_id,body) VALUES(?,?,?)",message.getConversationId(),message.getSenderId(),message.getBody()); }
}
