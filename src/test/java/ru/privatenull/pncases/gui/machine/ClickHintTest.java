package ru.privatenull.pncases.gui.machine;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickHintTest {

    private static List<String> plain(List<String> lore) {
        return lore.stream().map(ChatColor::stripColor).toList();
    }

    @Test
    void everyHintBecomesItsOwnLineWithTheKeysInBrackets() {
        List<String> lore = plain(MachineItemFactory.designHintLore(
                List.of("Количество слотов в меню"),
                List.of("Размер", "27 слотов"),
                List.of(Hint.left("+1 ряд"), Hint.right("-1 ряд"),
                        Hint.shiftLeft("+2 ряда"), Hint.shiftRight("-2 ряда"))));

        assertTrue(lore.contains(" [ ЛКМ ] +1 ряд"), lore.toString());
        assertTrue(lore.contains(" [ ПКМ ] -1 ряд"), lore.toString());
        assertTrue(lore.contains(" [ Shift + ЛКМ ] +2 ряда"), lore.toString());
        assertTrue(lore.contains(" [ Shift + ПКМ ] -2 ряда"), lore.toString());
    }

    @Test
    void cursorItemsHaveTheirOwnHint() {
        List<String> lore = plain(MachineItemFactory.designHintLore(
                List.of(), List.of(), List.of(Hint.item("заменить предмет"))));

        assertTrue(lore.contains(" [ Предмет + ЛКМ ] заменить предмет"), lore.toString());
    }

    @Test
    void leftAndRightKeepTheOriginalLetterByLetterGradient() {
        List<String> lore = MachineItemFactory.designHintLore(
                List.of(), List.of(), List.of(Hint.left("a"), Hint.right("b")));

        // A hex color is written as 7 section signs, so five colors mean the gradient survived, not one flat color.
        for (String line : lore) {
            if (ChatColor.stripColor(line).contains("[ ЛКМ ]") || ChatColor.stripColor(line).contains("[ ПКМ ]")) {
                long colorCodes = line.chars().filter(ch -> ch == '§').count();
                assertTrue(colorCodes >= 5 * 7, "expected at least five colors in the label, found " + colorCodes + " codes");
            }
        }
    }

    @Test
    void shiftLabelsAreGradientsToo() {
        List<String> lore = MachineItemFactory.designHintLore(
                List.of(), List.of(), List.of(Hint.shiftLeft("a")));

        String line = lore.stream().filter(value -> ChatColor.stripColor(value).contains("Shift")).findFirst().orElseThrow();
        long colorCodes = line.chars().filter(ch -> ch == '§').count();
        assertTrue(colorCodes >= 7 * 7, "expected at least seven colors in the label, found " + colorCodes + " codes");
    }

    @Test
    void parametersAreKeptAndNumbersAfterTheColorSurvive() {
        List<String> lore = plain(MachineItemFactory.designHintLore(
                List.of("Цена одного открытия"), List.of("Уровни", "12"), List.of(Hint.left("увеличить"))));

        assertTrue(lore.contains(" ↳ 12"), lore.toString());
    }

    @Test
    void withoutHintsThereIsNoGuideBlock() {
        List<String> lore = plain(MachineItemFactory.designHintLore(List.of("Только описание"), List.of(), List.of()));

        assertEquals(List.of("", " Только описание", ""), lore);
    }

    @Test
    void aHintActionThatStartsWithHexCharactersIsNotEatenByTheColor() {
        List<String> lore = plain(MachineItemFactory.designHintLore(
                List.of(), List.of(), List.of(Hint.left("1234 слотов"), Hint.right("deadbeef"))));

        assertTrue(lore.contains(" [ ЛКМ ] 1234 слотов"), lore.toString());
        assertTrue(lore.contains(" [ ПКМ ] deadbeef"), lore.toString());
    }
}
