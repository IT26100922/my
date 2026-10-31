package com.friendfinder;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import com.friendfinder.controller.*;
import com.friendfinder.util.Config;

public final class Main {
    public static void main(String[] args) throws Exception {
        try { Class.forName("org.postgresql.Driver"); }
        catch (ClassNotFoundException e) { System.out.println("PostgreSQL JDBC driver is missing. Place its JAR in backend/lib. Static pages are available; database APIs require the driver and PostgreSQL configuration."); }
        int port=Integer.parseInt(Config.get("server.port","8080"));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/api/",new ApiController());
        server.createContext("/",new StaticController(Config.get("frontend.path","frontend")));
        server.setExecutor(Executors.newFixedThreadPool(12));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
        server.start(); System.out.println("IT Friend Finder: http://localhost:"+port);
    }
}
