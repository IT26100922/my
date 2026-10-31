package com.friendfinder.util;

import java.util.*;

public final class Input {
    private Input() {}
    public static String text(Map<String,Object> data, String key, int min, int max) {
        Object raw = data.get(key); String value = raw instanceof String ? ((String)raw).trim() : "";
        ApiException.require(value.length() >= min && value.length() <= max, 400, key + " must contain " + min + "–" + max + " characters"); return value;
    }
    public static long id(Map<String,Object> data, String key) {
        try { long result = Long.parseLong(String.valueOf(data.get(key))); if (result < 1) throw new NumberFormatException(); return result; }
        catch (NumberFormatException e) { throw new ApiException(400, "Invalid " + key); }
    }
    public static String password(Map<String,Object> data,String key,int minimum) {
        Object raw=data.get(key);
        ApiException.require(raw instanceof String,400,key + " is required");
        String value=(String)raw;
        ApiException.require(!value.isBlank() && value.length()>=minimum && value.length()<=128,400,key + " must contain " + minimum + "–128 characters");
        return value; // Password whitespace is significant; never silently trim it.
    }
    public static String choice(Map<String,Object> data, String key, String... choices) {
        String value = text(data, key, 1, 60);
        ApiException.require(Arrays.asList(choices).contains(value), 400, "Invalid " + key); return value;
    }
    public static List<?> list(Map<String,Object> data, String key) {
        ApiException.require(data.get(key) instanceof List<?>, 400, key + " must be an array"); return (List<?>)data.get(key);
    }
}
