package com.friendfinder.dao;

import java.sql.*;
import java.util.*;
import com.friendfinder.model.*;

public final class CommunityDAO {
    public List<Map<String,Object>> list(long userId,boolean includeInactive) throws SQLException {
        return Jdbc.query("SELECT c.*,u.full_name leader_name,(SELECT COUNT(*) FROM community_members m WHERE m.community_id=c.id) member_count,EXISTS(SELECT 1 FROM community_members m WHERE m.community_id=c.id AND m.user_id=?) joined FROM communities c LEFT JOIN users u ON u.id=c.leader_id WHERE c.active=TRUE OR ?=TRUE ORDER BY c.id",userId,includeInactive);
    }
    public Map<String,Object> find(Connection connection,long id) throws SQLException { return Jdbc.first(Jdbc.query(connection,"SELECT * FROM communities WHERE id=? FOR UPDATE",id)); }
    public Community model(Map<String,Object> row) { return new Community(Jdbc.number(row,"id"),(String)row.get("name"),Jdbc.number(row,"leader_id")); }
    public boolean member(Connection connection,long userId,long communityId) throws SQLException { return !Jdbc.query(connection,"SELECT user_id FROM community_members WHERE user_id=? AND community_id=?",userId,communityId).isEmpty(); }
    public void join(Connection connection,long userId,long communityId) throws SQLException { Jdbc.update(connection,"INSERT INTO community_members(user_id,community_id) VALUES(?,?)",userId,communityId); }
    public void leave(Connection connection,long userId,long communityId) throws SQLException { Jdbc.update(connection,"DELETE FROM community_members WHERE user_id=? AND community_id=?",userId,communityId); }
    public List<Map<String,Object>> members(long communityId) throws SQLException { return Jdbc.query("SELECT u.id,u.full_name,u.username,u.role FROM community_members m JOIN users u ON u.id=m.user_id WHERE m.community_id=? AND u.suspended=FALSE ORDER BY u.full_name",communityId); }
    public long create(Connection connection,String name,String description,Long interestId) throws SQLException { return Jdbc.insert(connection,"INSERT INTO communities(name,description,interest_id) VALUES(?,?,?)",name,description,interestId); }
    public void edit(Connection connection,long id,String name,String description,boolean active,Long interestId) throws SQLException { Jdbc.update(connection,"UPDATE communities SET name=?,description=?,active=?,interest_id=? WHERE id=?",name,description,active,interestId,id); }
    public void assign(Connection connection,long id,long leaderId) throws SQLException {
        Jdbc.update(connection,"UPDATE users SET role='COMMUNITY_LEADER' WHERE id=?",leaderId);
        Jdbc.update(connection,"UPDATE communities SET leader_id=? WHERE id=?",leaderId,id);
        Jdbc.update(connection,"INSERT INTO community_members(user_id,community_id) VALUES(?,?) ON CONFLICT DO NOTHING",leaderId,id);
    }
}
