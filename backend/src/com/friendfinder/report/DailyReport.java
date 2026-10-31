package com.friendfinder.report;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import com.friendfinder.dao.Jdbc;
import com.friendfinder.interfacepkg.Reportable;

public final class DailyReport implements Reportable {
    private final LocalDate date;
    public DailyReport() { this(LocalDate.now(java.time.ZoneOffset.UTC)); }
    public DailyReport(LocalDate date) { this.date = java.util.Objects.requireNonNull(date); }
    @Override public Map<String,Object> generateReport() throws SQLException {
        String start=date + " 00:00:00", end=date.plusDays(1) + " 00:00:00";
        Map<String,Object> result = new LinkedHashMap<>(); result.put("date",date.toString());
        Map<String,Long> counts = new HashMap<>();
        for (var row : Jdbc.query("SELECT event,COUNT(*) total FROM activity_logs WHERE created_at>=? AND created_at<? GROUP BY event",start,end)) counts.put((String)row.get("event"),Jdbc.number(row,"total"));
        Map<String,String> metrics = new LinkedHashMap<>();
        metrics.put("new_users","REGISTER"); metrics.put("deactivated_users","ACCOUNT_DEACTIVATE"); metrics.put("new_friendships","FRIEND_ACCEPT");
        metrics.put("community_joins","COMMUNITY_JOIN"); metrics.put("community_leaves","COMMUNITY_LEAVE"); metrics.put("posts_submitted","POST_SUBMIT");
        metrics.put("posts_approved","POST_APPROVE"); metrics.put("posts_rejected","POST_REJECT"); metrics.put("comments","COMMENT_CREATE"); metrics.put("messages_sent","MESSAGE_SEND");
        metrics.forEach((label,event) -> result.put(label,counts.getOrDefault(event,0L)));
        result.put("most_active_user",Jdbc.first(Jdbc.query("SELECT u.id,u.full_name,COUNT(*) events FROM activity_logs a JOIN users u ON u.id=a.user_id WHERE a.created_at>=? AND a.created_at<? GROUP BY u.id,u.full_name ORDER BY events DESC,u.id LIMIT 1",start,end)));
        result.put("most_active_community",Jdbc.first(Jdbc.query("SELECT c.id,c.name,COUNT(*) events FROM activity_logs a JOIN communities c ON c.id=a.community_id WHERE a.created_at>=? AND a.created_at<? GROUP BY c.id,c.name ORDER BY events DESC,c.id LIMIT 1",start,end)));
        return result;
    }
}
