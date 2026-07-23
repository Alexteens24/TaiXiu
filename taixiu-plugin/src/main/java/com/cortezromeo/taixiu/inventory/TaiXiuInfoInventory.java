package com.cortezromeo.taixiu.inventory;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.api.storage.ISession;
import com.cortezromeo.taixiu.file.inventory.TaiXiuInfoInventoryFile;
import com.cortezromeo.taixiu.language.Messages;
import com.cortezromeo.taixiu.manager.TaiXiuManager;
import com.cortezromeo.taixiu.util.ItemUtil;
import com.cortezromeo.taixiu.util.MessageUtil;
import com.cortezromeo.taixiu.util.TextFormatter;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TaiXiuInfoInventory extends PaginatedInventory {

    private List<ItemStack> inventoryItems = new ArrayList<>();
    private List<String> betPlayers = new ArrayList<>();
    private HashMap<String, Long> xiuPlayers = new HashMap<>();
    private HashMap<String, Long> taiPlayers = new HashMap<>();
    private SortItemsType sortItemsType;
    private ISession sessionData;
    private ScheduledTask wrappedTask;

    public TaiXiuInfoInventory(Player owner, ISession sessionData) {
        super(owner);
        sortItemsType = SortItemsType.all;
        this.sessionData = sessionData;
    }

    @Override
    public void open() {
        if (getInventory() == null || !getInventory().getViewers().contains(getOwner()))
            super.open();
        else {
            setMenuItems();
            getOwner().updateInventory();
        }

        if (wrappedTask == null) {
            if (getSessionData().getResult() != TaiXiuResult.NONE)
                return;

            wrappedTask = TaiXiu.scheduler.runEntityTimer(getOwner(), task -> {
                wrappedTask = task;
                if (getInventory().getViewers().isEmpty() || !getOwner().getOpenInventory().getTopInventory().equals(getInventory())) {
                    task.cancel();
                    return;
                }
                open();
            }, 20, 20);
        }
    }

    @Override
    public String getMenuName() {
        String title = TaiXiuInfoInventoryFile.get().getString("title");
        title = title.replace("%session%", String.valueOf(getSessionData().getSession()));
        return TextFormatter.legacy(title);
    }

    @Override
    public int getSlots() {
        return 54;
    }

    public ISession getSessionData() {
        return sessionData;
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) {
            return;
        }

        ItemStack itemStack = event.getCurrentItem();
        String itemeCustomData = TaiXiu.nms.getCustomData(itemStack);

        if (itemeCustomData == null) return;
        if (itemeCustomData.equals("prevPage")) {
            if (page != 0){
                page = page - 1;
                open();
            }
        }
        if (itemeCustomData.equals("nextPage")) {
            if (!((index + 1) >= betPlayers.size())) {
                page = page + 1;
                open();
            } else {
                MessageUtil.sendMessage(getOwner(), Messages.ALREADY_ON_LAST_PAGE);
            }
        }
        if (itemeCustomData.equals("closeItem"))
            getOwner().closeInventory();
        if (itemeCustomData.equals("sortItemsItem")) {
            if (sortItemsType == SortItemsType.all)
                sortItemsType = SortItemsType.taiPlayers;
            else if (sortItemsType == SortItemsType.taiPlayers)
                sortItemsType = SortItemsType.xiuPlayers;
            else if (sortItemsType == SortItemsType.xiuPlayers)
                sortItemsType = SortItemsType.highestCurrency;
            else if (sortItemsType == SortItemsType.highestCurrency)
                sortItemsType = SortItemsType.lowestCurrency;
            else if (sortItemsType == SortItemsType.lowestCurrency)
                sortItemsType = SortItemsType.all;
            inventoryItems.clear();
            super.open();
        }
    }

    @Override
    public void setMenuItems() {
        TaiXiu.scheduler.runEntity(getOwner(), () -> {
            addPaginatedMenuItems();
            FileConfiguration invFileConfig = TaiXiuInfoInventoryFile.get();

            ItemStack sessionInfoItem = TaiXiu.nms.addCustomData(
                    ItemUtil.getItem(invFileConfig.getString("items.sessionInfo.type"),
                            invFileConfig.getString("items.sessionInfo.value"),
                            (short) invFileConfig.getInt("items.sessionInfo.data"),
                            invFileConfig.getString("items.sessionInfo.name"),
                            getSessionData().getResult() == TaiXiuResult.NONE
                                    ? invFileConfig.getStringList("items.sessionInfo.lore.playing")
                                    : invFileConfig.getStringList("items.sessionInfo.lore.ending"),
                            sessionReplacements(getSessionData())), "sessionInfoItem");
            int sessionInfoItemSlot = invFileConfig.getInt("items.sessionInfo.slot");
            if (sessionInfoItemSlot < 0)
                sessionInfoItemSlot = 0;
            if (sessionInfoItemSlot > 8)
                sessionInfoItemSlot = 8;
            sessionInfoItemSlot = 45 + sessionInfoItemSlot;

            inventory.setItem(sessionInfoItemSlot, sessionInfoItem);

            ItemStack sortItemsItem = TaiXiu.nms.addCustomData(ItemUtil.getItem(invFileConfig.getString("items.sortItems.type"),
                    invFileConfig.getString("items.sortItems.value"),
                    (short) invFileConfig.getInt("items.sortItems.data"),
                    invFileConfig.getString("items.sortItems.name"),
                    invFileConfig.getStringList("items.sortItems.lore." + sortItemsType.toString())), "sortItemsItem");
            int sortItemsItemSlot = invFileConfig.getInt("items.sortItems.slot");
            if (sortItemsItemSlot < 0)
                sortItemsItemSlot = 0;
            if (sortItemsItemSlot > 8)
                sortItemsItemSlot = 8;
            sortItemsItemSlot = 45 + sortItemsItemSlot;
            inventory.setItem(sortItemsItemSlot, sortItemsItem);

            this.betPlayers.clear();
            this.xiuPlayers.clear();
            this.taiPlayers.clear();

            //
            Map<String, Long> xiuPlayersFromSession = getSessionData().getXiuPlayerSnapshot();
            if (!xiuPlayersFromSession.isEmpty()) {
                for (String xiuPlayer : xiuPlayersFromSession.keySet()) {
                    if (sortItemsType != SortItemsType.taiPlayers) {
                        xiuPlayers.put(xiuPlayer, xiuPlayersFromSession.get(xiuPlayer));
                        betPlayers.add(xiuPlayer);
                    }
                }
            }
            Map<String, Long> taiPlayersFromSession = getSessionData().getTaiPlayerSnapshot();
            if (!taiPlayersFromSession.isEmpty()) {
                for (String taiPlayer : taiPlayersFromSession.keySet()) {
                    if (sortItemsType != SortItemsType.xiuPlayers) {
                        this.betPlayers.add(taiPlayer);
                        this.taiPlayers.put(taiPlayer, taiPlayersFromSession.get(taiPlayer));
                    }
                }
            }

            if (!betPlayers.isEmpty()) {
                if (sortItemsType == SortItemsType.lowestCurrency) {
                    betPlayers.clear();
                    HashMap<String, Long> allPlayers = new HashMap<>();
                    if (!xiuPlayers.isEmpty())
                        allPlayers.putAll(xiuPlayers);
                    if (!taiPlayers.isEmpty())
                        allPlayers.putAll(taiPlayers);
                    allPlayers.entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByValue())  // Sort by value
                            .forEach(entry -> betPlayers.add(entry.getKey()));
                }
                if (sortItemsType == SortItemsType.highestCurrency) {
                    betPlayers.clear();
                    HashMap<String, Long> allPlayers = new HashMap<>();
                    if (!xiuPlayers.isEmpty())
                        allPlayers.putAll(xiuPlayers);
                    if (!taiPlayers.isEmpty())
                        allPlayers.putAll(taiPlayers);
                    allPlayers.entrySet()
                            .stream()
                            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                            .forEach(entry -> betPlayers.add(entry.getKey()));
                }

                for (int i = 0; i < getMaxItemsPerPage(); i++) {
                    index = getMaxItemsPerPage() * page + i;
                    if (index >= betPlayers.size())
                        break;
                    if (betPlayers.get(index) != null) {
                        String playerName = betPlayers.get(index);
                        ItemStack betPlayerItem = ItemUtil.getItem(invFileConfig.getString("items.betPlayer.type"),
                                (invFileConfig.getString("items.betPlayer.value") == null ? playerName : (invFileConfig.getString("items.betPlayer.value"))),
                                (short) invFileConfig.getInt("items.betPlayer.data"),
                                invFileConfig.getString("items.betPlayer.name"),
                                invFileConfig.getStringList("items.betPlayer.lore"),
                                betReplacements(playerName,
                                        xiuPlayers.containsKey(playerName)
                                                ? xiuPlayers.get(playerName)
                                                : taiPlayers.get(playerName),
                                        xiuPlayers.containsKey(playerName)
                                                ? TaiXiuResult.XIU
                                                : TaiXiuResult.TAI));
                        if (!this.xiuPlayers.isEmpty())
                            if (this.xiuPlayers.containsKey(playerName)) {
                                ItemStack itemStack = TaiXiu.nms.addCustomData(betPlayerItem, "inventoryItem");
                                if (!inventoryItems.contains(itemStack)) {
                                    inventoryItems.add(itemStack);
                                    inventory.addItem(itemStack);
                                }
                            }
                        if (!this.taiPlayers.isEmpty())
                            if (this.taiPlayers.containsKey(playerName)) {
                                ItemStack itemStack = TaiXiu.nms.addCustomData(betPlayerItem, "inventoryItem");
                                if (!inventoryItems.contains(itemStack)) {
                                    inventoryItems.add(itemStack);
                                    inventory.addItem(itemStack);
                                }
                            }
                    }
                }
            }
        });
    }

    private Map<String, String> betReplacements(String playerName, long amountBet, TaiXiuResult result) {
        return Map.of(
                "%playerName%", playerName,
                "%bet%", MessageUtil.getFormatResultName(result),
                "%currencyName%", MessageUtil.getCurrencyName(getSessionData().getCurrencyType()),
                "%currencySymbol%", MessageUtil.getCurrencySymbol(getSessionData().getCurrencyType()),
                "%amountBet%", String.valueOf(amountBet)
        );
    }

    private Map<String, String> sessionReplacements(ISession session) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%time%", session.getResult() != TaiXiuResult.NONE
                ? "0" : String.valueOf(TaiXiuManager.getTimeLeft()));
        replacements.put("%session%", String.valueOf(session.getSession()));
        replacements.put("%xiuPlayerNumber%", String.valueOf(session.getXiuPlayerSnapshot().size()));
        replacements.put("%taiPlayerNumber%", String.valueOf(session.getTaiPlayerSnapshot().size()));
        replacements.put("%xiuTotalBet%", MessageUtil.getFormatMoneyDisplay(TaiXiuManager.getXiuBet(session)));
        replacements.put("%taiTotalBet%", MessageUtil.getFormatMoneyDisplay(TaiXiuManager.getTaiBet(session)));
        replacements.put("%totalBet%", MessageUtil.getFormatMoneyDisplay(TaiXiuManager.getTotalBet(session)));
        replacements.put("%bestWinners%", TaiXiuManager.getBestWinner(session));
        replacements.put("%dice1%", String.valueOf(session.getDice1()));
        replacements.put("%dice2%", String.valueOf(session.getDice2()));
        replacements.put("%dice3%", String.valueOf(session.getDice3()));
        replacements.put("%currencyName%", MessageUtil.getCurrencyName(session.getCurrencyType()));
        replacements.put("%currencySymbol%", MessageUtil.getCurrencySymbol(session.getCurrencyType()));
        replacements.put("%result%", MessageUtil.getFormatResultName(session.getResult()));
        return replacements;
    }

    public enum SortItemsType {
        all, taiPlayers, xiuPlayers, highestCurrency, lowestCurrency
    }

}
