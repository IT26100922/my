package com.friendfinder.dao;

import java.sql.*;
import java.util.*;
import com.friendfinder.model.*;

public final class PostDAO {
    public List<Map<String,Object>> feed(long communityId) throws SQLException {
        return Jdbc.query("SELECT p.id,p.community_id,p.author_id,p.title,p.body,p.status,p.created_at,u.full_name author_name,c.name community_name FROM posts p JOIN users u ON u.id=p.author_id JOIN communities c ON c.id=p.community_id WHERE p.status='APPROVED' AND c.active=TRUE AND (?=0 OR p.community_id=?) ORDER BY p.id DESC LIMIT 100",communityId,communityId);
    }
    public List<Map<String,Object>> pending(long leaderId) throws SQLException { return Jdbc.query("SELECT p.*,u.full_name author_name,c.name community_name FROM posts p JOIN communities c ON c.id=p.community_id JOIN users u ON u.id=p.author_id WHERE c.leader_id=? AND c.active=TRUE AND p.status='PENDING' ORDER BY p.id",leaderId); }
    public Post find(Connection connection,long id) throws SQLException {
        var row = Jdbc.first(Jdbc.query(connection,"SELECT * FROM posts WHERE id=? FOR UPDATE",id));
        return row == null ? null : new Post(Jdbc.number(row,"id"),Jdbc.number(row,"author_id"),Jdbc.number(row,"community_id"),(String)row.get("title"),(String)row.get("body"),(String)row.get("status"));
    }
    public long create(Connection connection,Post post) throws SQLException { return Jdbc.insert(connection,"INSERT INTO posts(community_id,author_id,title,body) VALUES(?,?,?,?)",post.getCommunityId(),post.getAuthorId(),post.getTitle(),post.getBody()); }
    public void moderate(Connection connection,Post post) throws SQLException { Jdbc.update(connection,"UPDATE posts SET status=?::post_status,moderated_by=?,moderated_at=CURRENT_TIMESTAMP WHERE id=?",post.getStatus(),post.getModeratorId(),post.getId()); }
    public List<Map<String,Object>> comments(long postId) throws SQLException { return Jdbc.query("SELECT c.id,c.body,c.created_at,u.full_name author_name FROM comments c JOIN users u ON u.id=c.author_id JOIN posts p ON p.id=c.post_id JOIN communities co ON co.id=p.community_id WHERE c.post_id=? AND p.status='APPROVED' AND co.active=TRUE ORDER BY c.id",postId); }
    public long comment(Connection connection,Comment comment) throws SQLException { return Jdbc.insert(connection,"INSERT INTO comments(post_id,author_id,body) VALUES(?,?,?)",comment.getPostId(),comment.getAuthorId(),comment.getBody()); }
}
