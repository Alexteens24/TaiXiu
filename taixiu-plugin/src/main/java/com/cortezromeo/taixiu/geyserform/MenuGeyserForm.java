package com.cortezromeo.taixiu.geyserform;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.file.GeyserFormFile;
import com.cortezromeo.taixiu.language.Messages;
import com.cortezromeo.taixiu.manager.BossBarManager;
import com.cortezromeo.taixiu.manager.DatabaseManager;
import com.cortezromeo.taixiu.manager.TaiXiuManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.HashMap;
import java.util.List;

import static com.cortezromeo.taixiu.util.MessageUtil.sendMessage;

public class MenuGeyserForm {

    private static FileConfiguration geyserFormFile;
    private static String title;
    private static HashMap<Integer, ButtonData> buttonData = new HashMap<>();

    public static void setupValue() {
        geyserFormFile = GeyserFormFile.get();
        buttonData.clear();
        String stringPath = "form.menu.";
        title = geyserFormFile.getString(stringPath + "title");
        buttonData.put(1, new ButtonData(geyserFormFile.getString(stringPath + "button.rule.name")
                , FormImage.Type.valueOf(geyserFormFile.getString(stringPath + "button.rule.imageType"))
                , geyserFormFile.getString(stringPath + "button.rule.imageData")));
        buttonData.put(2, new ButtonData(geyserFormFile.getString(stringPath + "button.sessionInfo.name")
                , FormImage.Type.valueOf(geyserFormFile.getString(stringPath + "button.sessionInfo.imageType"))
                , geyserFormFile.getString(stringPath + "button.sessionInfo.imageData")));
        buttonData.put(3, new ButtonData(geyserFormFile.getString(stringPath + "button.bet.name")
                , FormImage.Type.valueOf(geyserFormFile.getString(stringPath + "button.bet.imageType"))
                , geyserFormFile.getString(stringPath + "button.bet.imageData")));
        buttonData.put(4, new ButtonData(geyserFormFile.getString(stringPath + "button.toggle.on.name")
                , FormImage.Type.valueOf(geyserFormFile.getString(stringPath + "button.toggle.on.imageType"))
                , geyserFormFile.getString(stringPath + "button.toggle.on.imageData")));
        buttonData.put(5, new ButtonData(geyserFormFile.getString(stringPath + "button.toggle.off.name")
                , FormImage.Type.valueOf(geyserFormFile.getString(stringPath + "button.toggle.off.imageType"))
                , geyserFormFile.getString(stringPath + "button.toggle.off.imageData")));
        buttonData.put(6, new ButtonData(geyserFormFile.getString(stringPath + "button.rollover.name", "&6Nhồi cược")
                , FormImage.Type.valueOf(geyserFormFile.getString(stringPath + "button.rollover.imageType", "URL"))
                , geyserFormFile.getString(stringPath + "button.rollover.imageData", "https://i.imgur.com/ReMGrcW.png")));
    }

    private static boolean checkTogglePlayer(Player player) {
        return DatabaseManager.togglePlayers.contains(player.getName());
    }

    public static void openForm(Player player) {
        boolean showRollover = TaiXiu.plugin.getConfig().getBoolean("rollover.enabled")
                && player.hasPermission("taixiu.rollover");
        SimpleForm.Builder builder = SimpleForm.builder().title(TaiXiu.nms.addColor(title))
                .button(buttonData.get(1).getButtonName(), buttonData.get(1).getButtonImageType(), buttonData.get(1).getButtonImageData())
                .button(buttonData.get(2).getButtonName(), buttonData.get(2).getButtonImageType(), buttonData.get(2).getButtonImageData())
                .button(buttonData.get(3).getButtonName(), buttonData.get(3).getButtonImageType(), buttonData.get(3).getButtonImageData());
        if (showRollover) builder.button(buttonData.get(6).getButtonName(),
                buttonData.get(6).getButtonImageType(), buttonData.get(6).getButtonImageData());
        SimpleForm form = builder.button(
                        (checkTogglePlayer(player) ? buttonData.get(5).getButtonName() : buttonData.get(4).getButtonName()),
                        (checkTogglePlayer(player) ? buttonData.get(5).getButtonImageType() : buttonData.get(4).getButtonImageType()),
                        (checkTogglePlayer(player) ? buttonData.get(5).getButtonImageData() : buttonData.get(4).getButtonImageData())
                )
                .validResultHandler((simpleForm, simpleFormResponse) -> {

                    FloodgatePlayer fgPlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
                    int clickedButtonID = simpleFormResponse.clickedButtonId();

                    if (clickedButtonID == 0)
                        fgPlayer.sendForm(RuleGeyserForm.getForm(player));

                    if (clickedButtonID == 1)
                        fgPlayer.sendForm(InfoGeyserForm.getForm(player));

                    if (clickedButtonID == 2) {
                        int configDisableTime = TaiXiu.plugin.getConfig().getInt("bet-settings.disable-while-remaining");
                        if (TaiXiuManager.getTaiXiuTask().getTime() <= configDisableTime) {
                            sendMessage(player, Messages.LATE_BET
                                    .replace("%time%", String.valueOf(TaiXiuManager.getTimeLeft()))
                                    .replace("%configDisableTime%", String.valueOf(configDisableTime)));
                            return;
                        }
                        fgPlayer.sendForm(BetGeyserForm.getForm(player));
                    }

                    if (showRollover && clickedButtonID == 3) {
                        RolloverGeyserForm.openForm(player);
                        return;
                    }

                    int toggleButtonId = showRollover ? 4 : 3;
                    if (clickedButtonID == toggleButtonId) {
                        List<String> togglePlayers = DatabaseManager.togglePlayers;
                        if (togglePlayers.contains(player.getName())) {
                            togglePlayers.remove(player.getName());
                            sendMessage(player, Messages.TOGGLE_OFF);
                        } else {
                            togglePlayers.add(player.getName());
                            sendMessage(player, Messages.TOGGLE_ON);
                        }
                        BossBarManager.toggleBossBar(player);

                        openForm(player);
                    }
                }).build();
        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(form);
    }
}
