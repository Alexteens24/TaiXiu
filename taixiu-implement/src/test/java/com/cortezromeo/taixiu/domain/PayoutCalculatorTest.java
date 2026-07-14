package com.cortezromeo.taixiu.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayoutCalculatorTest {

    @Test
    void calculatesGrossAndTaxedPayouts() {
        assertEquals(2_000, PayoutCalculator.calculate(1_000, 0, false));
        assertEquals(1_900, PayoutCalculator.calculate(1_000, 10, false));
        assertEquals(2_000, PayoutCalculator.calculate(1_000, 10, true));
    }

    @Test
    void validatesInputsAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> PayoutCalculator.calculate(0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> PayoutCalculator.calculate(1, 101, false));
        assertThrows(ArithmeticException.class, () -> PayoutCalculator.calculate(Long.MAX_VALUE, 0, false));
    }
}
