package com.cortezromeo.taixiu.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextFormatterTest {
    @AfterEach
    void restoreLegacyMode() {
        TextFormatter.configure("LEGACY", ignored -> { });
    }

    @Test
    void parsesLegacyColorsDecorationsAndRgb() {
        var legacy = TextFormatter.component("&c&lAlert", TextFormatter.Mode.LEGACY);
        assertEquals(NamedTextColor.RED, legacy.color());
        assertEquals(TextDecoration.State.TRUE, legacy.decoration(TextDecoration.BOLD));

        String rgb = TextFormatter.legacy(TextFormatter.component("&#03c6fcSky", TextFormatter.Mode.LEGACY));
        assertTrue(rgb.startsWith("§x§0§3§c§6§f§c"));
        assertEquals("Sky", TextFormatter.plain(TextFormatter.component("&#03c6fcSky",
                TextFormatter.Mode.LEGACY)));
    }

    @Test
    void parsesMiniMessageWithInteractiveEvents() {
        var component = TextFormatter.component(
                "<red><bold><click:run_command:'/taixiu'>Play</click></bold></red>",
                TextFormatter.Mode.MINIMESSAGE);
        assertEquals(NamedTextColor.RED, component.color());
        assertEquals(TextDecoration.State.TRUE, component.decoration(TextDecoration.BOLD));
        assertNotNull(component.clickEvent());
        assertEquals("Play", TextFormatter.plain(component));
    }

    @Test
    void preservesMiniMessageGradientsWhenBridgedToLegacy() {
        var component = TextFormatter.component(
                "<gradient:#ff0000:#0000ff>AB</gradient>",
                TextFormatter.Mode.MINIMESSAGE);

        assertEquals("AB", TextFormatter.plain(component));
        assertTrue(TextFormatter.legacy(component).contains("§x"));
    }

    @Test
    void keepsModesExplicitAndUnknownTagsLiteral() {
        assertEquals("&cLegacy", TextFormatter.plain(
                TextFormatter.component("&cLegacy", TextFormatter.Mode.MINIMESSAGE)));
        assertEquals("<red>Mini</red>", TextFormatter.plain(
                TextFormatter.component("<red>Mini</red>", TextFormatter.Mode.LEGACY)));
        assertEquals("Use <amount>", TextFormatter.plain(
                TextFormatter.component("Use <amount>", TextFormatter.Mode.MINIMESSAGE)));
    }

    @Test
    void insertsExternalPlaceholderOutputAsLiteralText() {
        var component = TextFormatter.componentWithUnparsedPlaceholders(
                "<green>Rank: %rank% %action%</green>",
                TextFormatter.Mode.MINIMESSAGE,
                token -> switch (token) {
                    case "%rank%" -> "&a<red>VIP</red>";
                    case "%action%" -> "<click:run_command:'/some-command'><hover:show_text:'Secret'>Click me</hover></click>";
                    default -> token;
                });

        assertEquals("Rank: &a<red>VIP</red> <click:run_command:'/some-command'>"
                        + "<hover:show_text:'Secret'>Click me</hover></click>",
                TextFormatter.plain(component));
        assertFalse(hasClickEvent(component));
        assertFalse(hasHoverEvent(component));
    }

    @Test
    void preservesUnresolvedPlaceholdersAndDoesNotResolveTagArguments() {
        var component = TextFormatter.componentWithUnparsedPlaceholders(
                "<click:run_command:'/%command%'>%known% %unknown%</click>",
                TextFormatter.Mode.MINIMESSAGE,
                token -> token.equals("%known%") ? "safe" : token);

        assertEquals("safe %unknown%", TextFormatter.plain(component));
        assertEquals("/%command%", component.clickEvent().value());
    }

    @Test
    void insertsExternalPlaceholderOutputLiterallyInLegacyMode() {
        var component = TextFormatter.componentWithUnparsedPlaceholders(
                "&cRank: %rank%",
                TextFormatter.Mode.LEGACY,
                token -> "&aVIP");

        assertEquals("Rank: &aVIP", TextFormatter.plain(component));
        assertEquals(NamedTextColor.RED, component.color());
    }

    @Test
    void capturedModeIsStableAcrossReload() {
        TextFormatter.configure("LEGACY", warning -> fail(warning));
        TextFormatter.Mode legacySnapshot = TextFormatter.mode();
        TextFormatter.configure("MINIMESSAGE", warning -> fail(warning));

        var queuedLegacy = TextFormatter.component("&cQueued", legacySnapshot);
        assertEquals(NamedTextColor.RED, queuedLegacy.color());
        assertEquals("Queued", TextFormatter.plain(queuedLegacy));

        TextFormatter.Mode miniSnapshot = TextFormatter.mode();
        TextFormatter.configure("LEGACY", warning -> fail(warning));

        var queuedMini = TextFormatter.component("<red>Queued</red>", miniSnapshot);
        assertEquals(NamedTextColor.RED, queuedMini.color());
        assertEquals("Queued", TextFormatter.plain(queuedMini));
    }

    @Test
    void malformedMiniMessageWarnsAndFallsBackWithoutFormatting() {
        List<String> warnings = new ArrayList<>();
        TextFormatter.configure("MINIMESSAGE", warnings::add);

        assertEquals("Broken", TextFormatter.plain("<color:not-a-color>Broken</color>"));
        assertEquals(1, warnings.size());
    }

    @Test
    void validatesConfiguredModeCaseInsensitively() {
        assertEquals(TextFormatter.Mode.MINIMESSAGE, TextFormatter.Mode.parse("minimessage"));
        assertThrows(IllegalArgumentException.class, () -> TextFormatter.Mode.parse("mixed"));
    }

    private static boolean hasClickEvent(Component component) {
        if (component.clickEvent() != null) return true;
        return component.children().stream().anyMatch(TextFormatterTest::hasClickEvent);
    }

    private static boolean hasHoverEvent(Component component) {
        if (component.hoverEvent() != null) return true;
        return component.children().stream().anyMatch(TextFormatterTest::hasHoverEvent);
    }
}
