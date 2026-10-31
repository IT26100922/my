package com.friendfinder.model;

import java.util.*;

public final class QuestionnaireResponse {
    public static final List<String> GOALS = List.of("Friends", "Study Partners", "Project Partners", "Mentorship", "Professional Networking");
    private final Map<Long,Integer> ratings;
    private final Set<String> goals;
    private final String experience;
    private final String collaboration;
    private final String activity;
    public QuestionnaireResponse(Map<Long,Integer> ratings, Set<String> goals, String experience, String collaboration, String activity) {
        if (ratings.isEmpty() || ratings.values().stream().anyMatch(n -> n < 1 || n > 5)) throw new IllegalArgumentException("Ratings must be 1–5");
        if (goals.isEmpty() || !GOALS.containsAll(goals)) throw new IllegalArgumentException("Choose valid goals");
        if (!List.of("Beginner","Intermediate","Advanced").contains(experience) || !List.of("Mostly Discussion","Mostly Projects","Both").contains(collaboration) || !List.of("Casual","Few Times Per Week","Daily").contains(activity)) throw new IllegalArgumentException("Invalid questionnaire choice");
        this.ratings = Map.copyOf(ratings); this.goals = Set.copyOf(goals); this.experience = experience; this.collaboration = collaboration; this.activity = activity;
    }
    public Map<Long,Integer> getRatings() { return ratings; }
    public Set<String> getGoals() { return goals; }
    public String getExperience() { return experience; }
    public String getCollaboration() { return collaboration; }
    public String getActivity() { return activity; }
}
