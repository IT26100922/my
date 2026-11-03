package com.moviebooking.booking.policy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refund rules by notice period. The rule is chosen from the whole hours left before the show,
 * and the first matching policy is used: full (24h+), partial (2h to 24h), none (under 2h).
 */
class CancellationPolicyTest {

    private static final List<CancellationPolicy> POLICIES =
            List.of(new FullRefundPolicy(), new PartialRefundPolicy(), new NoRefundPolicy());

    private static CancellationPolicy policyFor(long hours) {
        return POLICIES.stream().filter(p -> p.supports(hours)).findFirst().orElseThrow();
    }

    @Test
    void twentyFourHoursOrMoreGivesFullRefund() {
        assertThat(policyFor(24)).isInstanceOf(FullRefundPolicy.class);
        assertThat(policyFor(100)).isInstanceOf(FullRefundPolicy.class);
        assertThat(policyFor(24).refundFor(new BigDecimal("1500.00"))).isEqualByComparingTo("1500.00");
    }

    @Test
    void twoToTwentyFourHoursGivesHalfRefundRoundedHalfUp() {
        assertThat(policyFor(23)).isInstanceOf(PartialRefundPolicy.class);
        assertThat(policyFor(2)).isInstanceOf(PartialRefundPolicy.class);
        assertThat(policyFor(12).refundFor(new BigDecimal("1200.00"))).isEqualByComparingTo("600.00");
        // 1001.00 * 0.5 = 500.50 exactly; 1000.01 * 0.5 = 500.005 rounds half-up to 500.01
        assertThat(policyFor(12).refundFor(new BigDecimal("1001.00"))).isEqualByComparingTo("500.50");
        assertThat(policyFor(12).refundFor(new BigDecimal("1000.01"))).isEqualByComparingTo("500.01");
    }

    @Test
    void lessThanTwoHoursGivesNoRefund() {
        assertThat(policyFor(1)).isInstanceOf(NoRefundPolicy.class);
        assertThat(policyFor(0)).isInstanceOf(NoRefundPolicy.class);
        assertThat(policyFor(1).refundFor(new BigDecimal("1200.00"))).isEqualByComparingTo("0.00");
    }
}
