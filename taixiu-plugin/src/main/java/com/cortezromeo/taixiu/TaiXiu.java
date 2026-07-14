package com.cortezromeo.taixiu;

import com.cortezromeo.taixiu.api.server.VersionSupport;
import com.cortezromeo.taixiu.command.TaiXiuAdminCommand;
import com.cortezromeo.taixiu.command.TaiXiuCommand;
import com.cortezromeo.taixiu.file.GeyserFormFile;
import com.cortezromeo.taixiu.economy.CurrencyService;
import com.cortezromeo.taixiu.economy.PlayerPointsCurrencyGateway;
import com.cortezromeo.taixiu.economy.VaultCurrencyGateway;
import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.file.inventory.TaiXiuInfoInventoryFile;
import com.cortezromeo.taixiu.language.English;
import com.cortezromeo.taixiu.language.Messages;
import com.cortezromeo.taixiu.language.Vietnamese;
import com.cortezromeo.taixiu.listener.InventoryClickListener;
import com.cortezromeo.taixiu.listener.PlayerJoinListener;
import com.cortezromeo.taixiu.listener.PlayerQuitListener;
import com.cortezromeo.taixiu.manager.BossBarManager;
import com.cortezromeo.taixiu.manager.DatabaseManager;
import com.cortezromeo.taixiu.manager.TaiXiuManager;
import com.cortezromeo.taixiu.scheduler.SchedulerService;
import com.cortezromeo.taixiu.storage.SessionDataStorage;
import com.cortezromeo.taixiu.support.Support;
import com.cortezromeo.taixiu.support.version.cross.CrossVersionSupport;
import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Set;

import static com.cortezromeo.taixiu.manager.DebugManager.setDebug;
import static com.cortezromeo.taixiu.util.MessageUtil.log;

public final class TaiXiu extends JavaPlugin {

    public static TaiXiu plugin;
    public static VersionSupport nms;
    public static Support support;
    public static SchedulerService scheduler;
    public static CurrencyService currencies;
    private boolean databaseInitialized;

