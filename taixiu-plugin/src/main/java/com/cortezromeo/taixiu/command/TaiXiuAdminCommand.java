package com.cortezromeo.taixiu.command;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.CurrencyTyppe;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.api.TaiXiuState;
import com.cortezromeo.taixiu.file.GeyserFormFile;
import com.cortezromeo.taixiu.file.inventory.TaiXiuInfoInventoryFile;
import com.cortezromeo.taixiu.language.Messages;
import com.cortezromeo.taixiu.manager.BossBarManager;
import com.cortezromeo.taixiu.manager.DatabaseManager;
import com.cortezromeo.taixiu.manager.TaiXiuManager;
import com.cortezromeo.taixiu.storage.SessionDataStorage;
import com.cortezromeo.taixiu.economy.VaultCurrencyGateway;
import com.cortezromeo.taixiu.economy.PlayerPointsCurrencyGateway;
import com.cortezromeo.taixiu.util.MessageUtil;
import com.cortezromeo.taixiu.util.TextFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.cortezromeo.taixiu.manager.DebugManager.setDebug;
import static com.cortezromeo.taixiu.util.MessageUtil.sendBroadCast;

public class TaiXiuAdminCommand implements CommandExecutor, TabExecutor {
    private TaiXiu plugin;

    public TaiXiuAdminCommand(TaiXiu plugin) {
        this.plugin = plugin;
        plugin.getCommand("taixiuadmin").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            if (!sender.hasPermission("taixiu.admin")) {
                sendMessage(sender, Messages.NO_PERMISSION);
                return false;
            }
        }

