package com.moviebooking.promo;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tier 1 pricing strategy (promo codes) and Tier 2 money (the wallet ledger).
 *
 * <p>The wallet is checked here because it must be an honest ledger, not a number that drifts: a top-up
 * writes a TOP_UP row, a wallet payment writes a PAYMENT row, and a refund writes a REFUND row - and the
 * three of them have to add up to the balance on the customer.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PromoAndWalletTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private MockHttpSession register(String email) throws Exception {
        mvc.perform(post("/api/auth/customer/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Wallet Fan\",\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    private MockHttpSession admin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@cinema.lk\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    @Test
    void adminCreatesAPromoAndTheCustomerGetsTheDiscountOnTheBooking() throws Exception {
        String code = "TEST" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String body = "{\"code\":\"" + code + "\",\"type\":\"PERCENT\",\"value\":25,\"minSpend\":100,"
                + "\"validFrom\":\"" + java.time.LocalDate.now() + "\",\"validUntil\":\""
                + java.time.LocalDate.now().plusDays(10) + "\",\"maxUses\":5,\"description\":\"test promo\"}";
        mvc.perform(post("/api/admin/promos").session(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.code").value(code));

        MockHttpSession customer = register("promo-" + UUID.randomUUID() + "@cinema.lk");
        // The promo box on the booking page asks this before the customer commits.
        mvc.perform(post("/api/customer/promo/check").session(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"total\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.discount").value(500.0))
                .andExpect(jsonPath("$.newTotal").value(1500.0));

        // A basket under the minimum spend is refused, with a reason the page can show.
        mvc.perform(post("/api/customer/promo/check").session(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"total\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        // And a code that does not exist is not an error, it is simply not valid.
        mvc.perform(post("/api/customer/promo/check").session(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOPE-404\",\"total\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        Long showId = jdbc.queryForObject("SELECT MIN(id) FROM showtimes", Long.class);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", LocalDateTime.now().plusDays(6), showId);
        MvcResult booked = mvc.perform(post("/api/customer/bookings").session(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"B1\",\"B2\"],\"promoCode\":\"" + code + "\"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.promoCode").value(code))
                .andReturn();
        JsonNode booking = mapper.readTree(booked.getResponse().getContentAsString());
        assertTrue(new BigDecimal(booking.get("discountAmount").asText()).compareTo(BigDecimal.ZERO) > 0);
        mvc.perform(get("/api/customer/bookings/" + booking.get("id").asLong()).session(customer))
                .andExpect(jsonPath("$.promoCode").value(code));
    }

    @Test
    void walletTopUpPayAndRefundAreWrittenInTheLedger() throws Exception {
        MockHttpSession customer = register("ledger-" + UUID.randomUUID() + "@cinema.lk");

        mvc.perform(get("/api/customer/wallet").session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));

        // Too small a top-up is refused by bean validation.
        mvc.perform(post("/api/customer/wallet/top-up").session(customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10,\"method\":\"CARD\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/customer/wallet/top-up").session(customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000,\"method\":\"CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(10000.0))
                .andExpect(jsonPath("$.entries[0].type").value("TOP_UP"));

        Long showId = jdbc.queryForObject("SELECT MIN(id) FROM showtimes", Long.class);
        jdbc.update("UPDATE showtimes SET start_time = ? WHERE id = ?", LocalDateTime.now().plusDays(7), showId);
        MvcResult booked = mvc.perform(post("/api/customer/bookings").session(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":" + showId + ",\"seatCodes\":[\"D1\"]}"))
                .andExpect(status().is2xxSuccessful()).andReturn();
        long bookingId = mapper.readTree(booked.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/customer/payments").session(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":" + bookingId + ",\"method\":\"WALLET\",\"reference\":\"WALLET\"}"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        JsonNode afterPayment = mapper.readTree(mvc.perform(get("/api/customer/wallet").session(customer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        BigDecimal paid = new BigDecimal(afterPayment.get("balance").asText());
        assertTrue(paid.compareTo(new BigDecimal("10000")) < 0, "the wallet pays for the seat");
        assertEquals("PAYMENT", afterPayment.get("entries").get(0).get("type").asText());

        // Cancelling more than 24 hours before the show returns 100 % into the wallet, at once.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/customer/bookings/" + bookingId).session(customer))
                .andExpect(status().is2xxSuccessful())
                ;

        JsonNode afterRefund = mapper.readTree(mvc.perform(get("/api/customer/wallet").session(customer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(10000.0, afterRefund.get("balance").asDouble(), 0.001,
                "the refund lands back in the wallet");
        assertEquals("REFUND", afterRefund.get("entries").get(0).get("type").asText());
    }

    @Test
    void aCustomerWithWalletHistoryCanStillDeleteTheirAccount() throws Exception {
        // The wallet ledger and the waitlist point at the customer with a foreign key. Deleting the
        // account has to take them with it, instead of failing on the constraint with a 500.
        MockHttpSession customer = register("leaver-" + UUID.randomUUID() + "@cinema.lk");
        mvc.perform(post("/api/customer/wallet/top-up").session(customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250,\"method\":\"CARD\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/customer/profile").session(customer))
                .andExpect(status().isOk());
    }
}
