package com.cortezromeo.taixiu.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

public final class VaultCurrencyGateway implements CurrencyGateway {
    private final Economy economy;

    public VaultCurrencyGateway(Economy economy) { this.economy = economy; }

    @Override public long balance(OfflinePlayer player) { return (long) Math.floor(economy.getBalance(player)); }

    @Override public CurrencyTransaction debit(OfflinePlayer player, long amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess() ? CurrencyTransaction.success() : CurrencyTransaction.failure(response.errorMessage);
    }

    @Override public CurrencyTransaction credit(OfflinePlayer player, long amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess() ? CurrencyTransaction.success() : CurrencyTransaction.failure(response.errorMessage);
    }
}
