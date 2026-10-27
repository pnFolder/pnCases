package ru.privatenull.pncases.gui.machine;

import java.util.List;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.support.text.ColorUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineItemFactoryTest {
    @Test
    void numericalValuesSurviveRepeatedColorProcessing() {
        for (String value : List.of("0.11%", "0.112%", "112", "10", "100%", "7 дней")) {
            List<String> lore = MachineItemFactory.designLore(List.of(), List.of("Шанс", value), null, null);
            String rendered = ColorUtil.colorize(ColorUtil.colorize(lore.get(2)));
            assertEquals(" ↳ " + value, ChatColor.stripColor(rendered));
        }
    }

    @Test
    void numbersInDescriptionsAndParameterNamesAreNotConsumedAsAlpha() {
        List<String> lore = MachineItemFactory.designLore(List.of("100000 открытий"),
                List.of("10 наград", "112"), null, null);
        assertEquals(" 100000 открытий", ChatColor.stripColor(ColorUtil.colorize(lore.get(1))));
        assertEquals(" 10 наград", ChatColor.stripColor(ColorUtil.colorize(lore.get(3))));
    }
}
