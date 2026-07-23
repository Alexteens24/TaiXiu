package com.cortezromeo.taixiu.storage;

import java.util.List;

public record SettlementPreparation(List<JournalEntry> insurancePayouts) {
    public SettlementPreparation { insurancePayouts = List.copyOf(insurancePayouts); }
}
