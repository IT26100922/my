package com.friendfinder.dao;

import java.sql.*;
import java.util.*;
import com.friendfinder.model.ActivityLog;

public final class ActivityDAO {
    public void log(Connection connection, ActivityLog log) throws SQLException { Jdbc.update(connection,"INSERT INTO activity_logs(user_id,event,community_id) VALUES(?,?,?)",log.getUserId(),log.getEvent(),log.getCommunityId()); }
    public List<Map<String,Object>> recent() throws SQLException { return Jdbc.query("SELECT a.id,a.event,a.created_at,u.full_name,c.name community_name FROM activity_logs a JOIN users u ON u.id=a.user_id LEFT JOIN communities c ON c.id=a.community_id ORDER BY a.id DESC LIMIT 200"); }
}
