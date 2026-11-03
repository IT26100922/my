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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giving back <em>some</em> of the seats of a paid booking.
 *
 * <p>The refund is worked out by the same policy as a full cancellation (24 h 100 %, 2–24 h 50 %,
 * under 2 h nothing), but only on the value of the seats that are released. The rest of the booking keeps
 * its seats and stays CONFIRMED.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartialCancelTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private MockHttpSession register(String email) throws Exception {
        String body = "{\"fullName\":\"Part Cancel\",\"email\":\"" + email + "\",\"password\":\"password1\"}";
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    @Test
    void releasingOneSeatRefundsTheFullPriceOfThatSeatFarFromShowtime() throws Exception {
        Long showId = jdbc.queryForObject("SELECT MAX(id) FROM showtimes", Long.class);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", LocalDateTime.now().plusDays(4), showId);

        MockHttpSession session = register("part-" + UUID.randomUUID() + "@cinema.lk");
        MvcResult booked = mvc.perform(post("/api/customer/bookings").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"A1\",\"A2\",\"A3\"]}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        JsonNode booking = mapper.readTree(booked.getResponse().getContentAsString());
        long bookingId = booking.get("id").asLong();
        BigDecimal total = new BigDecimal(booking.get("totalAmount").asText());

        mvc.perform(post("/api/customer/payments").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"method\":\"CARD\",\"reference\":\"4111111111111111\"}"))
                .andExpect(status().is2xxSuccessful());

        // One seat of the three goes back. A4 stays held, so ask for a seat the booking really owns.
        MvcResult result = mvc.perform(post("/api/customer/bookings/" + bookingId + "/cancel-seats")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatCodes\":[\"A3\"]}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.policy").value("Full refund (cancelled 24h+ before show)"))
                .andReturn();
        BigDecimal refund = new BigDecimal(mapper.readTree(result.getResponse().getContentAsString())
                .get("refundAmount").asText());
        BigDecimal seatValue = total.divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP);
        // Every seat in row A is the same band, so the refund is a third of the total.
        org.junit.jupiter.api.Assertions.assertTrue(refund.compareTo(seatValue) <= 0
                        && refund.compareTo(seatValue.subtract(new BigDecimal("0.05"))) >= 0,
                "a single seat of three should refund about a third, got " + refund);

        // The booking keeps the other two seats and is still paid for.
        mvc.perform(get("/api/customer/bookings/" + bookingId).session(session))
                .andExpect(jsonPath("$.seatCodes.length()").value(2))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // A released seat is free again, so somebody else can take it.
        MockHttpSession other = register("part2-" + UUID.randomUUID() + "@cinema.lk");
        mvc.perform(post("/api/customer/bookings").session(other).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"A3\"]}"))
                .andExpect(status().is2xxSuccessful());

        // And the last seat cannot be dropped: that is a full cancellation.
        mvc.perform(post("/api/customer/bookings/" + bookingId + "/cancel-seats").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seatCodes\":[\"A1\",\"A2\"]}"))
                .andExpect(status().isBadRequest());
    }
}
