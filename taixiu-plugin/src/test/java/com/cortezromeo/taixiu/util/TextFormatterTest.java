package com.cortezromeo.taixiu.util;

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
}
