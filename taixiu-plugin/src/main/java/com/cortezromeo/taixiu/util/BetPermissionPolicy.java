package com.cortezromeo.taixiu.util;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Collection;

public final class BetPermissionPolicy {
    private static final String MAX_BET_PREFIX = "taixiu.maxbet.";
    private static final String TAX_DISCOUNT_PREFIX = "taixiu.tax.discount.";

    private BetPermissionPolicy() { }

    public static long effectiveMaxBet(Player player, long configuredMax) {
        return effectiveMaxBet(enabledPermissions(player), configuredMax);
    }

    public static double effectiveTax(Player player, double configuredTax) {
        if (player.hasPermission("taixiu.tax.bypass")) return 0;
        return effectiveTax(enabledPermissions(player), configuredTax);
    }

    static long effectiveMaxBet(Collection<String> permissions, long configuredMax) {
        long selected = -1;
        for (String permission : permissions) {
            if (!permission.startsWith(MAX_BET_PREFIX)) continue;
            try {
                long value = Long.parseLong(permission.substring(MAX_BET_PREFIX.length()));
                if (value > 0) selected = Math.max(selected, value);
            } catch (NumberFormatException ignored) { }
        }
        return selected > 0 ? selected : configuredMax;
    }

    static double effectiveTax(Collection<String> permissions, double configuredTax) {
        double discount = 0;
        for (String permission : permissions) {
            if (!permission.startsWith(TAX_DISCOUNT_PREFIX)) continue;
            try {
                double value = Double.parseDouble(permission.substring(TAX_DISCOUNT_PREFIX.length()));
                if (Double.isFinite(value) && value > 0 && value <= 100) discount = Math.max(discount, value);
            } catch (NumberFormatException ignored) { }
        }
        return Math.max(0, configuredTax - discount);
    }

    private static Collection<String> enabledPermissions(Player player) {
        return player.getEffectivePermissions().stream().filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission).toList();
    }
}
