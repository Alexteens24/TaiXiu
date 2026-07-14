package com.cortezromeo.taixiu.support.version.cross;

import com.cortezromeo.taixiu.api.server.VersionSupport;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bukkit.ChatColor.COLOR_CHAR;

public class CrossVersionSupport extends VersionSupport {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)&[0-9A-FK-OR]");
    private final NamespacedKey customDataKey;

    public CrossVersionSupport(Plugin plugin) {
        super(plugin);
        this.customDataKey = new NamespacedKey(plugin, "custom_data");
    }

    @Override
    public ItemStack createItemStack(String material, int amount, short data) {
        Material matched = Material.matchMaterial(material);
        if (matched == null || matched.isAir()) {
                    getPlugin().getLogger().severe("----------------------------------------------------");
                    getPlugin().getLogger().severe("INVALID MATERIAL: " + material);
                    getPlugin().getLogger().severe("The material name may be incorrect or not available in this server version.");
                    getPlugin().getLogger().severe(">> Reference Material List <<");
                    getPlugin().getLogger().severe("https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html");
                    getPlugin().getLogger().severe("----------------------------------------------------");
            matched = Material.BEDROCK;
        }
        return new ItemStack(matched, Math.max(1, amount));
    }

    @Override
    public Sound createSound(String soundName) {
        String key = soundName.toLowerCase(Locale.ROOT).replace('_', '.');
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
        if (sound == null) {
            getPlugin().getLogger().severe("----------------------------------------------------");
            getPlugin().getLogger().severe("INVALID SOUND NAME: " + soundName);
            getPlugin().getLogger().severe("The sound name may be incorrect or not supported in this server version.");
            getPlugin().getLogger().severe(">> Reference Sound List <<");
            getPlugin().getLogger().severe("https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html");
            getPlugin().getLogger().severe("----------------------------------------------------");
            return Registry.SOUNDS.get(NamespacedKey.minecraft("block.amethyst_cluster.break"));
        }
        return sound;
    }

    @Override
    public ItemStack getHeadItemFromBase64(String headValue) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.getServer().createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", headValue));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack getHeadItemFromPlayerName(String playerName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        org.bukkit.entity.Player online = Bukkit.getPlayerExact(playerName);
        PlayerProfile profile = online == null
                ? Bukkit.getServer().createProfile(playerName)
                : Bukkit.getServer().createProfile(online.getUniqueId(), online.getName());
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public ItemStack addCustomData(ItemStack itemStack, String data) {
        if (itemStack == null)
            return null;

        if (itemStack.getType() == Material.AIR)
            return null;

        ItemMeta meta = itemStack.getItemMeta();
        meta.getPersistentDataContainer().set(customDataKey, PersistentDataType.STRING, data);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    @Override
    public String getCustomData(ItemStack itemStack) {
        if (itemStack == null)
            return null;

        if (itemStack.getType() == Material.AIR)
            return null;

        ItemMeta meta = itemStack.getItemMeta();
        return meta.getPersistentDataContainer().get(customDataKey, PersistentDataType.STRING);
    }

    @Override
    public String addColor(String textToTranslate) {
        if (textToTranslate == null)
            return "NULL";

        Matcher matcher = HEX_PATTERN.matcher(textToTranslate);
        StringBuilder buffer = new StringBuilder(textToTranslate.length() + 4 * 8);
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, COLOR_CHAR + "x"
                    + COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
                    + COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
                    + COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5)
            );
        }
        String hexTranslated = matcher.appendTail(buffer).toString();

        return ChatColor.translateAlternateColorCodes('&', hexTranslated);
    }

    @Override
    public String stripColor(String textToStrip) {
        return textToStrip == null ? null : STRIP_COLOR_PATTERN.matcher(textToStrip).replaceAll("");
    }
}
