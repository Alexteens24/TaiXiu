package com.cortezromeo.taixiu.domain;

public final class PayoutCalculator {

    private PayoutCalculator() {
    }

    public static long calculate(long stake, double taxPercent, boolean bypassTax) {
        if (stake <= 0) throw new IllegalArgumentException("Stake must be positive");
        if (!Double.isFinite(taxPercent) || taxPercent < 0 || taxPercent > 100)
            throw new IllegalArgumentException("Tax must be between 0 and 100");
        if (bypassTax || taxPercent == 0) return Math.multiplyExact(stake, 2L);

        long profitAfterTax = Math.round(stake * (1D - taxPercent / 100D));
        return Math.addExact(stake, profitAfterTax);
    }
}
