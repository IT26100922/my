package com.friendfinder.util;

public class ApiException extends RuntimeException {
    private final int status;
    public ApiException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
    public static void require(boolean condition, int status, String message) {
        if (!condition) throw new ApiException(status, message);
    }
}
