package com.friendfinder.service;

import java.sql.*;
import java.util.*;
import com.friendfinder.dao.*;
import com.friendfinder.model.*;
import com.friendfinder.util.*;

public final class MessageService {
    private final MessageDAO messages = new MessageDAO(); private final FriendDAO friends = new FriendDAO();
    public long privateConversation(User user,long target) throws SQLException {
        AuthService.member(user); ApiException.require(target!=user.getId(),400,"Choose another member");
        return Jdbc.transaction(connection -> {
            FriendService.activeMember(connection,target);
            ApiException.require(friends.areFriends(connection,user.getId(),target),403,"Private conversations require an accepted friendship");
            var existing = messages.privatePair(connection,user.getId(),target); if (existing!=null) return Jdbc.number(existing,"id");
            long id = messages.create(connection,"PRIVATE","",user.getId(),Math.min(user.getId(),target),Math.max(user.getId(),target));
            messages.add(connection,id,user.getId()); messages.add(connection,id,target); return id;
        });
    }
    public long group(User user,String name,List<?> supplied) throws SQLException {
        AuthService.member(user); ApiException.require(((Member)user).canCreateGroup(),403,"Group creation unavailable");
        ApiException.require(name!=null && !name.isBlank() && name.length()<=100,400,"Group name required (maximum 100 characters)");
        Set<Long> participants = new LinkedHashSet<>();
        for (Object raw : supplied) { try { participants.add(Long.parseLong(raw.toString())); } catch (NumberFormatException e) { throw new ApiException(400,"Invalid participant"); } }
        ApiException.require(!participants.contains(user.getId()) && participants.size()>=1 && participants.size()<=19,400,"Choose 1–19 friends; you are added automatically");
        return Jdbc.transaction(connection -> {
            for (long id : participants) { FriendService.activeMember(connection,id); ApiException.require(friends.areFriends(connection,user.getId(),id),403,"All initial participants must be your friends"); }
            long id = messages.create(connection,"GROUP",name,user.getId(),null,null); messages.add(connection,id,user.getId());
            for (long participant : participants) messages.add(connection,id,participant);
            new ActivityDAO().log(connection,new ActivityLog(user.getId(),"GROUP_CREATE",null)); return id;
        });
    }
    public List<Map<String,Object>> list(User user) throws SQLException {
        AuthService.member(user); List<Map<String,Object>> result = messages.list(user.getId());
        try (Connection connection = DatabaseConnection.open()) {
            for (var row : result) {
                // Runtime polymorphism: the same call yields a peer name or group title.
                Conversation conversation = messages.model(connection,row); row.put("display_name",conversation.getDisplayName(user.getId()));
            }
        }
        return result;
    }
    public Map<String,Object> load(User user,long conversationId) throws SQLException {
        AuthService.member(user);
        try (Connection connection = DatabaseConnection.open()) {
            requireAccess(connection,user,conversationId);
            var participants = messages.participants(connection,conversationId); for (var participant : participants) participant.remove("email");
            return Map.of("messages",messages.messages(connection,conversationId),"participants",participants);
        }
    }
    public long send(User user,long conversationId,String body) throws SQLException {
        AuthService.member(user); Message message = new Message(conversationId,user.getId(),body);
        return Jdbc.transaction(connection -> {
            requireAccess(connection,user,conversationId);
            for (var participant : messages.participants(connection,conversationId)) ApiException.require(!Jdbc.bool(participant,"suspended"),409,"A participant is suspended; this conversation is paused");
            var conversation = Jdbc.first(Jdbc.query(connection,"SELECT * FROM conversations WHERE id=?",conversationId));
            if (conversation.get("type").equals("PRIVATE")) ApiException.require(friends.areFriends(connection,Jdbc.number(conversation,"private_low"),Jdbc.number(conversation,"private_high")),403,"Renew your friendship before sending another message");
            long id = messages.send(connection,message); new ActivityDAO().log(connection,new ActivityLog(user.getId(),"MESSAGE_SEND",null)); return id;
        });
    }
    private void requireAccess(Connection connection,User user,long conversationId) throws SQLException { ApiException.require(messages.member(connection,user.getId(),conversationId),403,"You do not belong to this conversation"); }
}
