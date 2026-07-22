package com.cortezromeo.taixiu.storage;

import java.util.UUID;

public record BetMetadata(UUID playerId, double effectiveTax, boolean rolloverEligible,
                          boolean insuranceEligible, FundingSource fundingSource, int rolloverDepth) {

    public enum FundingSource { WALLET, ESCROW }

    public BetMetadata {
        if (playerId == null) throw new IllegalArgumentException("playerId is required");
        if ((!Double.isNaN(effectiveTax) && !Double.isFinite(effectiveTax))
                || effectiveTax < 0 || effectiveTax > 100)
            throw new IllegalArgumentException("effectiveTax must be between 0 and 100");
        if (fundingSource == null) fundingSource = FundingSource.WALLET;
        if (rolloverDepth < 0) throw new IllegalArgumentException("rolloverDepth cannot be negative");
    }

    public static BetMetadata legacy(UUID playerId, boolean taxBypass) {
        return new BetMetadata(playerId, taxBypass ? 0 : Double.NaN, false, false,
                FundingSource.WALLET, 0);
    }
}
