package com.moviebooking.config;

import com.moviebooking.user.entity.Admin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Role-based access control for the REST API.
 * <ul>
 *   <li>/api/admin/**    → the session must belong to an ADMIN;</li>
 *   <li>/api/customer/** → the session must belong to a CUSTOMER;</li>
 *   <li>a BOX_OFFICE admin may only scan tickets and look at bookings, and is refused everything else.</li>
 * </ul>
 * Login endpoints live under /api/auth/** and are not intercepted.
 *
 * <p>The box-office rule sits here, in one place, rather than in the pages: hiding a menu item is a
 * convenience, refusing the request is the actual protection.</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** What a box-office (door) account is allowed to call. */
    private static final String[] BOX_OFFICE_ALLOWED = {
            "/api/admin/tickets/",     // scan a ticket, read today's attendance
            "/api/admin/bookings"      // look a booking up and sell seats
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String path = request.getRequestURI();
        String requiredRole = path.startsWith("/api/admin/") ? "ADMIN" : "CUSTOMER";

        HttpSession session = request.getSession(false);
        String currentRole = session == null ? null : (String) session.getAttribute(SessionKeys.ROLE);
        String level = session == null ? null : (String) session.getAttribute(SessionKeys.LEVEL);

        if (requiredRole.equals(currentRole)) {
            if ("ADMIN".equals(currentRole) && Admin.BOX_OFFICE.equals(level) && !allowedForBoxOffice(path)) {
                write(response, HttpServletResponse.SC_FORBIDDEN,
                        "The box-office account can only scan tickets and view bookings");
                return false;
            }
            return true;
        }
        boolean loggedIn = currentRole != null;
        write(response, loggedIn ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED,
                loggedIn ? "You do not have access to this area" : "Please log in first");
        return false;
    }

    private static boolean allowedForBoxOffice(String path) {
        for (String prefix : BOX_OFFICE_ALLOWED) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
