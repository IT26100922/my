package com.friendfinder.service;

import java.sql.*;
import java.util.*;
import com.friendfinder.dao.*;
import com.friendfinder.model.*;
import com.friendfinder.util.*;

public final class ProfileService {
    private final UserDAO users = new UserDAO();
    public void edit(User user,Map<String,Object> data) throws SQLException {
        AuthService.member(user); String name = Input.text(data,"fullName",1,100), bio = Input.text(data,"bio",0,500);
        user.setFullName(name);
        Jdbc.transaction(connection -> { users.updateProfile(connection,user.getId(),name,bio); new ActivityDAO().log(connection,new ActivityLog(user.getId(),"PROFILE_UPDATE",null)); return null; });
    }
    public void questionnaire(User user,Map<String,Object> data) throws SQLException {
        AuthService.member(user);
        Map<Long,Integer> ratings = new LinkedHashMap<>(); List<?> supplied = Input.list(data,"interests");
        for (Object value : supplied) {
            ApiException.require(value instanceof Map<?,?>,400,"Invalid interest rating");
            @SuppressWarnings("unchecked") Map<String,Object> item = (Map<String,Object>)value;
            long id = Input.id(item,"id"), rating = Input.id(item,"rating");
            ApiException.require(rating <= 5 && !ratings.containsKey(id),400,"Choose each interest once with a rating from 1 to 5"); ratings.put(id,(int)rating);
        }
        Set<Long> expected = new HashSet<>(); for (var interest : users.interests()) expected.add(Jdbc.number(interest,"id"));
        ApiException.require(expected.equals(ratings.keySet()),400,"Rate every IT interest");
        Set<String> goals = new LinkedHashSet<>(); for (Object goal : Input.list(data,"goals")) { ApiException.require(goal instanceof String,400,"Invalid goal"); goals.add((String)goal); }
        QuestionnaireResponse response = new QuestionnaireResponse(ratings,goals,Input.choice(data,"experience","Beginner","Intermediate","Advanced"),Input.choice(data,"collaboration","Mostly Discussion","Mostly Projects","Both"),Input.choice(data,"activity","Casual","Few Times Per Week","Daily"));
        Jdbc.transaction(connection -> { users.saveQuestionnaire(connection,user.getId(),response); new ActivityDAO().log(connection,new ActivityLog(user.getId(),"QUESTIONNAIRE_SAVE",null)); return null; });
    }
}
