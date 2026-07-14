package com.cortezromeo.taixiu.economy;

import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.OfflinePlayer;

public final class PlayerPointsCurrencyGateway implements CurrencyGateway {
    private final PlayerPointsAPI playerPoints;

    public PlayerPointsCurrencyGateway(PlayerPointsAPI playerPoints) { this.playerPoints = playerPoints; }

    @Override public long maximumTransaction() { return Integer.MAX_VALUE; }

    @Override public long balance(OfflinePlayer player) { return playerPoints.look(player.getUniqueId()); }

    @Override public CurrencyTransaction debit(OfflinePlayer player, long amount) {
        if (amount > Integer.MAX_VALUE) return CurrencyTransaction.failure("amount exceeds PlayerPoints integer limit");
        return playerPoints.take(player.getUniqueId(), (int) amount)
                ? CurrencyTransaction.success() : CurrencyTransaction.failure("PlayerPoints rejected the debit");
    }

    @Override public CurrencyTransaction credit(OfflinePlayer player, long amount) {
        if (amount > Integer.MAX_VALUE) return CurrencyTransaction.failure("amount exceeds PlayerPoints integer limit");
        return playerPoints.give(player.getUniqueId(), (int) amount)
                ? CurrencyTransaction.success() : CurrencyTransaction.failure("PlayerPoints rejected the credit");
    }
}
