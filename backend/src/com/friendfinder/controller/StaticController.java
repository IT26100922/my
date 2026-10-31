package com.friendfinder.controller;

import com.sun.net.httpserver.*;
import java.io.*;
import java.nio.file.*;
import java.util.Map;

public final class StaticController implements HttpHandler {
    private final Path root;
    public StaticController(String root) { this.root=Path.of(root).toAbsolutePath().normalize(); }
    @Override public void handle(HttpExchange exchange) throws IOException {
        try { serve(exchange); }
        catch (IOException e) { System.err.println("Static file error: " + e); exchange.sendResponseHeaders(500,-1); exchange.close(); }
        catch (IllegalArgumentException e) { exchange.sendResponseHeaders(404,-1); exchange.close(); }
    }
    private void serve(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET") && !exchange.getRequestMethod().equals("HEAD")) { exchange.sendResponseHeaders(405,-1); exchange.close(); return; }
        String requested=exchange.getRequestURI().getPath(); if (requested.equals("/")) requested="/index.html";
        Path file=root.resolve(requested.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file) || hasLink(file)) { exchange.sendResponseHeaders(404,-1); exchange.close(); return; }
        String name=file.getFileName().toString(), extension=name.substring(name.lastIndexOf('.')+1);
        String type=Map.of("html","text/html; charset=utf-8","css","text/css; charset=utf-8","js","text/javascript; charset=utf-8","svg","image/svg+xml","png","image/png","ico","image/x-icon").getOrDefault(extension,"application/octet-stream");
        exchange.getResponseHeaders().set("Content-Type",type);
        exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        if (exchange.getRequestMethod().equals("HEAD")) { exchange.sendResponseHeaders(200,-1); exchange.close(); return; }
        byte[] bytes=Files.readAllBytes(file); exchange.sendResponseHeaders(200,bytes.length); try (OutputStream output=exchange.getResponseBody()) { output.write(bytes); }
    }
    /** Static assets must be ordinary files/directories within the configured root. */
    private boolean hasLink(Path file) throws IOException {
        for (Path part=file; !part.equals(root); part=part.getParent()) {
            var attributes=Files.readAttributes(part,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther()) return true;
        }
        return false;
    }
}
