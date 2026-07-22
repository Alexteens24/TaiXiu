package com.cortezromeo.taixiu.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BetPermissionPolicyTest {
    @Test void highestMaxBetPermissionOverridesDefault() {
        assertEquals(2_000_000, BetPermissionPolicy.effectiveMaxBet(
                List.of("taixiu.maxbet.500000", "taixiu.maxbet.2000000"), 1_000_000));
    }

    @Test void invalidMaxBetPermissionsAreIgnored() {
        assertEquals(1_000_000, BetPermissionPolicy.effectiveMaxBet(
                List.of("taixiu.maxbet.*", "taixiu.maxbet.-2", "taixiu.maxbet.999999999999999999999"), 1_000_000));
    }

    @Test void taxDiscountUsesPercentagePointsAndClampsAtZero() {
        assertEquals(3.5, BetPermissionPolicy.effectiveTax(
                List.of("taixiu.tax.discount.1", "taixiu.tax.discount.1.5"), 5), 0.0001);
        assertEquals(0, BetPermissionPolicy.effectiveTax(List.of("taixiu.tax.discount.20"), 5), 0.0001);
    }
}
