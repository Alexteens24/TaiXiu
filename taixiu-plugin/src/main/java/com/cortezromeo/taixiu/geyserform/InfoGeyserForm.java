package com.cortezromeo.taixiu.geyserform;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.SessionSnapshot;
import com.cortezromeo.taixiu.api.storage.ISession;
import com.cortezromeo.taixiu.file.GeyserFormFile;
import com.cortezromeo.taixiu.language.Messages;
import com.cortezromeo.taixiu.manager.TaiXiuManager;
import com.cortezromeo.taixiu.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;

public class InfoGeyserForm {

    private static FileConfiguration geyserFormFile;
    private static String title;
    private static String goBackButtonName;
    private static String closeButtonName;

    public static void setupValue() {
        geyserFormFile = GeyserFormFile.get();
        String stringPath = "form.info.";
        title = geyserFormFile.getString(stringPath + "title");
        goBackButtonName = geyserFormFile.getString(stringPath + "button.goBack.name");
        closeButtonName = geyserFormFile.getString(stringPath + "button.close.name");
    }

    public static ModalForm getForm(Player player) {
        return ModalForm.builder().title(title)
                .content(TaiXiu.nms.addColor(getContent(TaiXiuManager.getTaiXiuTask().getSession())))
                .button1(TaiXiu.nms.addColor(goBackButtonName))
                .button2(TaiXiu.nms.addColor(closeButtonName))
                .validResultHandler((modalForm, modalFormResponse) -> {
                    if (modalFormResponse.clickedButtonId() == 0)
                        MenuGeyserForm.openForm(player);
                })
                .build();
    }

    private static String getContent(ISession session) {
        SessionSnapshot snapshot = session.snapshot();
        String content = geyserFormFile.getString("form.info.content.content");
        content = content.replace("%session%", String.valueOf(snapshot.id()));
        content = content.replace("%time%", String.valueOf(TaiXiuManager.getTaiXiuTask().getTime()));

        String xiuPlayersFormat = geyserFormFile.getString("form.info.content.placeholders.xiuPlayers");
        StringBuilder xiuPlayers = new StringBuilder();
        if (!snapshot.xiuBets().isEmpty()) {
            for (var entry : snapshot.xiuBets().entrySet()) {
                xiuPlayers.append(xiuPlayersFormat.replace("%player%", entry.getKey()).replace("%money%", MessageUtil.getFormatMoneyDisplay(entry.getValue())));
            }
            content = content.replace("%xiuPlayers%", xiuPlayers);
        } else
            content = content.replace("%xiuPlayers%", Messages.NONE_NAME + "\n");

        String taiPlayersFormat = geyserFormFile.getString("form.info.content.placeholders.taiPlayers");
        StringBuilder taiPlayers = new StringBuilder();
        if (!snapshot.taiBets().isEmpty()) {
            for (var entry : snapshot.taiBets().entrySet()) {
                taiPlayers.append(taiPlayersFormat.replace("%player%", entry.getKey()).replace("%money%", MessageUtil.getFormatMoneyDisplay(entry.getValue())));
            }
            content = content.replace("%taiPlayers%", taiPlayers);
        } else
            content = content.replace("%taiPlayers%", Messages.NONE_NAME + "\n");

        content = content.replace("%totalBet%", MessageUtil.getFormatMoneyDisplay(TaiXiuManager.getTotalBet(session)));
        return content;
    }

}
