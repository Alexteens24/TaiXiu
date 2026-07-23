package com.cortezromeo.taixiu.util;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.language.Messages;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public class MessageUtil {

    public static String getFormatMoneyDisplay(long money) {
        DecimalFormat formatter = new DecimalFormat(TaiXiu.plugin.getConfig().getString("format-money"));
        return formatter.format(money);
    }

    public static String getFormatResultName(@NotNull TaiXiuResult result) {
        if (result == TaiXiuResult.XIU)
            return Messages.XIU_NAME;
        if (result == TaiXiuResult.TAI)
            return Messages.TAI_NAME;
        if (result == TaiXiuResult.SPECIAL)
            return Messages.SPECIAL_NAME;
        if (result == TaiXiuResult.NONE)
            return Messages.NONE_NAME;
        return null;
    }

    public static String getCurrencyName(CurrencyTyppe currencyTyppe) {
        return TaiXiu.plugin.getConfig().getString("currency-settings.display-settings." + currencyTyppe.toString() + ".name");
    }

    public static String getCurrencySymbol(CurrencyTyppe currencyTyppe) {
        return TaiXiu.plugin.getConfig().getString("currency-settings.display-settings." + currencyTyppe.toString() + ".symbol");
    }

    public static void throwErrorMessage(String message) {
        Bukkit.getLogger().severe(message);
        log("&4&l[TAI XIU ERROR] &c&lNếu lỗi này ảnh hưởng đến trải nghiệm người chơi, liên hệ mình qua discord: Cortez_Romeo");
    }

    public static void sendBroadCast(String message) {
        if (message.equals(""))
            return;

        String resolved = message.replace("%prefix%", Messages.PREFIX);
        TextFormatter.Mode selectedMode = TextFormatter.mode();
        for (Player player : Bukkit.getOnlinePlayers())
            sendResolvedPlayerMessage(player, resolved, selectedMode);
    }

    public static void sendBroadCast(Component message) {
        if (message == null) return;
        for (Player player : Bukkit.getOnlinePlayers())
            sendComponent(player, message);
    }

    public static void log(String message) {
        Bukkit.getConsoleSender().sendMessage(TextFormatter.legacyComponent(message));
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        String resolved = message.replace("%prefix%", Messages.PREFIX);
        if (sender instanceof Player player) {
            sendResolvedPlayerMessage(player, resolved, false);
            return;
        }
        sender.sendMessage(TextFormatter.component(resolved));
    }

    public static void sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty())
            return;
        sendResolvedPlayerMessage(player, message.replace("%prefix%", Messages.PREFIX), false);
    }

    public static void sendLegacyMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        if (sender instanceof Player player) {
            sendResolvedPlayerMessage(player, message, true);
            return;
        }
        sender.sendMessage(TextFormatter.legacyComponent(message));
    }

    public static void sendComponent(CommandSender sender, Component component) {
        if (sender == null || component == null) return;
        if (sender instanceof Player player) {
            Runnable delivery = () -> player.sendMessage(component);
            if (Bukkit.isOwnedByCurrentRegion(player)) delivery.run();
            else TaiXiu.scheduler.runEntity(player, delivery);
            return;
        }
        sender.sendMessage(component);
    }

    private static void sendResolvedPlayerMessage(Player player, String message, boolean legacy) {
        TextFormatter.Mode selectedMode = legacy ? TextFormatter.Mode.LEGACY : TextFormatter.mode();
        sendResolvedPlayerMessage(player, message, selectedMode);
    }

    private static void sendResolvedPlayerMessage(Player player, String message, TextFormatter.Mode selectedMode) {
        Runnable delivery = () -> {
            player.sendMessage(resolvePlayerMessage(player, message, selectedMode));
        };
        if (Bukkit.isOwnedByCurrentRegion(player)) delivery.run();
        else TaiXiu.scheduler.runEntity(player, delivery);
    }

    public static Component resolvePlayerMessage(Player player, String message, TextFormatter.Mode selectedMode) {
        if (!TaiXiu.support.isPlaceholderAPISupported())
            return TextFormatter.component(message, selectedMode);
        return TextFormatter.componentWithUnparsedPlaceholders(message, selectedMode,
                token -> PlaceholderAPI.setPlaceholders(player, token));
    }

    // only use for testing plugin
    public static void devMessage(String message) {
        log("[DEV] " + message);
    }

    public static void devMessage(Player player, String message) {
        player.sendMessage("[DEV] " + message);
    }
}