    @Override
    public void onLoad() {
        plugin = this;
        scheduler = new SchedulerService(this);
        nms = new CrossVersionSupport(plugin);
    }
    @Override
    public void onEnable() {
        initFile();
        if (!validateConfig()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        initLanguages();
        setDebug(getConfig().getBoolean("debug"));

        support = new Support();
        if (!support.setupSupports()) {
            getLogger().severe("TaiXiu could not start because a required integration is unavailable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        currencies = new CurrencyService();
        currencies.register(CurrencyTyppe.VAULT, new VaultCurrencyGateway(support.getVault()));
        if (support.getPlayerPointsAPI() != null)
            currencies.register(CurrencyTyppe.PLAYERPOINTS, new PlayerPointsCurrencyGateway(support.getPlayerPointsAPI()));

        initDatabase();
        TaiXiuManager.beginStartup();
        TaiXiuManager.recoverPendingTransactions();
        initCommand();
        initListener();

        TaiXiuManager.startTask(getConfig().getInt("task.taiXiuTask.time-per-session"));
        BossBarManager.setupValue();

        log("&f--------------------------------");
        log("&2  _____           _    __  __  _         ");
        log("&2 |_   _|   __ _  (_)   \\ \\/ / (_)  _   _ ");
        log("&2   | |    / _  | | |    \\  /  | | | | | |");
        log("&2   | |   | (_| | | |    /  \\  | | | |_| |");
        log("&2   |_|    \\____| |_|   /_/\\_\\ |_|  \\____|");
        log("");
        log("&fVersion: &b" + getDescription().getVersion());
        log("&fAuthor: &bCortez_Romeo");
        log("&eRunning version: " + Bukkit.getServer().getClass().getName().split("\\.")[3]);
        if (scheduler.isFolia())
            log("      &2&lFOLIA SUPPORTED");
        log("");
        log("&fSupport:");
        log((support.isVaultSupported() ? "&2[SUPPORTED] &aVault" : "&4[UNSUPPORTED] &cVault"));
        log((support.isPlaceholderAPISupported() ? "&2[SUPPORTED] &aPlaceholderAPI" : "&4[UNSUPPORTED] &cPlaceholderAPI"));
        log((support.isFloodgateSupported() ? "&2[SUPPORTED] &aFloodgate API (Forms and Cumulus)" : "&4[UNSUPPORTED] &cFloodgate API (Forms and Cumulus)"));
        if (!getConfig().getBoolean("floodgate-settings.enabled"))
            log("  &e&oquyền sử dụng Floodgate API đã bị tắt trong config.yml");
        log((support.isPlayerPointsSupported() ? "&2[SUPPORTED] &aPlayerPoints" : "&4[UNSUPPORTED] &cPlayerPoints"));
        log("");
        log("&f--------------------------------");

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (getConfig().getBoolean("toggle-settings.auto-toggle")) {
                if (!DatabaseManager.togglePlayers.contains(p.getName())) {
                    DatabaseManager.togglePlayers.add(p.getName());
                    BossBarManager.toggleBossBar(p);
                }
            }
        }

        if (getConfig().getBoolean("bStats.enabled", true)) new Metrics(this, 21630);
    }

    private void initFile() {
        File inventoryFolder = new File(getDataFolder() + "/inventories");
        if (!inventoryFolder.exists())
            inventoryFolder.mkdirs();

        File languageFolder = new File(getDataFolder() + "/languages");
        if (!languageFolder.exists())
            languageFolder.mkdirs();

        // config.yml
        File configFile = new File(getDataFolder(), "config.yml");
        boolean existingConfig = configFile.isFile();
        YamlConfiguration oldConfig = existingConfig ? YamlConfiguration.loadConfiguration(configFile) : null;
        boolean hadRetention = oldConfig != null && oldConfig.contains("database.retention.mode");
        String legacyDisablingMode = oldConfig == null ? "SAVE_LATEST" :
                oldConfig.getString("database.while-disabling-plugin.type", "SAVE_LATEST");
        saveDefaultConfig();
        try {
            ConfigUpdater.update(this, "config.yml", configFile);
        } catch (IOException e) {
            throw new IllegalStateException("Could not update config.yml", e);
        }
        reloadConfig();
        if (existingConfig && !hadRetention) {
            boolean latestOnly = "SAVE_LATEST_AND_DELETE_OLD_DATA".equalsIgnoreCase(legacyDisablingMode);
            getConfig().set("database.retention.mode", latestOnly ? "COUNT" : "ALL");
            if (latestOnly) getConfig().set("database.retention.max-sessions", 1);
            saveConfig();
            getLogger().info("Migrated legacy session retention settings to SQLite retention mode "
                    + getConfig().getString("database.retention.mode") + ".");
        }

        // inventories/sessioninfoinventory.yml
        String taiXiuInfoInventoryFileName = "sessioninfoinventory.yml";
        TaiXiuInfoInventoryFile.setup();
        TaiXiuInfoInventoryFile.saveDefault();
        File taiXiuInfoInventoryFile = new File(getDataFolder() + "/inventories/sessioninfoinventory.yml");
        try {
            ConfigUpdater.update(this, taiXiuInfoInventoryFileName, taiXiuInfoInventoryFile);
        } catch (IOException e) {
            throw new IllegalStateException("Could not update sessioninfoinventory.yml", e);
        }
        TaiXiuInfoInventoryFile.reload();

        // geyserform.yml
        if (!new File(getDataFolder() + "/geyserform.yml").exists()) {
            GeyserFormFile.setup();
            GeyserFormFile.setupLang();
        } else
            GeyserFormFile.fileExists();
        GeyserFormFile.reload();

        // discordsrv-result-message.json
        File resultJsonFile = new File(getDataFolder(), "discordsrv-result-message.json");
        if (!resultJsonFile.exists()) saveResource(resultJsonFile.getName(), false);

        // discordsrv-playerbet-message.json
        File playerBetJsonFile = new File(getDataFolder(), "discordsrv-playerbet-message.json");
        if (!playerBetJsonFile.exists()) saveResource(playerBetJsonFile.getName(), false);
    }

    public boolean validateConfig() {
        boolean valid = true;
        String retention = getConfig().getString("database.retention.mode", "ALL").toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "DAYS", "COUNT").contains(retention)) {
            getLogger().severe("database.retention.mode must be ALL, DAYS, or COUNT");
            valid = false;
        }
        if ("DAYS".equals(retention) && getConfig().getLong("database.retention.days") < 1) {
            getLogger().severe("database.retention.days must be at least 1");
            valid = false;
        }
        if ("COUNT".equals(retention) && getConfig().getLong("database.retention.max-sessions") < 1) {
            getLogger().severe("database.retention.max-sessions must be at least 1");
            valid = false;
        }
        if (getConfig().getInt("database.history-cache-size", 256) < 1) {
            getLogger().severe("database.history-cache-size must be at least 1");
            valid = false;
        }
        if (getConfig().getLong("database.journal-retention-days", 90) < 1) {
            getLogger().severe("database.journal-retention-days must be at least 1");
            valid = false;
        }
        if (getConfig().getInt("discord-webhook-settings.queue-capacity", 256) < 16) {
            getLogger().severe("discord-webhook-settings.queue-capacity must be at least 16");
            valid = false;
        }
        try {
            CurrencyTyppe.valueOf(getConfig().getString("currency-settings.default", "VAULT").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            getLogger().severe("currency-settings.default must be VAULT or PLAYERPOINTS");
            valid = false;
        }
        int duration = getConfig().getInt("task.taiXiuTask.time-per-session");
        int disableAt = getConfig().getInt("bet-settings.disable-while-remaining");
        long minBet = getConfig().getLong("bet-settings.min-bet");
        long maxBet = getConfig().getLong("bet-settings.max-bet");
        double tax = getConfig().getDouble("bet-settings.tax");
        if (duration < 1 || disableAt < 0 || disableAt >= duration) {
            getLogger().severe("Session time must be positive and disable-while-remaining must be within the session");
            valid = false;
        }
        if (minBet < 1 || maxBet < minBet) {
            getLogger().severe("Bet limits must satisfy 1 <= min-bet <= max-bet");
            valid = false;
        }
        if (!Double.isFinite(tax) || tax < 0 || tax > 100) {
            getLogger().severe("bet-settings.tax must be between 0 and 100");
            valid = false;
        }
        try {
            new DecimalFormat(getConfig().getString("format-money", "#,###"));
        } catch (IllegalArgumentException exception) {
            getLogger().severe("format-money is not a valid DecimalFormat pattern");
            valid = false;
        }
        try {
            BarColor.valueOf(getConfig().getString("boss-bar.type.playing.color.playing", "YELLOW").toUpperCase(Locale.ROOT));
            BarColor.valueOf(getConfig().getString("boss-bar.type.playing.color.bet-disabling", "BLUE").toUpperCase(Locale.ROOT));
            BarColor.valueOf(getConfig().getString("boss-bar.type.playing.color.pausing", "BLUE").toUpperCase(Locale.ROOT));
            BarStyle.valueOf(getConfig().getString("boss-bar.type.playing.style", "SOLID").toUpperCase(Locale.ROOT));
            BarStyle.valueOf(getConfig().getString("boss-bar.type.reloading.style", "SOLID").toUpperCase(Locale.ROOT));
            String resultColor = getConfig().getString("boss-bar.type.reloading.color", "RESULT-COLOR");
            if ("RESULT-COLOR".equalsIgnoreCase(resultColor)) {
                BarColor.valueOf(getConfig().getString("boss-bar.type.reloading.result-color-setting.xiu", "GREEN").toUpperCase(Locale.ROOT));
                BarColor.valueOf(getConfig().getString("boss-bar.type.reloading.result-color-setting.tai", "RED").toUpperCase(Locale.ROOT));
            } else {
                BarColor.valueOf(resultColor.toUpperCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException exception) {
            getLogger().severe("One or more boss-bar colors/styles are invalid: " + exception.getMessage());
            valid = false;
        }
        if (getConfig().getDouble("boss-bar.type.reloading.time") < 0) {
            getLogger().severe("boss-bar.type.reloading.time cannot be negative");
            valid = false;
        }
        String databaseName = getConfig().getString("database.file", "taixiu.db");
        Path dataPath = getDataFolder().toPath().toAbsolutePath().normalize();
        Path databasePath = dataPath.resolve(databaseName).normalize();
        if (Path.of(databaseName).isAbsolute() || !databasePath.startsWith(dataPath)) {
            getLogger().severe("database.file must stay inside the TaiXiu data folder");
            valid = false;
        }
        return valid;
    }

    public void initLanguages() {
        // messages_vi.yml
        String vietnameseFileName = "messages_vi.yml";
        Vietnamese.setup();
        Vietnamese.saveDefault();
        File vietnameseFile = new File(getDataFolder(), "/languages/messages_vi.yml");
        try {
            ConfigUpdater.update(this, vietnameseFileName, vietnameseFile);
        } catch (IOException e) {
            throw new IllegalStateException("Could not update messages_vi.yml", e);
        }
        Vietnamese.reload();

        // messages_en.yml
        String englishFileName = "messages_en.yml";
        English.setup();
        English.saveDefault();
        File englishFile = new File(getDataFolder(), "/languages/messages_en.yml");
        try {
            ConfigUpdater.update(this, englishFileName, englishFile);
        } catch (IOException e) {
            throw new IllegalStateException("Could not update messages_en.yml", e);
        }
        English.reload();

        // load locale from config.yml
        Messages.setupValue(getConfig().getString("locale"));
    }

    private void initDatabase() {
        SessionDataStorage.init();
        databaseInitialized = true;
    }

    private void initCommand() {
        new TaiXiuCommand(this);
        new TaiXiuAdminCommand(this);
    }

    private void initListener() {
        new InventoryClickListener(this);
        new PlayerJoinListener(this);
        new PlayerQuitListener(this);
    }

    @Override
    public void onDisable() {
        log("&f--------------------------------");
        log("&c  _____           _    __  __  _         ");
        log("&c |_   _|   __ _  (_)   \\ \\/ / (_)  _   _ ");
        log("&c   | |    / _  | | |    \\  /  | | | | | |");
        log("&c   | |   | (_| | | |    /  \\  | | | |_| |");
        log("&c   |_|    \\____| |_|   /_/\\_\\ |_|  \\____|");
        log("");
        log("&fVersion: &b" + getDescription().getVersion());
        log("&fAuthor: &bCortez_Romeo");
        log("&f--------------------------------");

        TaiXiuManager.beginShutdown();
        if (scheduler != null) scheduler.cancelAll();

        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBarManager.remove(p);
        }
        if (support != null) support.close();
        if (databaseInitialized)
            SessionDataStorage.flushAndClose(DatabaseManager.activeSnapshots());
    }
}
