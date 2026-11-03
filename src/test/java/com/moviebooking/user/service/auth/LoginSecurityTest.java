package com.moviebooking.user.service.auth;

import com.moviebooking.user.repository.LoginAttemptRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Admin/customer log-in rules: lockout, roles, logout, password length, security headers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired LoginAttemptRepository attempts;

    private static String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private MockHttpSession login(String path, String email, String password) throws Exception {
        MvcResult r = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json(email, password)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    @Test
    void sixthWrongPasswordIsBlockedWith429_andLockIsStoredHashed() throws Exception {
        String email = "lock-" + UUID.randomUUID() + "@cinema.lk";
        for (int i = 1; i <= 5; i++) {
            mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(email, "wrong-pass")))
                    .andExpect(status().is4xxClientError());
        }
        mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, "wrong-pass")))
                .andExpect(status().isTooManyRequests());
        // Even the right password is refused while locked.
        mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, "customer123")))
                .andExpect(status().isTooManyRequests());

        assertThat(attempts.findById(LoginAttemptService.emailKey(email))).isPresent();
        assertThat(attempts.findAll()).noneMatch(a -> a.getEmailHash().contains("@"));
    }

    @Test
    void ipLimitStopsOneAddressFromLockingOutManyAccounts() throws Exception {
        // 20 failures from one address (each for a different email) lock that address, not the other customers.
        String attacker = "10.9.9.9";
        for (int i = 0; i < LoginAttemptService.IP_MAX_FAILURES; i++) {
            mvc.perform(post("/api/auth/customer/login").with(from(attacker)).contentType(MediaType.APPLICATION_JSON)
                            .content(json("victim-" + UUID.randomUUID() + "@cinema.lk", "wrong-pass")))
                    .andExpect(status().is4xxClientError());
        }
        // The address is now locked, even for an email that has never failed.
        mvc.perform(post("/api/auth/customer/login").with(from(attacker)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("fresh-" + UUID.randomUUID() + "@cinema.lk", "wrong-pass")))
                .andExpect(status().isTooManyRequests());
        // A real customer on another address can still log in.
        mvc.perform(post("/api/auth/customer/login").with(from("10.1.1.1")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("customer@cinema.lk", "customer123")))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void adminLoginReportsSuperAdminLevel() throws Exception {
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("admin@cinema.lk", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionLevel").value("SUPER_ADMIN"));
    }

    @Test
    void staffCanLogInButCannotListAdmins() throws Exception {
        MockHttpSession staff = login("/api/auth/admin/login", "staff@cinema.lk", "staff123");
        mvc.perform(get("/api/auth/me").session(staff))
                .andExpect(jsonPath("$.permissionLevel").value("STAFF"));
        mvc.perform(get("/api/admin/admins").session(staff))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only SUPER_ADMIN accounts can manage other admins"));
    }

    @Test
    void superAdminCanListAdmins() throws Exception {
        MockHttpSession admin = login("/api/auth/admin/login", "admin@cinema.lk", "admin123");
        mvc.perform(get("/api/admin/admins").session(admin)).andExpect(status().isOk());
    }

    @Test
    void adminDataNeedsASession() throws Exception {
        mvc.perform(get("/api/admin/customers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/admins")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutExpiresTheSessionCookieAndEndsTheSession() throws Exception {
        MockHttpSession admin = login("/api/auth/admin/login", "admin@cinema.lk", "admin123");
        MvcResult out = mvc.perform(post("/api/auth/logout").session(admin))
                .andExpect(status().isOk()).andReturn();
        assertThat(out.getResponse().getHeader("Set-Cookie")).contains("JSESSIONID=", "Max-Age=0");
        assertThat(admin.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me").session(admin)).andExpect(status().isUnauthorized());
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/movies"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void passwordMustBeAtLeastEightCharacters() throws Exception {
        String base = "{\"fullName\":\"Len Test\",\"email\":\"%s\",\"password\":\"%s\"}";
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(base, "len6-" + UUID.randomUUID() + "@cinema.lk", "abc123")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(base, "len8-" + UUID.randomUUID() + "@cinema.lk", "abcd1234")))
                .andExpect(status().isCreated());
    }
}
