package com.moviebooking.config;

/**
 * Names of the HttpSession attributes used for login state.
 * INFORMATION HIDING: other classes use these constants instead of raw strings.
 */
public final class SessionKeys {

    public static final String USER_ID = "loggedUserId";
    public static final String ROLE = "loggedUserRole";
    public static final String USER_NAME = "loggedUserName";
    public static final String LEVEL = "loggedUserLevel";
    public static final String EMAIL = "loggedUserEmail";

    private SessionKeys() {
    }
}
