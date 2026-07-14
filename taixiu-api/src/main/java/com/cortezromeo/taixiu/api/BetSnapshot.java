package com.cortezromeo.taixiu.api;

import java.util.UUID;

public record BetSnapshot(UUID playerId, String playerName, TaiXiuResult side, long stake, boolean taxBypass) {
}
