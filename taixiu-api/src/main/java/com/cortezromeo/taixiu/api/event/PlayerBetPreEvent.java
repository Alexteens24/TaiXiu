package com.cortezromeo.taixiu.api.event;

import com.cortezromeo.taixiu.api.TaiXiuResult;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class PlayerBetPreEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final TaiXiuResult side;
    private final long amount;
    private boolean cancelled;

    public PlayerBetPreEvent(Player player, TaiXiuResult side, long amount) {
        this.player = player;
        this.side = side;
        this.amount = amount;
    }

    public Player getPlayer() { return player; }
    public TaiXiuResult getSide() { return side; }
    public long getAmount() { return amount; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
