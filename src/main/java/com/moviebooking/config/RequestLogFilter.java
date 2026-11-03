package com.moviebooking.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One line per request in the application log:
 * {@code GET /api/shows/12 200 14 ms user=42}.
 *
 * <p>Written as a filter so it covers every endpoint, including the ones that fail, and so a slow request
 * shows up as a slow request instead of having to be guessed. Static files are skipped: they are served by
 * Spring's resource handler and would only add noise.</p>
 */
@Component
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("HTTP");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!isStatic(request.getRequestURI())) {
                long ms = Math.round((System.nanoTime() - started) / 1_000_000.0);
                HttpSession session = request.getSession(false);
                String user = session == null || session.getAttribute(SessionKeys.USER_ID) == null
                        ? "-"
                        : session.getAttribute(SessionKeys.USER_ID) + "/" + session.getAttribute(SessionKeys.ROLE);
                log.info("{} {} {} {} ms user={}", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), ms, user);
            }
        }
    }

    /** True for the pages and assets, which are not interesting in an API log. */
    private static boolean isStatic(String path) {
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/")
                || path.startsWith("/lang/") || path.endsWith(".html") || path.endsWith(".svg")
                || path.endsWith(".ico") || path.endsWith(".png") || path.endsWith(".jpg");
    }
}
