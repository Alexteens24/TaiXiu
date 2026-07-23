package com.cortezromeo.taixiu.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextFormatter {
    private static final Pattern LEGACY_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern MINI_TAG = Pattern.compile("(?<!\\\\)</?[A-Za-z#!?][^>]*>");
    private static final String LEGACY_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
    private static final long WARNING_INTERVAL_MILLIS = 60_000;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final AtomicLong NEXT_WARNING_AT = new AtomicLong();
    private static volatile Mode mode = Mode.LEGACY;
    private static volatile Consumer<String> warningSink = ignored -> { };

    private TextFormatter() { }

    public enum Mode {
        LEGACY,
        MINIMESSAGE;

        public static Mode parse(String value) {
            if (value == null) throw new IllegalArgumentException("text-format is required");
            return valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    public static void configure(String configuredMode, Consumer<String> warnings) {
        mode = Mode.parse(configuredMode);
        warningSink = warnings == null ? ignored -> { } : warnings;
        NEXT_WARNING_AT.set(0);
    }

    public static Mode mode() {
        return mode;
    }

    public static Component component(String input) {
        return component(input, mode);
    }

    static Component component(String input, Mode selectedMode) {
        if (input == null || input.isEmpty()) return Component.empty();
        if (selectedMode == Mode.LEGACY)
            return LEGACY_SECTION.deserialize(translateLegacy(input));
        try {
            Component parsed = MINI_MESSAGE.deserialize(input);
            if (containsUnresolvedKnownTag(input, parsed))
                throw new IllegalArgumentException("A recognized MiniMessage tag has invalid arguments");
            return parsed;
        } catch (RuntimeException exception) {
            warnMiniMessageFailure(exception);
            return Component.text(stripBrokenMiniMessage(input));
        }
    }

    public static Component legacyComponent(String input) {
        return component(input, Mode.LEGACY);
    }

    public static String legacy(String input) {
        return legacy(component(input));
    }

    public static String legacy(Component component) {
        return LEGACY_SECTION.serialize(component == null ? Component.empty() : component);
    }

    public static String plain(String input) {
        return PLAIN_TEXT.serialize(component(input));
    }

    public static String plain(Component component) {
        return PLAIN_TEXT.serialize(component == null ? Component.empty() : component);
    }

    private static String translateLegacy(String input) {
        Matcher matcher = LEGACY_HEX.matcher(input);
        StringBuilder expanded = new StringBuilder(input.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (int i = 0; i < hex.length(); i++)
                replacement.append('§').append(hex.charAt(i));
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(expanded);

        for (int i = 0; i + 1 < expanded.length(); i++) {
            if (expanded.charAt(i) != '&') continue;
            char code = expanded.charAt(i + 1);
            if (LEGACY_CODES.indexOf(code) < 0) continue;
            expanded.setCharAt(i, '§');
            expanded.setCharAt(i + 1, Character.toLowerCase(code));
        }
        return expanded.toString();
    }

    private static String stripBrokenMiniMessage(String input) {
        try {
            return MINI_MESSAGE.stripTags(input);
        } catch (RuntimeException ignored) {
            return MINI_TAG.matcher(input).replaceAll("");
        }
    }

    private static boolean containsUnresolvedKnownTag(String input, Component parsed) {
        String plain = PLAIN_TEXT.serialize(parsed);
        Matcher matcher = MINI_TAG.matcher(input);
        while (matcher.find()) {
            String token = matcher.group();
            int nameStart = token.startsWith("</") ? 2 : 1;
            int nameEnd = token.indexOf(':', nameStart);
            if (nameEnd < 0) nameEnd = token.length() - 1;
            String name = token.substring(nameStart, nameEnd);
            if (MINI_MESSAGE.tags().has(name) && plain.contains(token)) return true;
        }
        return false;
    }

    private static void warnMiniMessageFailure(RuntimeException exception) {
        long now = System.currentTimeMillis();
        long next = NEXT_WARNING_AT.get();
        if (now < next || !NEXT_WARNING_AT.compareAndSet(next, now + WARNING_INTERVAL_MILLIS)) return;
        warningSink.accept("Could not parse configured MiniMessage text; displaying it without formatting: "
                + exception.getMessage());
    }
}
