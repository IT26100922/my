package com.moviebooking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 description of the REST API. Springdoc reads the controllers, so the document is never
 * out of date: it is generated from the same annotations Spring MVC uses to route the requests.
 *
 * <p>Open <code>/swagger-ui.html</code> while the app is running to try a call. The API is
 * session-cookie based, so log in through the site first, in the same browser, and the "Try it out"
 * buttons will carry the cookie.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String COOKIE_SCHEME = "sessionCookie";

    @Bean
    public OpenAPI cineMaxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CineMax Movie Ticket Booking API")
                        .version("3.0")
                        .description("""
                                REST API of the CineMax booking system (Spring Boot 3, MySQL 8).

                                **Areas**
                                * `/api/auth/**` — login, signup and logout for customers and admins.
                                * `/api/movies`, `/api/shows` — the catalogue, no login needed.
                                * `/api/customer/**` — booking, wallet, promos, waitlist. Needs a customer session.
                                * `/api/admin/**` — management, analytics, door check-in. Needs an admin session;
                                  a `BOX_OFFICE` admin is limited to ticket scanning and bookings.

                                **Errors** come back as `{"message": "..."}` with 400 (validation),
                                401 (not logged in), 403 (wrong role), 404 (not found) or 409 (seat clash).
                                """)
                        .contact(new Contact().name("CineMax development team")
                                .email("admin@cinema.lk"))
                        .license(new License().name("University coursework project")))
                .servers(List.of(new Server().url("/").description("This server")))
                .components(new Components().addSecuritySchemes(COOKIE_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Session cookie set by /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_SCHEME));
    }
}
