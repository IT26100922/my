package com.moviebooking.checkin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tier 1 ticket check-in at the door.
 *
 * <p>A ticket is only good once: the first scan says OK, the second says ALREADY_USED. An unpaid hold, a
 * cancelled booking, a code that does not exist and a ticket for another day are all refused, each with
 * its own verdict, because the door staff need to know <em>why</em>.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckInTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private MockHttpSession admin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@cinema.lk\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    private MockHttpSession customer() throws Exception {
        String email = "door-" + UUID.randomUUID() + "@cinema.lk";
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Door Test\",\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    @Test
    void aTicketIsAcceptedOnceAndOnlyOnTheRightDay() throws Exception {
        MockHttpSession admin = admin();
        MockHttpSession customer = customer();

        Long showId = jdbc.queryForObject("SELECT MAX(id) FROM showtimes", Long.class);
        // Book while the show is still in the future (booking refuses a started show) ...
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?",
                LocalDateTime.now().plusDays(2), showId);

        // An unpaid hold is not a ticket.
        MvcResult held = mvc.perform(post("/api/customer/bookings").session(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"E1\"]}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        String heldRef = mapper.readTree(held.getResponse().getContentAsString()).get("reference").asText();
        mvc.perform(post("/api/admin/tickets/validate").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + heldRef + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NOT_PAID"));

        // A paid ticket is accepted, and then refuses a second scan.
        MvcResult paid = mvc.perform(post("/api/customer/bookings").session(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"E2\"]}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        JsonNode paidBooking = mapper.readTree(paid.getResponse().getContentAsString());
        long bookingId = paidBooking.get("id").asLong();
        String reference = paidBooking.get("reference").asText();
        mvc.perform(post("/api/customer/payments").session(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"method\":\"CARD\",\"reference\":\"4111111111111111\"}"))
                .andExpect(status().is2xxSuccessful());

        // ... then push the show into today (the doors have opened) and scan.
        jdbc.update("UPDATE showtimes SET start_time = TIMESTAMP(CURDATE(), '00:05:00') WHERE id = ?", showId);

        mvc.perform(post("/api/admin/tickets/validate").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + reference + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("OK"))
                .andExpect(jsonPath("$.seatCodes[0]").value("E2"));
        mvc.perform(post("/api/admin/tickets/validate").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + reference + "\"}"))
                .andExpect(jsonPath("$.result").value("ALREADY_USED"));

        // The database now remembers who was let in, and when.
        Integer checkedIn = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE id = ? AND status = 'CHECKED_IN' AND checked_in_at IS NOT NULL",
                Integer.class, bookingId);
        org.junit.jupiter.api.Assertions.assertEquals(1, checkedIn.intValue());

        // A code nobody knows, and a booking for another day.
        mvc.perform(post("/api/admin/tickets/validate").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"BK00000000\"}"))
                .andExpect(jsonPath("$.result").value("NOT_FOUND"));

        Long futureShow = jdbc.queryForObject("SELECT MIN(id) FROM showtimes", Long.class);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", LocalDateTime.now().plusDays(9), futureShow);
        MvcResult other = mvc.perform(post("/api/customer/bookings").session(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + futureShow + ",\"seatCodes\":[\"E3\"]}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        JsonNode otherBooking = mapper.readTree(other.getResponse().getContentAsString());
        mvc.perform(post("/api/customer/payments").session(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + otherBooking.get("id").asLong()
                                + ",\"method\":\"CARD\",\"reference\":\"4111111111111112\"}"))
                .andExpect(status().is2xxSuccessful());
        // A ticket for another day is refused, and says which day it was for.
        mvc.perform(post("/api/admin/tickets/validate").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + otherBooking.get("reference").asText() + "\"}"))
                .andExpect(jsonPath("$.result").value("NOT_STARTED_TODAY"));
        jdbc.update("UPDATE showtimes SET start_time = TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '19:00:00') " +
                "WHERE id = ?", futureShow);
        mvc.perform(post("/api/admin/tickets/validate").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + otherBooking.get("reference").asText() + "\"}"))
                .andExpect(jsonPath("$.result").value("WRONG_DAY"));

        // The attendance strip at the top of the door page.
        mvc.perform(get("/api/admin/tickets/today").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedIn").isNumber())
                .andExpect(jsonPath("$.expected").isNumber())
                .andExpect(jsonPath("$.attendancePercent").isNumber());

        // A cancelled booking is not a ticket either.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/customer/bookings/" + otherBooking.get("id").asLong()).session(customer))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/admin/tickets/validate").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + otherBooking.get("reference").asText() + "\"}"))
                .andExpect(jsonPath("$.result").value("CANCELLED"));
    }
}
