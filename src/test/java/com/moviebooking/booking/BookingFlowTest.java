package com.moviebooking.booking;

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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end customer path: register, book seats, pay, get the confirmation, and check that another
 * customer cannot take the same seat or read the booking.
 * The seed show dates are in the past by the time tests run, so the first show is moved into the future
 * inside the throw-away test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingFlowTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private MockHttpSession register(String email) throws Exception {
        String body = "{\"fullName\":\"Test Customer\",\"email\":\"" + email + "\",\"password\":\"password1\"}";
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    @Test
    void customerBooksPaysAndSeatIsProtected() throws Exception {
        // Move the first seed show into the future so booking is allowed.
        Long showId = jdbc.queryForObject("SELECT MIN(id) FROM showtimes", Long.class);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", LocalDateTime.now().plusDays(3), showId);
        String seat = "H12";

        MockHttpSession alice = register("alice-" + UUID.randomUUID() + "@cinema.lk");
        MvcResult booked = mvc.perform(post("/api/customer/bookings").session(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"" + seat + "\"]}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();
        JsonNode b = mapper.readTree(booked.getResponse().getContentAsString());
        long bookingId = b.get("id").asLong();

        mvc.perform(post("/api/customer/payments").session(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"method\":\"CARD\",\"reference\":\"4111111111111111\"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mvc.perform(get("/api/customer/notifications").session(alice))
                .andExpect(jsonPath("$.items[0].type").value("BOOKING_CONFIRMED"));

        // A second customer cannot book the same seat for the same show...
        MockHttpSession bob = register("bob-" + UUID.randomUUID() + "@cinema.lk");
        mvc.perform(post("/api/customer/bookings").session(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"" + seat + "\"]}"))
                .andExpect(status().is4xxClientError());
        // ...and cannot read Alice's booking.
        mvc.perform(get("/api/customer/bookings/" + bookingId).session(bob))
                .andExpect(status().is4xxClientError());
    }
}
