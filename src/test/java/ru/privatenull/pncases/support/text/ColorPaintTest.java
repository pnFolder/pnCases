package ru.privatenull.pncases.support.text;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorPaintTest {

    private static final List<String> VALUES = List.of("26.3", "26.3.0", "12", "10 / 20", "100.0", "30d", "deadbeef", "1.5.4");

    @Test
    void concatenatingAValueDirectlyAfterAHexColorSwallowsItsFirstDigits() {
        // The reason paint exists: "&#FCA865" + "26.3" is read as the RGBA color &#FCA86526.
        String broken = ChatColor.stripColor(ColorUtil.colorize("&#FCA865" + "26.3"));

        assertEquals(".3", broken);
    }

    @Test
    void paintKeepsEveryCharacterOfTheValue() {
        for (String value : VALUES) {
            String rendered = ColorUtil.colorize(ColorUtil.paint("&#FCA865", value));

            assertEquals(value, ChatColor.stripColor(rendered), "value " + value);
        }
    }

    @Test
    void paintedTextKeepsItsColorAfterBeingColorizedAgain() {
        String painted = ColorUtil.paint("&#FCA865", "26.3");

        assertTrue(painted.startsWith("§x§f§c§a§8§6§5"));
        assertEquals(painted, ColorUtil.colorize(painted));
    }

    @Test
    void paintedTextDiffersFromUnpaintedHexConcatenation() {
        assertNotEquals(ColorUtil.colorize("&#FCA865" + "26.3"), ColorUtil.colorize(ColorUtil.paint("&#FCA865", "26.3")));
    }
}
