package com.cortezromeo.taixiu.geyserform;

import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.file.GeyserFormFile;
import com.cortezromeo.taixiu.manager.TaiXiuManager;
import com.cortezromeo.taixiu.util.TextFormatter;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.bukkit.entity.Player;

public final class RolloverGeyserForm {
    private RolloverGeyserForm() { }

    public static void openForm(Player player) {
        var cfg = GeyserFormFile.get();
        SimpleForm form = SimpleForm.builder()
                .title(TextFormatter.legacy(cfg.getString("form.rollover.title", "Rollover")))
                .content(TextFormatter.legacy(cfg.getString("form.rollover.content", "Choose an action")))
                .button(TextFormatter.legacy(cfg.getString("form.rollover.button.tai", "Tai / High")))
                .button(TextFormatter.legacy(cfg.getString("form.rollover.button.xiu", "Xiu / Low")))
                .button(TextFormatter.legacy(cfg.getString("form.rollover.button.cashout", "Cash out")))
                .validResultHandler((ignored, response) -> {
                    if (response.clickedButtonId() == 0) TaiXiuManager.rollover(player, TaiXiuResult.TAI);
                    else if (response.clickedButtonId() == 1) TaiXiuManager.rollover(player, TaiXiuResult.XIU);
                    else TaiXiuManager.cashoutRollover(player);
                }).build();
        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(form);
    }
}
