package com.moviebooking.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Reviews: public reading, the customer-only posting guard, eligibility when nothing has been booked,
 * and input validation. The full "booked, show started, reviewed" path is covered by the service rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private MockHttpSession register(String email) throws Exception {
        String body = "{\"fullName\":\"Reviewer\",\"email\":\"" + email + "\",\"password\":\"password1\"}";
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        var r = mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    private long firstMovieId() throws Exception {
        var r = mvc.perform(get("/api/movies")).andExpect(status().isOk()).andReturn();
        JsonNode movies = mapper.readTree(r.getResponse().getContentAsString());
        return movies.get(0).get("id").asLong();
    }

    @Test
    void publicReviewSummaryIsAvailableWithoutLogin() throws Exception {
        long movieId = firstMovieId();
        mvc.perform(get("/api/movies/" + movieId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.average").isNumber())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void postingReviewRequiresCustomerSession() throws Exception {
        mvc.perform(post("/api/customer/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"rating\":5,\"comment\":\"Great\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void customerWithNoBookingHasNothingToReview() throws Exception {
        MockHttpSession session = register("rev-" + UUID.randomUUID() + "@test.lk");
        long movieId = firstMovieId();
        mvc.perform(get("/api/customer/reviews/eligible").param("movieId", String.valueOf(movieId)).session(session))
                .andExpect(status().isNoContent());
    }

    @Test
    void ratingOutsideOneToFiveIsRejected() throws Exception {
        MockHttpSession session = register("rev-" + UUID.randomUUID() + "@test.lk");
        mvc.perform(post("/api/customer/reviews").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"rating\":6}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reviewIsAllowedOnlyAfterConfirmedShowHasStarted() throws Exception {
        Long showId = jdbc.queryForObject("SELECT MIN(id) FROM showtimes", Long.class);
        Long movieId = jdbc.queryForObject("SELECT movie_id FROM showtimes WHERE id = ?", Long.class, showId);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", LocalDateTime.now().plusDays(3), showId);

        MockHttpSession session = register("rev-" + UUID.randomUUID() + "@test.lk");
        MvcResult booked = mvc.perform(post("/api/customer/bookings").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"H3\"]}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        long bookingId = mapper.readTree(booked.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/customer/payments").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"method\":\"CARD\",\"reference\":\"4111111111111111\"}"))
                .andExpect(status().is2xxSuccessful());

        // Show is still in the future: nothing to review, and posting is refused.
        mvc.perform(get("/api/customer/reviews/eligible").param("movieId", String.valueOf(movieId)).session(session))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/customer/reviews").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"rating\":4}"))
                .andExpect(status().isBadRequest());

        // Show has started: the booking becomes eligible and the review is accepted once.
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", LocalDateTime.now().minusHours(1), showId);
        mvc.perform(get("/api/customer/reviews/eligible").param("movieId", String.valueOf(movieId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId));
        mvc.perform(post("/api/customer/reviews").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"rating\":4,\"comment\":\"  Lovely film  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Lovely film"));
        mvc.perform(post("/api/customer/reviews").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"rating\":5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/customer/reviews/eligible").param("movieId", String.valueOf(movieId)).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/movies/" + movieId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.average").value(4.0))
                .andExpect(jsonPath("$.items[0].customerName").value("Reviewer"));
    }

    @Test
    void customerCannotReviewSomeoneElsesBooking() throws Exception {
        MockHttpSession session = register("rev-" + UUID.randomUUID() + "@test.lk");
        mvc.perform(post("/api/customer/reviews").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":999999,\"rating\":4,\"comment\":\"Nice\"}"))
                .andExpect(status().isNotFound());
    }
}
