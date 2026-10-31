package com.friendfinder.service;

import java.sql.*;
import com.friendfinder.dao.*;
import com.friendfinder.model.*;
import com.friendfinder.util.*;

public final class CommunityService {
    private final CommunityDAO communities = new CommunityDAO();
    public void membership(User user,long id,boolean join) throws SQLException {
        AuthService.member(user);
        Jdbc.transaction(connection -> {
            var row = requireActive(connection,id); boolean existing = communities.member(connection,user.getId(),id);
            ApiException.require(join != existing,409,join?"Already a community member":"You are not a member");
            if (join) {
                CommunityMembership membership = new CommunityMembership((Member)user,communities.model(row));
                communities.join(connection,membership.getMember().getId(),membership.getCommunity().getId());
            }
            else { ApiException.require(Jdbc.number(row,"leader_id")!=user.getId(),409,"An assigned leader must remain a member; ask an administrator to reassign the community"); communities.leave(connection,user.getId(),id); }
            new ActivityDAO().log(connection,new ActivityLog(user.getId(),join?"COMMUNITY_JOIN":"COMMUNITY_LEAVE",id)); return null;
        });
    }
    public java.util.Map<String,Object> requireActive(Connection connection,long id) throws SQLException {
        var row = communities.find(connection,id); ApiException.require(row!=null,404,"Community not found"); ApiException.require(Jdbc.bool(row,"active"),409,"This community is archived"); return row;
    }
    public void requireMember(Connection connection,long userId,long communityId) throws SQLException { requireActive(connection,communityId); ApiException.require(communities.member(connection,userId,communityId),403,"Join this community first"); }
}
