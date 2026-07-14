package com.cortezromeo.taixiu.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SchedulerService {
    private final Plugin plugin;

    public SchedulerService(Plugin plugin) {
        this.plugin = plugin;
    }

    public ScheduledTask runGlobal(Runnable runnable) {
        return Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> runnable.run());
    }

    public ScheduledTask runGlobalTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, ignored -> runnable.run(), Math.max(1, initialDelayTicks), periodTicks);
    }

    public ScheduledTask runAsync(Runnable runnable) {
        return Bukkit.getAsyncScheduler().runNow(plugin, ignored -> runnable.run());
    }

    public ScheduledTask runAsyncTimer(Runnable runnable, long initialDelaySeconds, long periodSeconds) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin, ignored -> runnable.run(), initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
    }

    public boolean runEntity(Player player, Runnable runnable) {
        return player.getScheduler().execute(plugin, runnable, null, 1L);
    }

    public ScheduledTask runEntityTimer(Player player, Consumer<ScheduledTask> runnable,
                                        long initialDelayTicks, long periodTicks) {
        return player.getScheduler().runAtFixedRate(
                plugin, runnable, null, Math.max(1, initialDelayTicks), periodTicks);
    }

    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    public boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
