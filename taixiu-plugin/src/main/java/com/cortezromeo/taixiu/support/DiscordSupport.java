package com.cortezromeo.taixiu.support;

import com.cortezromeo.taixiu.TaiXiu;
import com.cortezromeo.taixiu.api.TaiXiuResult;
import com.cortezromeo.taixiu.api.storage.ISession;
import com.cortezromeo.taixiu.manager.TaiXiuManager;
import com.cortezromeo.taixiu.util.MessageUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class DiscordSupport implements AutoCloseable {

    private final String webHookURL;
    private final ThreadPoolExecutor executor;
    private final Map<Path, CachedTemplate> templates = new ConcurrentHashMap<>();
    private final AtomicLong droppedMessages = new AtomicLong();

    public DiscordSupport(String webHookURL) {
        this.webHookURL = webHookURL;
        int capacity = TaiXiu.plugin == null ? 256
                : Math.max(16, TaiXiu.plugin.getConfig().getInt("discord-webhook-settings.queue-capacity", 256));
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, "TaiXiu-Discord");
                    thread.setDaemon(true);
                    return thread;
                }, (task, pool) -> {
                    long dropped = droppedMessages.incrementAndGet();
                    if (dropped == 1 || dropped % 100 == 0)
                        MessageUtil.throwErrorMessage("Discord webhook queue full; dropped " + dropped + " message(s)");
                });
    }

    public void sendMessage(String message) {
        submit(() -> {
            DiscordWebhook discordWebhook = new DiscordWebhook(webHookURL);
            discordWebhook.addEmbed(new DiscordWebhook.EmbedObject().setDescription(message));
            discordWebhook.execute();
        });
    }

    public void sendMessage(DiscordWebhook.EmbedObject embedObject) {
        submit(() -> {
            DiscordWebhook discordWebhook = new DiscordWebhook(webHookURL);
            discordWebhook.addEmbed(embedObject);
            discordWebhook.execute();
        });
    }

    public void sendResult(String jsonFile, ISession session) {
        submit(() -> {
            DiscordWebhook webhook = new DiscordWebhook(webHookURL);
            webhook.addEmbed(getResultMessageFromJSON(jsonFile, session));
            webhook.execute();
        });
    }

    public void sendPlayerBet(String jsonFile, ISession session, String playerName, UUID playerId,
                              TaiXiuResult result, long money) {
        submit(() -> {
            DiscordWebhook webhook = new DiscordWebhook(webHookURL);
            webhook.addEmbed(getPlayerBetMessageFromJSON(jsonFile, session, playerName, playerId, result, money));
            webhook.execute();
        });
    }

    private void submit(ThrowingRunnable task) {
        if (webHookURL == null || webHookURL.isBlank() || executor.isShutdown()) return;
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception exception) {
                MessageUtil.throwErrorMessage("Discord webhook failed: " + exception.getMessage());
            }
        });
    }

    public DiscordWebhook.EmbedObject getResultMessageFromJSON(String jsonFile, ISession session) throws IOException {
        String jsonString = template(Paths.get(jsonFile));
        JSONObject jsonObject = new JSONObject(jsonString);
        DiscordWebhook.EmbedObject embedObject = new DiscordWebhook.EmbedObject();

        if (jsonObject.has("title")) {
            embedObject.setTitle(formatResultString(jsonObject.getString("title"), jsonObject, session));
        }
        if (jsonObject.has("description")) {
            embedObject.setDescription(formatResultString(jsonObject.getString("description"), jsonObject, session));
        }
        if (jsonObject.has("thumbnail")) {
            JSONObject object = jsonObject.getJSONObject("thumbnail");
            if (session.getResult() == TaiXiuResult.TAI) {
                embedObject.setThumbnail(object.getString("tai"));
            } else if (session.getResult() == TaiXiuResult.XIU)
                embedObject.setThumbnail(object.getString("xiu"));
            else
                embedObject.setThumbnail(object.getString("special"));
        }
        if (jsonObject.has("color")) {
            JSONObject object = jsonObject.getJSONObject("color");
            if (session.getResult() == TaiXiuResult.TAI) {
                embedObject.setColor(object.getInt("tai"));
            } else if (session.getResult() == TaiXiuResult.XIU)
                embedObject.setColor(object.getInt("xiu"));
            else
                embedObject.setColor(object.getInt("special"));
        }
        if (jsonObject.has("fields")) {
            for (Object fieldObj : jsonObject.getJSONArray("fields")) {
                JSONObject field = (JSONObject) fieldObj;
                if (field.getString("fieldtype").equalsIgnoreCase("blank")) {
                    embedObject.addBlankField(false);
                } else {
                    String name = formatResultString(field.getString("name"), jsonObject, session);
                    String value = formatResultString(field.getString("value"), jsonObject, session);
                    embedObject.addField(name, value, field.optBoolean("inline", false));
                }
            }
        }
        if (jsonObject.has("footer")) {
            JSONObject object = jsonObject.getJSONObject("footer");
            String text = formatResultString(object.getString("text"), jsonObject, session);
            String icon_url = object.getString("icon_url");
            if (icon_url == null)
                embedObject.setFooter(text);
            else
                embedObject.setFooter(text, icon_url);
        }
        return embedObject;
    }

    public DiscordWebhook.EmbedObject getPlayerBetMessageFromJSON(String jsonFile, ISession session, String playerName,
                                                                  java.util.UUID playerId, TaiXiuResult taiXiuResult, long money) throws IOException {
        String jsonString = template(Paths.get(jsonFile));
        JSONObject jsonObject = new JSONObject(jsonString);
        DiscordWebhook.EmbedObject embedObject = new DiscordWebhook.EmbedObject();

        if (jsonObject.has("title")) {
            embedObject.setTitle(jsonObject.getString("title"));
        }
        if (jsonObject.has("author")) {
            JSONObject object = jsonObject.getJSONObject("author");
            String name = formatPlayerBet(object.getString("name"), jsonObject, session, playerName, playerId, taiXiuResult, money);
            String icon_url = object.getString("icon_url");
            if (icon_url == null)
                embedObject.setAuthor(name);
            else
                embedObject.setAuthor(name, formatPlayerBet(icon_url, jsonObject, session, playerName, playerId, taiXiuResult, money), formatPlayerBet(icon_url, jsonObject, session, playerName, playerId, taiXiuResult, money));
        }
        if (jsonObject.has("color")) {
            JSONObject object = jsonObject.getJSONObject("color");
            if (taiXiuResult == TaiXiuResult.TAI) {
                embedObject.setColor(object.getInt("tai"));
            } else if (taiXiuResult == TaiXiuResult.XIU)
                embedObject.setColor(object.getInt("xiu"));
        }
        if (jsonObject.has("fields")) {
            for (Object fieldObj : jsonObject.getJSONArray("fields")) {
                JSONObject field = (JSONObject) fieldObj;
                if (field.getString("fieldtype").equalsIgnoreCase("blank")) {
                    embedObject.addBlankField(false);
                } else {
                    String name = formatPlayerBet(field.getString("name"), jsonObject, session, playerName, playerId, taiXiuResult, money);
                    String value = formatPlayerBet(field.getString("value"), jsonObject, session, playerName, playerId, taiXiuResult, money);
                    embedObject.addField(name, value, field.optBoolean("inline", false));
                }
            }
        }
        return embedObject;
    }

    private String formatPlayerBet(String string, JSONObject jsonObject, ISession session, String playerName,
                                   java.util.UUID playerId, TaiXiuResult taiXiuResult, long money) {
        String resultFormatted = "N/A";
        if (taiXiuResult == TaiXiuResult.TAI)
            resultFormatted = jsonObject.getJSONObject("placeholders").getString("tai");
        else if (taiXiuResult == TaiXiuResult.XIU)
            resultFormatted = jsonObject.getJSONObject("placeholders").getString("xiu");
        String pattern = jsonObject.getJSONObject("placeholders").getString("date");
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);

        string = string.replace("%playerName%", playerName)
                .replace("%playerUUID%", playerId.toString())
                .replace("%currencyName%", TaiXiu.nms.stripColor(MessageUtil.getCurrencyName(session.getCurrencyType())))
                .replace("%money%", MessageUtil.getFormatMoneyDisplay(money))
                .replace("%bet%", resultFormatted)
                .replace("%date%", simpleDateFormat.format(new Date()));
        return string;
    }

    private String formatResultString(String string, JSONObject jsonObject, ISession session) {
        String resultFormatted;
        TaiXiuResult result = session.getResult();
        if (result == TaiXiuResult.TAI)
            resultFormatted = jsonObject.getJSONObject("placeholders").getString("tai");
        else if (result == TaiXiuResult.XIU)
            resultFormatted = jsonObject.getJSONObject("placeholders").getString("xiu");
        else
            resultFormatted = jsonObject.getJSONObject("placeholders").getString("special");

        String bestWinnersFormatted = "N/A";
        try {
            String invalid = jsonObject.getJSONObject("placeholders").getJSONObject("bestWinners").getString("invalid");
            if (result == TaiXiuResult.NONE)
                bestWinnersFormatted = invalid;
            else if (result == TaiXiuResult.SPECIAL) {
                bestWinnersFormatted = jsonObject.getJSONObject("placeholders").getJSONObject("bestWinners").getString("valid-special");
                bestWinnersFormatted = bestWinnersFormatted.replace("%allBet%", MessageUtil.getFormatMoneyDisplay(TaiXiuManager.getTotalBet(session)));
            } else {
                Map<String, Long> bestWinners = result == TaiXiuResult.XIU
                        ? session.getXiuPlayerSnapshot() : session.getTaiPlayerSnapshot();
                if (bestWinners.isEmpty()) {
                    bestWinnersFormatted = invalid;
                } else {
                    Long bestWinnersBet = Collections.max(bestWinners.values());

                    List<String> players = new ArrayList<>();
                    for (Map.Entry<String, Long> entry : bestWinners.entrySet())
                        if (entry.getValue() >= bestWinnersBet)
                            players.add(entry.getKey());

                    String delim = jsonObject.getJSONObject("placeholders").getJSONObject("bestWinners").getString("playerName-delim");
                    String bestWinnersName = String.join(delim, players);

                    bestWinnersFormatted = jsonObject.getJSONObject("placeholders").getJSONObject("bestWinners").getString("valid");
                    bestWinnersFormatted = bestWinnersFormatted.replace("%playerName%", bestWinnersName)
                            .replace("%bet%", MessageUtil.getFormatMoneyDisplay(bestWinnersBet * 2));
                }
            }
        } catch (Exception e) {
            MessageUtil.throwErrorMessage("<discordmanager.java<formatString>>" + e);
        }

        string = string.replace("%session%", String.valueOf(session.getSession()))
                .replace("%dice1%", String.valueOf(session.getDice1()))
                .replace("%dice2%", String.valueOf(session.getDice2()))
                .replace("%dice3%", String.valueOf(session.getDice3()))
                .replace("%totalPoint%", String.valueOf(session.getDice1() + session.getDice2() + session.getDice3()))
                .replace("%currencyName%", TaiXiu.nms.stripColor(MessageUtil.getCurrencyName(session.getCurrencyType())))
                .replace("%result%", resultFormatted)
                .replace("%bestWinners%", bestWinnersFormatted);
        return string;
    }

    private String template(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        long modified = Files.getLastModifiedTime(normalized).toMillis();
        CachedTemplate cached = templates.get(normalized);
        if (cached != null && cached.modifiedAt() == modified) return cached.json();
        String json = Files.readString(normalized, StandardCharsets.UTF_8);
        templates.put(normalized, new CachedTemplate(modified, json));
        return json;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        templates.clear();
    }

    private record CachedTemplate(long modifiedAt, String json) { }
    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
