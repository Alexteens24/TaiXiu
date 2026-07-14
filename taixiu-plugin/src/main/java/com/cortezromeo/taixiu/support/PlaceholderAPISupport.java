package com.cortezromeo.taixiu.support;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.storage.ISession;
import com.cortezromeo.taixiu.manager.DatabaseManager;
import com.cortezromeo.taixiu.manager.TaiXiuManager;
import com.cortezromeo.taixiu.util.MessageUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class PlaceholderAPISupport extends PlaceholderExpansion {

    @Override
    public String getAuthor() {
        return "Cortez_Romeo";
    }

    @Override
    public String getIdentifier() {
        return "taixiu";
    }

    @Override
    public String getVersion() {
        return TaiXiu.plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String s) {
        if (s == null)
            return null;

        if (s.equals("phien") || s.equals("currentsession"))
            return String.valueOf(TaiXiuManager.getSessionData().getSession());

        if (s.equals("timeleft")) {
            return String.valueOf(TaiXiuManager.getTimeLeft());
        }

        if (s.startsWith("result_phien_")) {
            String sessionNumber = s.replace("result_phien_", "");
            if (sessionNumber.equals("current") || sessionNumber.equals("hientai"))
                sessionNumber = String.valueOf(TaiXiuManager.getSessionData().getSession());

            ISession session = resolveSession(sessionNumber);
            if (session == null) {
                return loadingValue();
            }
            return String.valueOf(session.getResult());
        }

        if (s.startsWith("resultformat_phien_")) {
            String sessionNumber = s.replace("resultformat_phien_", "");
            if (sessionNumber.equals("current") || sessionNumber.equals("hientai"))
                sessionNumber = String.valueOf(TaiXiuManager.getSessionData().getSession());

            ISession session = resolveSession(sessionNumber);
            if (session == null) {
                return loadingValue();
            }
            return MessageUtil.getFormatResultName(session.getResult());
        }

        if (s.startsWith("taiplayers_phien_")) {
            String sessionNumber = s.replace("taiplayers_phien_", "");
            if (sessionNumber.equals("current") || sessionNumber.equals("hientai"))
                sessionNumber = String.valueOf(TaiXiuManager.getSessionData().getSession());

            ISession session = resolveSession(sessionNumber);
            if (session == null) {
                return loadingValue();
            }
            return String.valueOf(session.getTaiPlayerSnapshot());
        }

        if (s.startsWith("xiuplayers_phien_")) {
            String sessionNumber = s.replace("xiuplayers_phien_", "");
            if (sessionNumber.equals("current") || sessionNumber.equals("hientai"))
                sessionNumber = String.valueOf(TaiXiuManager.getSessionData().getSession());

            ISession session = resolveSession(sessionNumber);
            if (session == null) {
                return loadingValue();
            }
            return String.valueOf(session.getXiuPlayerSnapshot());
        }

        if (s.startsWith("taiplayers_bet_phien_")) {
            String sessionNumber = s.replace("taiplayers_bet_phien_", "");
            if (sessionNumber.equals("current") || sessionNumber.equals("hientai"))
                sessionNumber = String.valueOf(TaiXiuManager.getSessionData().getSession());

            ISession session = resolveSession(sessionNumber);
            if (session == null) {
                return loadingValue();
            }

            long sum = session.getTaiPlayerSnapshot().values().stream().mapToLong(Long::longValue).sum();

            return String.valueOf(sum);
        }

        if (s.startsWith("xiuplayers_bet_phien_")) {
            String sessionNumber = s.replace("xiuplayers_bet_phien_", "");
            if (sessionNumber.equals("current") || sessionNumber.equals("hientai"))
                sessionNumber = String.valueOf(TaiXiuManager.getSessionData().getSession());

            ISession session = resolveSession(sessionNumber);
            if (session == null) {
                return loadingValue();
            }

            long sum = session.getXiuPlayerSnapshot().values().stream().mapToLong(Long::longValue).sum();

            return String.valueOf(sum);
        }

        if (s.startsWith("totalbet_phien_")) {
            String sessionNumber = s.replace("totalbet_phien_", "");
            if (sessionNumber.equals("current") || sessionNumber.equals("hientai"))
                sessionNumber = String.valueOf(TaiXiuManager.getSessionData().getSession());

            ISession session = resolveSession(sessionNumber);
            if (session == null) {
                return loadingValue();
            }
            return String.valueOf(TaiXiuManager.getTotalBet(session));
        }
        return null;
    }

    private ISession resolveSession(String sessionNumber) {
        try {
            long id = Long.parseLong(sessionNumber);
            if (id < 0) return null;
            ISession cached = DatabaseManager.getSessionData(id);
            if (cached != null) return cached;
            DatabaseManager.loadSessionDataAsync(id).exceptionally(error -> {
                TaiXiu.plugin.getLogger().warning("Could not asynchronously load placeholder session #" + id
                        + ": " + error.getMessage());
                return null;
            });
        } catch (NumberFormatException ignored) {
            // Invalid user-supplied placeholder suffixes resolve to an empty value.
        }
        return null;
    }

    private String loadingValue() {
        return TaiXiu.plugin.getConfig().getString("placeholder-history-loading-value", "...");
    }
}
