package com.moviebooking.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role rules of the API, tested where they are enforced: {@link AuthInterceptor}.
 *
 * <p>Three cases matter: nobody anonymous gets in, a customer cannot pretend to be staff, and the
 * door account ({@code BOX_OFFICE}) can scan tickets but cannot change a film or read the reports.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleGuardTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private MockHttpSession login(String path, String email, String password) throws Exception {
        MvcResult r = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    @Test
    void anonymousCallerIsTurnedAwayWith401() throws Exception {
        mvc.perform(get("/api/admin/bookings/page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please log in first"));
        mvc.perform(get("/api/customer/wallet"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotReachTheAdminArea() throws Exception {
        MockHttpSession customer = login("/api/auth/customer/login", "customer@cinema.lk", "customer123");
        mvc.perform(get("/api/admin/bookings/page").session(customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this area"));
    }

    @Test
    void adminCannotUseTheCustomerArea() throws Exception {
        MockHttpSession admin = login("/api/auth/admin/login", "admin@cinema.lk", "admin123");
        mvc.perform(get("/api/customer/wallet").session(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void boxOfficeMayScanTicketsButMayNotEditAnything() throws Exception {
        // The door account is turned into BOX_OFFICE for this test, then put back by the test database reset.
        jdbc.update("UPDATE users SET permission_level = 'BOX_OFFICE' WHERE email = 'staff@cinema.lk'");
        MockHttpSession door = login("/api/auth/admin/login", "staff@cinema.lk", "staff123");
        try {
            // Scanning is the job: an unknown code is a normal 200 answer, not a refusal.
            mvc.perform(post("/api/admin/tickets/validate").session(door)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"BK-NOT-A-CODE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("NOT_FOUND"));
            // Bookings may be looked at...
            mvc.perform(get("/api/admin/bookings/page").session(door)).andExpect(status().isOk());
            // ...but not films, screens, users, promos or the reports.
            mvc.perform(get("/api/admin/customers").session(door)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/promos").session(door)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/files/export-sales").session(door)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/analytics").session(door)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("The box-office account can only scan tickets and view bookings"));
        } finally {
            jdbc.update("UPDATE users SET permission_level = 'STAFF' WHERE email = 'staff@cinema.lk'");
            door.invalidate();
        }
    }
}
