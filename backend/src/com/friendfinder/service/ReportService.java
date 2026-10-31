package com.friendfinder.service;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import com.friendfinder.dao.*;
import com.friendfinder.interfacepkg.Reportable;
import com.friendfinder.model.User;
import com.friendfinder.report.DailyReport;

public final class ReportService {
    public Map<String,Object> report(User user,LocalDate date) throws SQLException {
        AuthService.admin(user); Reportable report = new DailyReport(date); return report.generateReport();
    }
    public Map<String,Object> dashboard(User user) throws SQLException {
        AuthService.admin(user);
        var result = Jdbc.first(Jdbc.query("SELECT (SELECT COUNT(*) FROM users) total_users,(SELECT COUNT(*) FROM users WHERE suspended=FALSE) active_users,(SELECT COUNT(*) FROM communities) total_communities"));
        result.put("report",report(user,LocalDate.now(java.time.ZoneOffset.UTC))); return result;
    }
}
