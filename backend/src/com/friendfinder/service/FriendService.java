package com.friendfinder.service;

import java.sql.*;
import com.friendfinder.dao.*;
import com.friendfinder.model.*;
import com.friendfinder.util.*;

public final class FriendService {
    private final FriendDAO friends = new FriendDAO(); private final ActivityDAO activity = new ActivityDAO();
    public long send(User user,long target) throws SQLException {
        AuthService.member(user); ApiException.require(user.getId()!=target,400,"You cannot add yourself");
        return Jdbc.transaction(connection -> {
            activeMember(connection,target);
            ApiException.require(friends.pair(connection,user.getId(),target)==null,409,"A request or friendship already exists; rejected requests cannot be resent");
            long id = friends.send(connection,user.getId(),target); activity.log(connection,new ActivityLog(user.getId(),"FRIEND_REQUEST",null)); return id;
        });
    }
    public void respond(User user,long requestId,boolean accept) throws SQLException {
        AuthService.member(user);
        Jdbc.transaction(connection -> {
            FriendRequest request = friends.find(connection,requestId);
            ApiException.require(request!=null,404,"Friend request not found");
            ApiException.require(request.getReceiverId()==user.getId(),403,"Only the recipient can respond");
            ApiException.require(request.getStatus().equals("PENDING"),409,"This request has already been answered");
            if (accept) activeMember(connection,request.getSenderId());
            friends.respond(connection,requestId,accept?"ACCEPTED":"REJECTED");
            activity.log(connection,new ActivityLog(user.getId(),accept?"FRIEND_ACCEPT":"FRIEND_REJECT",null)); return null;
        });
    }
    public void remove(User user,long target) throws SQLException {
        AuthService.member(user);
        Jdbc.transaction(connection -> { ApiException.require(friends.areFriends(connection,user.getId(),target),404,"Friendship not found"); friends.remove(connection,user.getId(),target); activity.log(connection,new ActivityLog(user.getId(),"FRIEND_REMOVE",null)); return null; });
    }
    public static void activeMember(Connection connection,long id) throws SQLException {
        var target = Jdbc.first(Jdbc.query(connection,"SELECT id,role,suspended FROM users WHERE id=?",id));
        ApiException.require(target!=null,404,"User not found");
        ApiException.require(!Jdbc.bool(target,"suspended") && !target.get("role").equals("ADMIN"),403,"This user is unavailable");
    }
}
