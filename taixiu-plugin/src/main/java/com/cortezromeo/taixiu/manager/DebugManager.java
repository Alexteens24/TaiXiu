package com.cortezromeo.taixiu.manager;

import com.cortezromeo.taixiu.util.TextFormatter;
import org.bukkit.Bukkit;

public class DebugManager {

    public static boolean debug;

    public static boolean getDebug() {
        return debug;
    }

    public static void setDebug(boolean b) {
        debug = b;
    }

    public static void debug(String prefix, String message) {
        if (!debug)
            return;
        Bukkit.getConsoleSender().sendMessage(TextFormatter.legacyComponent(
                "&6[TAI XIU DEBUG] &e" + prefix.toUpperCase() + " >>> " + message));
    }
}
