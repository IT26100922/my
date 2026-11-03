package com.moviebooking.report;

import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.report.dto.ReportDto.SalesReport;
import com.moviebooking.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A cancelled booking must not change any sales figure (money, refunds, payment count, tickets, daily chart,
 * payment methods, movies). Its payment stays in the payment history, which is covered by the Payments list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CancelledBookingsExcludedTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportService reports;
    @Autowired PaymentRepository payments;

    private MockHttpSession register(String email) throws Exception {
        String body = "{\"fullName\":\"Report Customer\",\"email\":\"" + email + "\",\"password\":\"password1\"}";
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        var r = mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    @Test
    void cancelledBookingLeavesEverySalesFigure() throws Exception {
        Long showId = jdbc.queryForObject("SELECT MIN(id) FROM showtimes", Long.class);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", LocalDateTime.now().plusDays(3), showId);
        LocalDate today = LocalDate.now();
        SalesReport before = reports.sales(today, today);

        MockHttpSession dana = register("dana-" + UUID.randomUUID() + "@cinema.lk");
        var booked = mvc.perform(post("/api/customer/bookings").session(dana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"F7\"]}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        long bookingId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(booked.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/customer/payments").session(dana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"method\":\"CARD\",\"reference\":\"4111111111111111\"}"))
                .andExpect(status().is2xxSuccessful());

        SalesReport paid = reports.sales(today, today);
        assertThat(paid.totals().payments()).isEqualTo(before.totals().payments() + 1);
        assertThat(paid.totals().gross()).isGreaterThan(before.totals().gross());

        mvc.perform(delete("/api/customer/bookings/" + bookingId).session(dana))
                .andExpect(status().is2xxSuccessful());

        SalesReport after = reports.sales(today, today);
        assertThat(after.totals().payments()).isEqualTo(before.totals().payments());
        assertThat(after.totals().gross()).isEqualByComparingTo(before.totals().gross());
        assertThat(after.totals().refunds()).isEqualByComparingTo(before.totals().refunds());
        assertThat(after.totals().net()).isEqualByComparingTo(before.totals().net());
        assertThat(after.totals().fees()).isEqualByComparingTo(before.totals().fees());
        assertThat(after.totals().tickets()).isEqualTo(before.totals().tickets());
        assertThat(after.daily()).isEqualTo(before.daily());
        assertThat(after.byMethod()).isEqualTo(before.byMethod());
        assertThat(after.byMovie()).isEqualTo(before.byMovie());

        // The transaction is still in the payment history.
        assertThat(payments.findByBookingId(bookingId)).hasSize(1);
    }
}
