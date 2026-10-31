package com.friendfinder.service;

import java.sql.*;
import java.util.*;
import com.friendfinder.dao.*;
import com.friendfinder.model.*;
import com.friendfinder.util.*;

public final class MatchingService {
    public static final int INTEREST_WEIGHT=50, EXPERIENCE_WEIGHT=15, GOAL_WEIGHT=20, COLLABORATION_WEIGHT=10, ACTIVITY_WEIGHT=5;
    private final UserDAO users = new UserDAO();
    public int calculateMatch(User first,User second) {
        QuestionnaireResponse a = first.getQuestionnaire(), b = second.getQuestionnaire();
        if (a == null || b == null) return 0;
        // Weighted Jaccard on enthusiasm (rating minus one): shared indifference earns no points.
        double overlap=0, total=0;
        Set<Long> interests = new HashSet<>(a.getRatings().keySet()); interests.addAll(b.getRatings().keySet());
        for (long id : interests) { int x=a.getRatings().getOrDefault(id,1)-1, y=b.getRatings().getOrDefault(id,1)-1; overlap+=Math.min(x,y); total+=Math.max(x,y); }
        double score = total == 0 ? 0 : INTEREST_WEIGHT * overlap / total;
        List<String> levels = List.of("Beginner","Intermediate","Advanced");
        score += EXPERIENCE_WEIGHT * (1 - Math.abs(levels.indexOf(a.getExperience()) - levels.indexOf(b.getExperience())) / 2.0);
        Set<String> shared = new HashSet<>(a.getGoals()); shared.retainAll(b.getGoals());
        Set<String> all = new HashSet<>(a.getGoals()); all.addAll(b.getGoals()); score += GOAL_WEIGHT * (double)shared.size()/all.size();
        score += COLLABORATION_WEIGHT * (a.getCollaboration().equals(b.getCollaboration()) ? 1 : a.getCollaboration().equals("Both") || b.getCollaboration().equals("Both") ? 0.75 : 0);
        List<String> activity = List.of("Casual","Few Times Per Week","Daily");
        score += ACTIVITY_WEIGHT * (1 - Math.abs(activity.indexOf(a.getActivity()) - activity.indexOf(b.getActivity()))/2.0);
        return Math.max(0,Math.min(100,(int)Math.round(score)));
    }
    public List<Map<String,Object>> recommendations(User current) throws SQLException {
        AuthService.member(current); current.setQuestionnaire(requireQuestionnaire(current.getId()));
        List<Map<String,Object>> result = new ArrayList<>(); AuthService profiles = new AuthService();
        for (var row : users.candidates(current.getId())) {
            User candidate = users.model(row); candidate.setQuestionnaire(users.questionnaire(candidate.getId()));
            var profile = profiles.profile(candidate.getId()); profile.put("match",calculateMatch(current,candidate)); result.add(profile);
        }
        result.sort(Comparator.comparingInt(row -> -((Number)row.get("match")).intValue())); return result;
    }
    public List<Map<String,Object>> communities(User user) throws SQLException {
        AuthService.member(user); QuestionnaireResponse response = requireQuestionnaire(user.getId());
        List<Map<String,Object>> rows = new CommunityDAO().list(user.getId(),false);
        for (var row : rows) row.put("match",response.getRatings().getOrDefault(Jdbc.number(row,"interest_id"),1) * 20);
        rows.sort(Comparator.comparingInt(row -> -((Number)row.get("match")).intValue())); return rows;
    }
    private QuestionnaireResponse requireQuestionnaire(long id) throws SQLException {
        var response = users.questionnaire(id); ApiException.require(response != null,409,"Complete your questionnaire to see recommendations"); return response;
    }
}
