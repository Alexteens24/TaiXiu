package com.cortezromeo.taixiu.storage;

import com.cortezromeo.taixiu.api.CurrencyTyppe;

import java.util.UUID;

@SuppressWarnings("deprecation")
public record JournalEntry(String id, long sessionId, UUID playerId, String playerName,
                           CurrencyTyppe currency, String kind, long amount, String status, String context) {
    public JournalEntry(String id, long sessionId, UUID playerId, String playerName,
                        CurrencyTyppe currency, String kind, long amount) {
        this(id, sessionId, playerId, playerName, currency, kind, amount, "PREPARED", "LEGACY");
    }

    public JournalEntry(String id, long sessionId, UUID playerId, String playerName,
                        CurrencyTyppe currency, String kind, long amount, String status) {
        this(id, sessionId, playerId, playerName, currency, kind, amount, status, "LEGACY");
    }

    public JournalEntry withContext(String value) {
        return new JournalEntry(id, sessionId, playerId, playerName, currency, kind, amount, status, value);
    }
}