        if (!Bukkit.isGlobalTickThread()) {
            TaiXiu.scheduler.runGlobal(() -> executeCommand(sender, args));
            return true;
        }
        return executeCommand(sender, args);
    }

    private boolean executeCommand(CommandSender sender, String[] args) {

        if (args.length == 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "changestate":
                    if (TaiXiuManager.getState() == TaiXiuState.PLAYING) {
                        TaiXiuManager.setState(TaiXiuState.PAUSING);
                    } else {
                        if (!TaiXiuManager.isHealthy()) {
                            sendLegacyMessage(sender, "&cCannot resume while health-lock is active: "
                                    + TaiXiuManager.healthSummary());
                            return true;
                        }
                        TaiXiuManager.setState(TaiXiuState.PLAYING);
                    }
                    sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_CHANGESTATE.replace("%state%", TaiXiuManager.getState().toString()));
                    sendBroadCast(Messages.COMMAND_TAIXIUADMIN_CHANGESTATE_BROADCAST
                            .replace("%playerName%", sender.getName())
                            .replace("%state%", TaiXiuManager.getState().toString()));
                    return false;
                case "health":
                case "suckhoe":
                    sendLegacyMessage(sender, TaiXiuManager.isHealthy()
                            ? "&aTaiXiu health: HEALTHY"
                            : "&cTaiXiu health-lock: " + TaiXiuManager.healthSummary());
                    return true;
                case "reload":
                    TaiXiu.plugin.reloadConfig();
                    if (!TaiXiu.plugin.validateConfig()) {
                        TaiXiuManager.markUnhealthy("INVALID_CONFIGURATION");
                        sendLegacyMessage(sender, "&cInvalid config. TaiXiu has been paused; check the console.");
                        return true;
                    }
                    TextFormatter.configure(TaiXiu.plugin.getConfig().getString("text-format", "LEGACY"),
                            message -> TaiXiu.plugin.getLogger().warning(message));
                    Messages.setupValue(TaiXiu.plugin.getConfig().getString("locale"));
                    GeyserFormFile.reload();
                    if (!TaiXiu.plugin.getConfig().getBoolean("boss-bar.enabled")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            BossBarManager.remove(p);
                        }
                    } else
                        BossBarManager.setupValue();
                    TaiXiuInfoInventoryFile.reload();
                    TaiXiu.support.reloadConfigurableSupports();
                    if (TaiXiu.support.getVault() != null)
                        TaiXiu.currencies.register(CurrencyTyppe.VAULT,
                                new VaultCurrencyGateway(TaiXiu.support.getVault()));
                    else
                        TaiXiuManager.markUnhealthy("VAULT_PROVIDER_UNAVAILABLE");
                    if (TaiXiu.support.getPlayerPointsAPI() != null)
                        TaiXiu.currencies.register(CurrencyTyppe.PLAYERPOINTS,
                                new PlayerPointsCurrencyGateway(TaiXiu.support.getPlayerPointsAPI()));
                    else
                        TaiXiu.currencies.unregister(CurrencyTyppe.PLAYERPOINTS);
                    setDebug(TaiXiu.plugin.getConfig().getBoolean("debug"));
                    SessionDataStorage.reloadRetentionAsync().exceptionally(error -> {
                        TaiXiu.plugin.getLogger().severe("Could not reload database retention: " + error.getMessage());
                        return null;
                    });
                    sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_RELOAD);
                    return false;
                default:
                    sendMessage(sender, Messages.WRONG_ARGUMENT);
                    return false;
            }
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "settime":
                    try {
                        int time = Integer.parseInt(args[1]);

                        if (time <= 0) {
                            sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_INVALID_INT_INPUT);
                            return false;
                        }

                        TaiXiuManager.setTime(time);
                        sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_SETTIME.replace("%time%", String.valueOf(time)));
                        sendBroadCast(Messages.COMMAND_TAIXIUADMIN_SETTIME_BROADCAST
                                .replace("%playerName%", sender.getName())
                                .replace("%time%", String.valueOf(time)));
                    } catch (Exception e) {
                        sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_INVALID_INT_INPUT);
                    }
                    return false;
                case "setcurrency":
                    try {
                        CurrencyTyppe currencyTyppe = CurrencyTyppe.valueOf(args[1].toUpperCase(Locale.ROOT));
                        if (TaiXiuManager.hasPendingBets()
                                || !TaiXiuManager.getSessionData().getTaiPlayerSnapshot().isEmpty()
                                || !TaiXiuManager.getSessionData().getXiuPlayerSnapshot().isEmpty()) {
                            sendLegacyMessage(sender, "&cCurrency cannot be changed after betting has started.");
                            return true;
                        }
                        if (currencyTyppe == CurrencyTyppe.PLAYERPOINTS)
                            if (TaiXiu.support.getPlayerPointsAPI() == null) {
                                sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_UNSUPPORTED_CURRENCY.replace("%currency%", currencyTyppe.toString()));
                                return false;
                            }
                        TaiXiuManager.setCurrencyType(currencyTyppe);
                        sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_SETCURRENCY
                                .replace("%currency%", String.valueOf(currencyTyppe))
                                .replace("%currencyName%", MessageUtil.getCurrencyName(currencyTyppe)));
                        sendBroadCast(Messages.COMMAND_TAIXIUADMIN_SETCURRENCY_BROADCAST
                                .replace("%playerName%", sender.getName())
                                .replace("%currency%", String.valueOf(currencyTyppe))
                                .replace("%currencyName%", MessageUtil.getCurrencyName(currencyTyppe)));
                    } catch (Exception e) {
                        sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_SETCURRENCY_INVALID);
                    }
                    return false;
                case "transaction":
                case "giaodich":
                    if (!isAlias(args[1], "list", "danhsach")) {
                        sendLegacyMessage(sender, "&cUsage: /taixiuadmin transaction|giaodich list|danhsach");
                        return true;
                    }
                    listTransactions(sender, 1, null);
                    return true;
                case "health":
                case "suckhoe":
                    if (!isAlias(args[1], "acknowledge", "xacnhan")) {
                        sendLegacyMessage(sender, "&cUsage: /taixiuadmin health|suckhoe acknowledge|xacnhan");
                        return true;
                    }
                    TaiXiuManager.acknowledgeHealth(sender.getName());
                    sendLegacyMessage(sender, "&eHealth-lock cleared. Use changestate to resume after verifying providers/database.");
                    return true;
                default:
                    sendMessage(sender, Messages.WRONG_ARGUMENT);
                    return false;
            }
        }

        if (args.length >= 3 && isAlias(args[0], "transaction", "giaodich")
                && isAlias(args[1], "list", "danhsach")) {
            try {
                int page = Math.max(1, Integer.parseInt(args[2]));
                String status = args.length > 3 ? args[3].toUpperCase(Locale.ROOT) : null;
                listTransactions(sender, page, status);
            } catch (NumberFormatException exception) {
                sendLegacyMessage(sender, "&cUsage: /taixiuadmin transaction|giaodich list|danhsach [page] [status]");
            }
            return true;
        }

        if (args.length >= 4 && isAlias(args[0], "transaction", "giaodich")) {
            if (!isAlias(args[3], "confirm", "xacnhan")) {
                sendLegacyMessage(sender, "&cReconciliation changes money/state. Append confirm|xacnhan to execute it.");
                return true;
            }
            String reason = args.length > 4
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length))
                    : "Manual reconciliation via command";
            TaiXiuManager.reconcileTransaction(args[1], normalizeAction(args[2]), sender.getName(), reason)
                    .whenComplete((result, error) -> TaiXiu.scheduler.runGlobal(() -> sendLegacyMessage(sender,
                            error == null ? "&e" + result : "&c" + error.getMessage())));
            return true;
        }

        if (args.length == 3 && isAlias(args[0], "transaction", "giaodich")) {
            sendLegacyMessage(sender, "&cUsage: /taixiuadmin transaction|giaodich <id> <action> confirm|xacnhan [reason]");
            return true;
        }

        if (args.length == 4) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "setresult":
                    if (TaiXiuManager.getSessionData().getResult() != TaiXiuResult.NONE) {
                        sendLegacyMessage(sender, "%prefix%&ePlease wait a few seconds before you can use this command again!");
                        return false;
                    }
                    try {
                        int dice1 = Integer.parseInt(args[1]);
                        int dice2 = Integer.parseInt(args[2]);
                        int dice3 = Integer.parseInt(args[3]);

                        if (dice1 < 0 || dice2 <0 || dice3 < 0 || dice1 > 6 || dice2 > 6 || dice3 > 6) {
                            sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_INVALID_DICE_INPUT);
                            return false;
                        }

                        var session = TaiXiuManager.getSessionData();
                        TaiXiuManager.resultSeasonAsync(session, dice1, dice2, dice3).whenComplete((settled, error) ->
                                TaiXiu.scheduler.runGlobal(() -> {
                                    if (error != null || !Boolean.TRUE.equals(settled)) {
                                        sendLegacyMessage(sender, "&cCould not settle the session; TaiXiu has been paused. Check the console.");
                                        return;
                                    }
                                    sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_SETRESULT
                                            .replace("%dice1%", String.valueOf(session.getDice1()))
                                            .replace("%dice2%", String.valueOf(session.getDice2()))
                                            .replace("%dice3%", String.valueOf(session.getDice3())));
                                    sendBroadCast(Messages.COMMAND_TAIXIUADMIN_SETRESULT_BROADCAST
                                            .replace("%playerName%", sender.getName())
                                            .replace("%dice1%", String.valueOf(session.getDice1()))
                                            .replace("%dice2%", String.valueOf(session.getDice2()))
                                            .replace("%dice3%", String.valueOf(session.getDice3())));
                                }));
                    } catch (Exception e) {
                        sendMessage(sender, Messages.COMMAND_TAIXIUADMIN_INVALID_DICE_INPUT);
                    }
                    return false;
                default:
                    sendMessage(sender, Messages.WRONG_ARGUMENT);
                    return false;
            }
        }

        for (String string : Messages.COMMAND_TAIXIUADMIN) {
            string = string.replace("%version%", TaiXiu.plugin.getDescription().getVersion());
            sendMessage(sender, string);
        }
        return false;
    }

    public void sendMessage(CommandSender sender, String message) {
        MessageUtil.sendComponent(sender, TextFormatter.component(
                message.replace("%prefix%", Messages.COMMAND_TAIXIUADMIN_PREFIX)));
    }

    private void sendLegacyMessage(CommandSender sender, String message) {
        boolean prefixed = message.contains("%prefix%");
        String body = message.replace("%prefix%", "");
        var component = prefixed
                ? TextFormatter.component(Messages.COMMAND_TAIXIUADMIN_PREFIX)
                    .append(TextFormatter.legacyComponent(body))
                : TextFormatter.legacyComponent(body);
        MessageUtil.sendComponent(sender, component);
    }

    private void listTransactions(CommandSender sender, int page, String status) {
        SessionDataStorage.unresolvedJournalAsync().whenComplete((entries, error) -> TaiXiu.scheduler.runGlobal(() -> {
            if (error != null) {
                sendLegacyMessage(sender, "&cCould not load transactions: " + error.getMessage());
                return;
            }
            var filtered = entries.stream()
                    .filter(entry -> status == null || entry.status().equalsIgnoreCase(status)).toList();
            int pageSize = 20;
            int pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
            int safePage = Math.min(page, pages);
            sendLegacyMessage(sender, "&eUnresolved transactions: " + filtered.size() + " (page " + safePage + "/" + pages + ")");
            filtered.stream().skip((long) (safePage - 1) * pageSize).limit(pageSize).forEach(entry -> sendLegacyMessage(sender,
                    "&7" + entry.id() + " &f" + entry.kind() + " &e" + entry.status()
                            + " &7" + entry.playerName() + " " + entry.amount() + " &8" + entry.context()));
        }));
    }

    private static boolean isAlias(String input, String english, String vietnamese) {
        return input.equalsIgnoreCase(english) || input.equalsIgnoreCase(vietnamese);
    }

    private static String normalizeAction(String action) {
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "hoantat" -> "complete";
            case "thatbai" -> "fail";
            case "hoantien" -> "refund";
            case "thulai" -> "retry";
            default -> action;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("taixiu.admin")) {
                commands.add("reload");
                commands.add("changestate");
                commands.add("settime");
                commands.add("setcurrency");
                commands.add("setresult");
                commands.add("transaction");
                commands.add("giaodich");
                commands.add("health");
                commands.add("suckhoe");
            }
            StringUtil.copyPartialMatches(args[0], commands, completions);
        }
        if (args.length == 2) {
            if (sender.hasPermission("taixiu.admin")) {
                if (args[0].equalsIgnoreCase("setcurrency")) {
                    commands.add("VAULT");
                    commands.add("PLAYERPOINTS");
                }
                if (isAlias(args[0], "transaction", "giaodich")) commands.addAll(List.of("list", "danhsach"));
                if (isAlias(args[0], "health", "suckhoe")) commands.addAll(List.of("acknowledge", "xacnhan"));
                StringUtil.copyPartialMatches(args[1], commands, completions);
            }
        }
        if (args.length == 3 && isAlias(args[0], "transaction", "giaodich")
                && !isAlias(args[1], "list", "danhsach")) {
            commands.addAll(List.of("complete", "hoantat", "fail", "thatbai",
                    "refund", "hoantien", "retry", "thulai"));
            StringUtil.copyPartialMatches(args[2], commands, completions);
        }
        if (args.length == 4 && isAlias(args[0], "transaction", "giaodich")
                && !isAlias(args[1], "list", "danhsach")) {
            commands.addAll(List.of("confirm", "xacnhan"));
            StringUtil.copyPartialMatches(args[3], commands, completions);
        }
        Collections.sort(completions);
        return completions;
    }

}
