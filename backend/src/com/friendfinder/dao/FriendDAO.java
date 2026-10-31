package com.friendfinder.dao;

import java.sql.*;
import java.util.*;
import com.friendfinder.model.FriendRequest;

public final class FriendDAO {
    public List<Map<String,Object>> list(long userId) throws SQLException {
        return Jdbc.query("SELECT f.id request_id,u.id,u.full_name,u.username,u.bio FROM friend_requests f JOIN users u ON u.id=CASE WHEN f.sender_id=? THEN f.receiver_id ELSE f.sender_id END WHERE (f.sender_id=? OR f.receiver_id=?) AND f.status='ACCEPTED' AND u.suspended=FALSE ORDER BY u.full_name",userId,userId,userId);
    }
    public List<Map<String,Object>> requests(long userId) throws SQLException { return Jdbc.query("SELECT f.*,s.full_name sender_name,r.full_name receiver_name FROM friend_requests f JOIN users s ON s.id=f.sender_id JOIN users r ON r.id=f.receiver_id WHERE (f.sender_id=? OR f.receiver_id=?) AND f.status='PENDING' ORDER BY f.id DESC",userId,userId); }
    public FriendRequest find(Connection connection,long id) throws SQLException {
        var row = Jdbc.first(Jdbc.query(connection,"SELECT * FROM friend_requests WHERE id=? FOR UPDATE",id));
        return row == null ? null : new FriendRequest(Jdbc.number(row,"id"),Jdbc.number(row,"sender_id"),Jdbc.number(row,"receiver_id"),(String)row.get("status"));
    }
    public Map<String,Object> pair(Connection connection,long first,long second) throws SQLException { return Jdbc.first(Jdbc.query(connection,"SELECT * FROM friend_requests WHERE pair_low=LEAST(?,?) AND pair_high=GREATEST(?,?) FOR UPDATE",first,second,first,second)); }
    public boolean areFriends(Connection connection,long first,long second) throws SQLException { var row = pair(connection,first,second); return row != null && row.get("status").equals("ACCEPTED"); }
    public long send(Connection connection,long first,long second) throws SQLException { return Jdbc.insert(connection,"INSERT INTO friend_requests(sender_id,receiver_id) VALUES(?,?)",first,second); }
    public void respond(Connection connection,long id,String status) throws SQLException { Jdbc.update(connection,"UPDATE friend_requests SET status=?::friend_request_status,responded_at=CURRENT_TIMESTAMP WHERE id=?",status,id); }
    public void remove(Connection connection,long first,long second) throws SQLException { Jdbc.update(connection,"DELETE FROM friend_requests WHERE pair_low=LEAST(?,?) AND pair_high=GREATEST(?,?) AND status='ACCEPTED'",first,second,first,second); }
}
