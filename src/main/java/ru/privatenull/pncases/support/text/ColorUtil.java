package ru.privatenull.pncases.support.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/** Parses MiniMessage together with pnFolder legacy, RGB and RGBA colors. */
public final class ColorUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    /** Private-use character that gives a trailing style a text node to attach to. */
    private static final String TRAILING_MARKER = "";
    private static final java.util.regex.Pattern VISIBLE_CODES =
            java.util.regex.Pattern.compile("§x(?:§[0-9a-fA-F]){6}|§.");
    private static final Map<Character, TextColor> COLORS = Map.ofEntries(
            Map.entry('0', NamedTextColor.BLACK), Map.entry('1', NamedTextColor.DARK_BLUE),
            Map.entry('2', NamedTextColor.DARK_GREEN), Map.entry('3', NamedTextColor.DARK_AQUA),
            Map.entry('4', NamedTextColor.DARK_RED), Map.entry('5', NamedTextColor.DARK_PURPLE),
            Map.entry('6', NamedTextColor.GOLD), Map.entry('7', NamedTextColor.GRAY),
            Map.entry('8', NamedTextColor.DARK_GRAY), Map.entry('9', NamedTextColor.BLUE),
            Map.entry('a', NamedTextColor.GREEN), Map.entry('b', NamedTextColor.AQUA),
            Map.entry('c', NamedTextColor.RED), Map.entry('d', NamedTextColor.LIGHT_PURPLE),
            Map.entry('e', NamedTextColor.YELLOW), Map.entry('f', NamedTextColor.WHITE)
    );

    private ColorUtil() {
    }

    public static Component component(String value) {
        return MINI_MESSAGE.deserialize(toMiniMessage(value == null ? "" : value));
    }

    private static String toMiniMessage(String input) {
        StringBuilder result = new StringBuilder(input.length());

        for (int index = 0; index < input.length();) {
            char marker = input.charAt(index);
            if (marker != '&' && marker != '\u00A7') {
                result.append(marker);
                index++;
                continue;
            }
            HexColor hex = readHex(input, index);
            if (hex != null) {
                result.append("<reset><#").append(hex.color().asHexString(), 1, 7).append('>');
                index += hex.length();
                continue;
            }
            if (index + 1 >= input.length()) {
                result.append(marker);
                index++;
                continue;
            }
            char code = Character.toLowerCase(input.charAt(index + 1));
            TextColor color = COLORS.get(code);
            if (color != null) {
                result.append("<reset><").append(colorTag(code)).append('>');
                index += 2;
                continue;
            }
            TextDecoration decoration = decoration(code);
            if (decoration != null) {
                result.append('<').append(decoration.toString()).append('>');
                index += 2;
                continue;
            }
            if (code == 'r') {
                result.append("<reset>");
                index += 2;
                continue;
            }
            result.append(marker);
            index++;
        }
        return result.toString();
    }

    /**
     * Returns legacy text. A trailing color without text (such as the prefix "&amp;#A6B0BA")
     * is kept so that it can be concatenated with text and colorized again.
     */
    /**
     * A color prefix followed by a value. The prefix is parsed on its own, because a value that starts with
     * hex characters (such as "26.3", "12" or "30d") would otherwise be read as the alpha part of an
     * RGBA color like &#FCA86526 and disappear.
     */
    public static String paint(String color, Object value) {
        return colorize(color) + value;
    }

    public static String colorize(String value) {
        String source = value == null ? "" : value;
        String serialized = LEGACY.serialize(component(source));
        if (!VISIBLE_CODES.matcher(serialized).replaceAll("").isBlank()) return serialized;
        // Without visible text Adventure drops the style, so give it a text node and remove it afterwards.
        return LEGACY.serialize(component(source + TRAILING_MARKER)).replace(TRAILING_MARKER, "");
    }

    private static HexColor readHex(String value, int index) {
        if (isAmpersandHex(value, index, 8)) {
            // Adventure/Minecraft text has no alpha channel. RRGGBBAA is accepted,
            // and AA is consumed while the visible RGB component is retained.
            return new HexColor(rgb(value, index + 2), 10);
        }
        if (isAmpersandHex(value, index, 6)) {
            return new HexColor(rgb(value, index + 2), 8);
        }
        if (isLegacyHex(value, index)) {
            StringBuilder hex = new StringBuilder(6);
            for (int part = index + 3; part <= index + 13; part += 2) hex.append(value.charAt(part));
            return new HexColor(TextColor.color(Integer.parseInt(hex.toString(), 16)), 14);
        }
        return null;
    }

    private static TextColor rgb(String value, int start) {
        return TextColor.color(Integer.parseInt(value.substring(start, start + 6), 16));
    }

    private static boolean isAmpersandHex(String value, int index, int digits) {
        if (value.charAt(index) != '&' || index + digits + 1 >= value.length()
                || value.charAt(index + 1) != '#') return false;
        for (int part = index + 2; part < index + 2 + digits; part++) {
            if (!isHex(value.charAt(part))) return false;
        }
        return true;
    }

    private static boolean isLegacyHex(String value, int index) {
        if (index + 13 >= value.length() || Character.toLowerCase(value.charAt(index + 1)) != 'x') return false;
        for (int part = index + 2; part <= index + 12; part += 2) {
            if ((value.charAt(part) != '&' && value.charAt(part) != '\u00A7')
                    || !isHex(value.charAt(part + 1))) return false;
        }
        return true;
    }

    private static TextDecoration decoration(char code) {
        return switch (code) {
            case 'k' -> TextDecoration.OBFUSCATED;
            case 'l' -> TextDecoration.BOLD;
            case 'm' -> TextDecoration.STRIKETHROUGH;
            case 'n' -> TextDecoration.UNDERLINED;
            case 'o' -> TextDecoration.ITALIC;
            default -> null;
        };
    }

    private static String colorTag(char code) {
        return switch (code) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            default -> throw new IllegalArgumentException("Unknown legacy color code: " + code);
        };
    }

    private static boolean isHex(char value) {
        return value >= '0' && value <= '9'
                || value >= 'a' && value <= 'f'
                || value >= 'A' && value <= 'F';
    }

    private record HexColor(TextColor color, int length) {
    }
}
