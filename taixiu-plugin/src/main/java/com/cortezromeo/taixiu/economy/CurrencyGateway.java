package com.cortezromeo.taixiu.economy;

import org.bukkit.OfflinePlayer;

public interface CurrencyGateway {
    default long maximumTransaction() { return Long.MAX_VALUE; }
    long balance(OfflinePlayer player);
    CurrencyTransaction debit(OfflinePlayer player, long amount);
    CurrencyTransaction credit(OfflinePlayer player, long amount);
}
