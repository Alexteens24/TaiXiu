package com.cortezromeo.taixiu.storage;

import com.cortezromeo.taixiu.api.CurrencyTyppe;

import java.util.UUID;

@SuppressWarnings("deprecation")
public record RolloverOffer(String id, long sourceSessionId, long targetSessionId, UUID playerId,
                            String playerName, CurrencyTyppe currency, long amount, int depth,
                            long expiresAt, String status, String journalId) { }
