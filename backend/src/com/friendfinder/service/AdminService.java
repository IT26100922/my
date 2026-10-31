package com.friendfinder.service;

import java.sql.*;
import java.util.*;
import com.friendfinder.dao.*;
import com.friendfinder.model.*;
import com.friendfinder.util.*;

public final class AdminService {
    private final CommunityDAO communities = new CommunityDAO();
    public void suspend(User admin,long id,boolean suspended) throws SQLException {
        AuthService.admin(admin); ApiException.require(id!=admin.getId(),400,"You cannot suspend your own account");
        Jdbc.transaction(connection -> {
            var target = Jdbc.first(Jdbc.query(connection,"SELECT id,role,suspended FROM users WHERE id=? FOR UPDATE",id));
            ApiException.require(target!=null,404,"User not found"); ApiException.require(!target.get("role").equals("ADMIN"),403,"Demo administrators cannot suspend other administrators");
            ApiException.require(Jdbc.bool(target,"suspended")!=suspended,409,"Account already has this status");
            new UserDAO().suspend(connection,id,suspended); new ActivityDAO().log(connection,new ActivityLog(admin.getId(),suspended?"ACCOUNT_DEACTIVATE":"ACCOUNT_REACTIVATE",null)); return null;
        });
    }
    public long community(User admin,Map<String,Object> data,boolean create) throws SQLException {
        AuthService.admin(admin); String name = Input.text(data,"name",1,100), description = Input.text(data,"description",1,1000);
        Long interest = data.get("interestId")==null ? null : Input.id(data,"interestId");
        return Jdbc.transaction(connection -> {
            if (interest!=null) ApiException.require(!Jdbc.query(connection,"SELECT id FROM interests WHERE id=?",interest).isEmpty(),400,"Invalid interest");
            long id;
            if (create) id=communities.create(connection,name,description,interest);
            else {
                id=Input.id(data,"id"); ApiException.require(communities.find(connection,id)!=null,404,"Community not found");
                ApiException.require(data.get("active") instanceof Boolean,400,"active must be true or false");
                communities.edit(connection,id,name,description,(Boolean)data.get("active"),interest);
            }
            new ActivityDAO().log(connection,new ActivityLog(admin.getId(),create?"COMMUNITY_CREATE":"COMMUNITY_UPDATE",id)); return id;
        });
    }
    public void assign(User admin,long communityId,long leaderId) throws SQLException {
        AuthService.admin(admin);
        Jdbc.transaction(connection -> {
            ApiException.require(communities.find(connection,communityId)!=null,404,"Community not found"); FriendService.activeMember(connection,leaderId);
            communities.assign(connection,communityId,leaderId); new ActivityDAO().log(connection,new ActivityLog(admin.getId(),"LEADER_ASSIGN",communityId)); return null;
        });
    }
}
