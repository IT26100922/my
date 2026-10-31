package com.friendfinder.service;

import java.sql.*;
import com.friendfinder.dao.*;
import com.friendfinder.model.*;
import com.friendfinder.util.*;

public final class PostService {
    private final PostDAO posts = new PostDAO(); private final CommunityService communities = new CommunityService();
    public long submit(User user,long communityId,String title,String body) throws SQLException {
        AuthService.member(user); Post post = new Post(user.getId(),communityId,title,body);
        return Jdbc.transaction(connection -> { communities.requireMember(connection,user.getId(),communityId); long id = posts.create(connection,post); new ActivityDAO().log(connection,new ActivityLog(user.getId(),"POST_SUBMIT",communityId)); return id; });
    }
    public void moderate(User user,long postId,boolean approve) throws SQLException {
        ApiException.require(user instanceof CommunityLeader,403,"Community leader permission required");
        Jdbc.transaction(connection -> {
            Post post = posts.find(connection,postId); ApiException.require(post!=null,404,"Post not found");
            Community community = new CommunityDAO().model(communities.requireActive(connection,post.getCommunityId()));
            ApiException.require(((CommunityLeader)user).canModerate(community),403,"You are not the assigned leader of this community");
            ApiException.require(post.getAuthorId()!=user.getId(),403,"You cannot moderate your own post");
            ApiException.require(post.getStatus().equals("PENDING"),409,"Post has already been moderated");
            if (approve) post.approve(user.getId()); else post.reject(user.getId());
            posts.moderate(connection,post); new ActivityDAO().log(connection,new ActivityLog(user.getId(),approve?"POST_APPROVE":"POST_REJECT",post.getCommunityId())); return null;
        });
    }
    public long comment(User user,long postId,String body) throws SQLException {
        AuthService.member(user); Comment comment = new Comment(postId,user.getId(),body);
        return Jdbc.transaction(connection -> {
            Post post = posts.find(connection,postId); ApiException.require(post!=null,404,"Post not found");
            ApiException.require(post.getStatus().equals("APPROVED"),409,"Comments are allowed only on approved posts");
            communities.requireMember(connection,user.getId(),post.getCommunityId()); long id = posts.comment(connection,comment);
            new ActivityDAO().log(connection,new ActivityLog(user.getId(),"COMMENT_CREATE",post.getCommunityId())); return id;
        });
    }
}
