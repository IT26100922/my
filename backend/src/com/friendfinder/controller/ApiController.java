package com.friendfinder.controller;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import com.friendfinder.dao.*;
import com.friendfinder.model.*;
import com.friendfinder.service.*;
import com.friendfinder.util.*;

/** HTTP translation only. Services implement business rules; DAOs implement SQL. */
public final class ApiController implements HttpHandler {
    private final AuthService auth = new AuthService();
    @Override public void handle(HttpExchange exchange) throws IOException {
        int status=200; Object response;
        try {
            String method=exchange.getRequestMethod(), path=exchange.getRequestURI().getPath();
            ApiException.require(method.equals("GET") || method.equals("POST"),405,"Use GET or POST");
            Map<String,Object> data = method.equals("POST") ? body(exchange) : query(exchange.getRequestURI().getRawQuery());
            String token = cookie(exchange,"ff_session");
            if (path.equals("/api/register") || path.equals("/api/login")) {
                requireMethod(method,"POST");
                Map<String,Object> result = path.endsWith("register") ? auth.register(data) : auth.login(data);
                setCookie(exchange,(String)result.get("token"),28800);
                response=Map.of("user",result.get("user")); status=path.endsWith("register")?201:200;
            } else if (path.equals("/api/health")) {
                requireMethod(method,"GET"); Jdbc.query("SELECT 1"); response=Map.of("status","ok");
            } else {
                User user=auth.authenticate(token);
                if (path.equals("/api/logout")) { requireMethod(method,"POST"); auth.logout(token); setCookie(exchange,"",0); response=Map.of("message","Logged out"); }
                else response=route(method,path,data,user);
            }
        } catch (ApiException e) { status=e.getStatus(); response=Map.of("error",e.getMessage()); }
        catch (IllegalArgumentException e) { status=400; response=Map.of("error",e.getMessage()==null?"Invalid request":e.getMessage()); }
        catch (SQLException e) {
            if (e.getSQLState()!=null && e.getSQLState().startsWith("23")) { status=409; response=Map.of("error","That username, email, membership, request or community already exists or conflicts with another record"); }
            else { status=503; response=Map.of("error","Database unavailable. Check the server configuration."); System.err.println("Database error: " + e.getSQLState() + " code " + e.getErrorCode()); }
        } catch (Exception e) { status=500; response=Map.of("error","Unexpected server error"); e.printStackTrace(System.err); }
        byte[] bytes=Json.stringify(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        exchange.sendResponseHeaders(status,bytes.length); try (OutputStream output=exchange.getResponseBody()) { output.write(bytes); }
    }
    private Object route(String method,String path,Map<String,Object> data,User user) throws SQLException {
        return method.equals("GET") ? get(path,data,user) : post(path,data,user);
    }
    private Object get(String path,Map<String,Object> data,User user) throws SQLException {
        return switch(path) {
            case "/api/users/me" -> auth.profile(user.getId());
            case "/api/users/profile" -> {
                AuthService.member(user); long id=Input.id(data,"id"); var profile=auth.profile(id);
                ApiException.require(!Jdbc.bool(profile,"suspended") && !profile.get("role").equals("ADMIN"),404,"Profile unavailable");
                var pair=Jdbc.first(Jdbc.query("SELECT id,sender_id,receiver_id,status FROM friend_requests WHERE pair_low=LEAST(?,?) AND pair_high=GREATEST(?,?)",user.getId(),id,user.getId(),id));
                profile.put("friend_request",pair); yield profile;
            }
            case "/api/interests" -> new UserDAO().interests();
            case "/api/matches" -> new MatchingService().recommendations(user);
            case "/api/communities/recommended" -> new MatchingService().communities(user);
            case "/api/friends" -> { AuthService.member(user); yield new FriendDAO().list(user.getId()); }
            case "/api/friends/requests" -> { AuthService.member(user); yield new FriendDAO().requests(user.getId()); }
            case "/api/communities" -> new CommunityDAO().list(user.getId(),false);
            case "/api/communities/members" -> { long id=Input.id(data,"communityId"); Jdbc.transaction(connection -> { new CommunityService().requireActive(connection,id); return null; }); yield new CommunityDAO().members(id); }
            case "/api/posts" -> new PostDAO().feed(data.containsKey("communityId")?Input.id(data,"communityId"):0);
            case "/api/posts/pending" -> { ApiException.require(user instanceof CommunityLeader,403,"Community leader permission required"); yield new PostDAO().pending(user.getId()); }
            case "/api/comments" -> new PostDAO().comments(Input.id(data,"postId"));
            case "/api/conversations" -> new MessageService().list(user);
            case "/api/messages" -> new MessageService().load(user,Input.id(data,"conversationId"));
            case "/api/admin/dashboard" -> new ReportService().dashboard(user);
            case "/api/admin/report" -> {
                AuthService.admin(user); LocalDate reportDate=LocalDate.now(java.time.ZoneOffset.UTC);
                if (data.containsKey("date")) {
                    try { reportDate=LocalDate.parse(Input.text(data,"date",10,10)); }
                    catch (java.time.format.DateTimeParseException e) { throw new ApiException(400,"Use a valid report date in YYYY-MM-DD format"); }
                }
                yield new ReportService().report(user,reportDate);
            }
            case "/api/admin/users" -> {
                AuthService.admin(user); String search=data.containsKey("search")?Input.text(data,"search",0,100):"";
                yield data.containsKey("interest") ? new UserDAO().searchUsers(search,Input.text(data,"interest",1,100)) : new UserDAO().searchUsers(search);
            }
            case "/api/admin/communities" -> { AuthService.admin(user); yield new CommunityDAO().list(user.getId(),true); }
            case "/api/admin/activity" -> { AuthService.admin(user); yield new ActivityDAO().recent(); }
            default -> throw new ApiException(404,"Endpoint not found");
        };
    }
    private Object post(String path,Map<String,Object> data,User user) throws SQLException {
        long id=0;
        switch(path) {
            case "/api/users/me" -> new ProfileService().edit(user,data);
            case "/api/questionnaire" -> new ProfileService().questionnaire(user,data);
            case "/api/friends/request" -> id=new FriendService().send(user,Input.id(data,"userId"));
            case "/api/friends/accept", "/api/friends/reject" -> new FriendService().respond(user,Input.id(data,"requestId"),path.endsWith("accept"));
            case "/api/friends/remove" -> new FriendService().remove(user,Input.id(data,"userId"));
            case "/api/communities/join", "/api/communities/leave" -> new CommunityService().membership(user,Input.id(data,"communityId"),path.endsWith("join"));
            case "/api/posts" -> id=new PostService().submit(user,Input.id(data,"communityId"),Input.text(data,"title",1,160),Input.text(data,"body",1,10000));
            case "/api/posts/approve", "/api/posts/reject" -> new PostService().moderate(user,Input.id(data,"postId"),path.endsWith("approve"));
            case "/api/comments" -> id=new PostService().comment(user,Input.id(data,"postId"),Input.text(data,"body",1,2000));
            case "/api/conversations/private" -> id=new MessageService().privateConversation(user,Input.id(data,"userId"));
            case "/api/conversations/group" -> id=new MessageService().group(user,Input.text(data,"name",1,100),Input.list(data,"participants"));
            case "/api/messages" -> id=new MessageService().send(user,Input.id(data,"conversationId"),Input.text(data,"body",1,4000));
            case "/api/admin/users/suspend", "/api/admin/users/reactivate" -> new AdminService().suspend(user,Input.id(data,"userId"),path.endsWith("suspend"));
            case "/api/admin/communities/create", "/api/admin/communities/edit" -> id=new AdminService().community(user,data,path.endsWith("create"));
            case "/api/admin/communities/leader" -> new AdminService().assign(user,Input.id(data,"communityId"),Input.id(data,"leaderId"));
            default -> throw new ApiException(404,"Endpoint not found");
        }
        return id>0?Map.of("message","Saved successfully","id",id):Map.of("message","Saved successfully");
    }
    private static void requireMethod(String method,String expected) { ApiException.require(method.equals(expected),405,"Use " + expected); }
    @SuppressWarnings("unchecked") private static Map<String,Object> body(HttpExchange exchange) throws IOException {
        String origin=exchange.getRequestHeaders().getFirst("Origin");
        String host=exchange.getRequestHeaders().getFirst("Host");
        ApiException.require(origin==null || origin.equals("http://"+host) || origin.equals("https://"+host),403,"Cross-origin writes are not allowed");
        String type=exchange.getRequestHeaders().getFirst("Content-Type"); ApiException.require(type!=null && type.toLowerCase(Locale.ROOT).startsWith("application/json"),400,"Send application/json");
        byte[] bytes=exchange.getRequestBody().readNBytes(65537); ApiException.require(bytes.length<=65536,413,"Request too large");
        Object parsed=Json.parse(new String(bytes,StandardCharsets.UTF_8)); ApiException.require(parsed instanceof Map<?,?>,400,"JSON object required"); return (Map<String,Object>)parsed;
    }
    private static Map<String,Object> query(String raw) {
        Map<String,Object> data=new LinkedHashMap<>(); if (raw==null || raw.isBlank()) return data;
        for (String pair:raw.split("&")) { String[] parts=pair.split("=",2); data.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts.length==2?parts[1]:"",StandardCharsets.UTF_8)); } return data;
    }
    private static String cookie(HttpExchange exchange,String name) {
        String value=exchange.getRequestHeaders().getFirst("Cookie"); if (value==null) return null;
        for (String part:value.split(";")) { String[] bits=part.trim().split("=",2); if (bits.length==2 && bits[0].equals(name)) return bits[1]; } return null;
    }
    private static void setCookie(HttpExchange exchange,String token,int age) { exchange.getResponseHeaders().add("Set-Cookie","ff_session="+token+"; HttpOnly; SameSite=Strict; Path=/; Max-Age="+age); }
}
