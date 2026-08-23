package ru.privatenull.pncases.gui;

import com.google.common.collect.ImmutableMultimap;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.IdleParticleSettings;
import ru.privatenull.pnlibrary.item.HeadUtil;

import java.util.List;
import java.util.Locale;

/**
 * Built-in textured GUI icons. They stay in code so server configs only
 * contain gameplay and user-editable case presentation.
 */
public final class GuiDesign {

    private static final String NEW_TAG = "&#EA3AD2&lɴ&#D744DC&lᴇ&#C34DE5&lᴡ";

    public static final List<Integer> ANIMATION_LIST_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43, 44
    );

    public static final String BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjllYTFkODYyNDdmNGFmMzUxZWQxODY2YmNhNmEzMDQwYTA2YzY4MTc3Yzc4ZTQyMzE2YTEwOThlNjBmYjdkMyJ9fX0=";
    public static final String INFO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTY0MzlkMmUzMDZiMjI1NTE2YWE5YTZkMDA3YTdlNzVlZGQyZDUwMTVkMTEzYjQyZjQ0YmU2MmE1MTdlNTc0ZiJ9fX0=";
    public static final String ANIMATION = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU3OWY4NjdhNzEzOTk5NTdiZTM3YTdjMmZiOTQxZDQ2ODUyM2ZjZTk5MDNlOWRmODhkMzdlMTgzNTY2NSJ9fX0=";
    public static final String MENU = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTk0OWExOGNiNTJjMjkzZmU3ZGU3YmExMDE0NjcxMzQwZWQ3ZmY4ZTVkNzA1YjJkNjBiZjg0ZDUzMTQ4ZTA0In19fQ==";
    public static final String TOGGLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWY5ZTAyNzY4OTM1NTkzMzIxNDM3ZjJkNjcwOWE1OWE2ZTU0MzdlYjQzMDE2MzE5NjVlOWRmNTM1OWFjODdlZiJ9fX0=";
    public static final String HOLOGRAM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdlZTRlNDg5NWE5YzZlMjQzMzZlMjQ2NjVjNTYzODI3MmEzZmVlYWM5NTU4ODJmZjkyYWUzMjE1YWU3ZiJ9fX0=";
    public static final String SHOWCASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWY4NDczNWZjOWM3NjBlOTVlYWYxMGNlYzRmMTBlZGI1ZjM4MjJhNWZmOTU1MWVlYjUwOTUxMzVkMWZmYTMwMiJ9fX0=";
    public static final String SHOWCASE_TOGGLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWE4MTgyZjk5NGQ2NDI2MDUyZjg2YzUwYjI0YzAxNmIyYmE5NGEyNWFmZjU0YWJmMTIyMDM0MTkzYTEzNThlMyJ9fX0=";
    public static final String PURCHASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdkODg1YjMyYjBkZDJkNmI3ZjFiNTgyYTM0MTg2ZjhhNTM3M2M0NjU4OWEyNzM0MjMxMzJiNDQ4YjgwMzQ2MiJ9fX0=";
    public static final String LAYOUT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzk5MjRiY2ZkN2VjNmVhNDM3NDM4Yjc1YjcyZWU0MWEyYjU3MTA0ZGJhMjVlY2FmYTYxMThlNjhlMWI4MWM5ZCJ9fX0=";
    public static final String TITLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTdhNTMwZjVjNTc0MmJmMTllMTc1YTRkNzhhZDQzNWFjMGY0Mzk2ZDNiNTQ2NGJkNjE4MmFiMzgyYWNhNDE3ZCJ9fX0=";
    public static final String SIZE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDVjNmRjMmJiZjUxYzM2Y2ZjNzcxNDU4NWE2YTU2ODNlZjJiMTRkNDdkOGZmNzE0NjU0YTg5M2Y1ZGE2MjIifX19";
    public static final String PREVIEW = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDU1NTYwODNjOWNkZTE5ZjU0YmY3OGE0MjFjYjk3MzFmNjBmMWQzZGUzY2Y1ODRmNTRiMmQ0MzY3N2RmMmE3In19fQ==";
    public static final String CONTENT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzY3ZDVkMzdjZDY0Y2UzZmI1NzM3N2QyNWQ2MTUyYWE0YWMyZTM3OTU0MjQ4ZDVkOTFmODhmYmQ3OTFmNDc2NiJ9fX0=";
    public static final String STYLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmFhMDE2M2ZiODAyYWIxOGE5ZWVjZjljOTk2Nzk3NmJiMTc5NDFjMjMxZTU1Y2IyNTZkM2RmNGNlZjlhMSJ9fX0=";
    public static final String THEME = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODViYzAxNzM0MzljNzg1YjY1ODczZGQ4OGRmN2MwYTUzOTc0MTlmNjdmYTY5NzI3Zjc4ZGUxNDZhNTFjN2JkYSJ9fX0=";
    public static final String REWARD_ITEM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGNjNzk5NTRkMzUwYTk4YzcyMTcyNTY4MzFmNjVjNjJhNDI4MDc0YjZlNGFlOWVlZGU3YTQ0ZjlkZTRhNyJ9fX0=";
    public static final String REWARD_COINS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjBhN2I5NGM0ZTU4MWI2OTkxNTlkNDg4NDZlYzA5MTM5MjUwNjIzN2M4OWE5N2M5MzI0OGEwZDhhYmM5MTZkNSJ9fX0=";
    public static final String REWARD_POINTS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjljODk5YzE0MzhjNGY4MTkyYTVjN2U2Yjc1OGZhYmE4ZmRkNTQ2MDRlZDg5YmY2MjI3YjZmY2MyYzc1YTQyMyJ9fX0=";
    public static final String REWARD_PRIVILEGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzg0YmQ0N2JjNGQ1NjRlNGMyYTA3OTIxMDBiZmQ1MDExMzYzODI1NmMxZjYyMGNkZjdmM2I0ODMwNGU4Mjc4NyJ9fX0=";
    public static final String REWARD_PERMISSION = TITLE;
    public static final String REWARD_COMMAND = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmQwZjQwNjFiZmI3NjdhN2Y5MjJhNmNhNzE3NmY3YTliMjA3MDliZDA1MTI2OTZiZWIxNWVhNmZhOThjYTU1YyJ9fX0=";
    public static final String REWARD_BASE64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTc4ZTZhODNmZmEzYTE2OWZjN2ExMmM2ZjZhYzkzMWZmMWIyMTJjMzYwYTU2Nzc0ZTRjOGQ0ZTI2OWI5NGU1YiJ9fX0=";
    public static final String NO_KEYS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzc1YjM2YzM5NDVmZGFhMGM3NDA4MTg1MzFhOGY0NWJhY2QyNjk2Y2UyYWI4NWE5ZWE0NTI5MGFjY2YxODBjYyJ9fX0=";
    public static final String NEXT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODI3MWE0NzEwNDQ5NWUzNTdjM2U4ZTgwZjUxMWE5ZjEwMmIwNzAwY2E5Yjg4ZTg4Yjc5NWQzM2ZmMjAxMDVlYiJ9fX0=";
    public static final String DISABLED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjc1NDgzNjJhMjRjMGZhODQ1M2U0ZDkzZTY4YzU5NjlkZGJkZTU3YmY2NjY2YzAzMTljMWVkMWU4NGQ4OTA2NSJ9fX0=";
    public static final String AQUARIUM_HEART = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTExM2VlNjEwODQxZGVkMjE1YWNkMmI0Y2FhZWVmODdkZmQ2ZTNkNDc2OGU3YWI0ZTE5ZWI3NmIzZDgxMjFjZiJ9fX0=";

    private static final String ANVIL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTg1NWIzZTFkOWE3YjlmZDc2YTc3NmZhNGVlYzg3MWQ1MmVkMjY5MzI1Mzg2MjhjNDNkZTMyZDFkMmQ3NDMifX19";
    private static final String DYNAMITE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2FmNTk3NzZmMmYwMzQxMmM3YjU5NDdhNjNhMGNmMjgzZDUxZmU2NWFjNmRmN2YyZjg4MmUwODM0NDU2NWU5In19fQ==";
    private static final String PORTAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjBiZmMyNTc3ZjZlMjZjNmM2ZjczNjVjMmM0MDc2YmNjZWU2NTMxMjQ5ODkzODJjZTkzYmNhNGZjOWUzOWIifX19";
    private static final String POISON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTVhZGU5MjcxNTg3ZTlmY2Y2NWYwZTA4YjMzM2YyNWZiOWRmOTlmYjRmNDJhZDBmODZmYjM1NTExNmU5OGIzZSJ9fX0=";
    private static final String FORTUNE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjg2YjlkNThiY2QxYTU1NWY5M2U3ZDg2NTkxNTljZmQyNWI4ZGQ2ZTliY2UxZTk3MzgyMjgyNDI5MTg2MiJ9fX0=";
    private static final String PILLAGER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGFlZTZiYjM3Y2JmYzkyYjBkODZkYjVhZGE0NzkwYzY0ZmY0NDY4ZDY4Yjg0OTQyZmRlMDQ0MDVlOGVmNTMzMyJ9fX0=";
    private static final String AQUARIUM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNThiNTdkZDRhODJhNDJhNDM4OWQ5YzZhZjczYTI3NzE4YThhNDRiOGM0Nzg4Y2Q2ZDY1YWJiZWE3ZTYxODE0MiJ9fX0=";
    private static final String MOB_HUNT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2YzYWEzM2YxM2VlY2E3YTFkNzhhMGI2NzA2NTRlZjZjZDQwNTBkZmU5Nzc5NGY0N2RhMzMyOWViZmY2In19fQ==";
    private static final String DRAGON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg2ZGVhOGE4OTBhODAwNDFhZGI2Y2QwYjM5OWIxN2QxNTQ0NWI3YmM1ZjQ3ZjFlYWMzM2IyYTNlNDhhYjM1ZiJ9fX0=";
    private static final String STANDARD_ANIMATIONS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmUyY2M0MjAxNWU2Njc4ZjhmZDQ5Y2NjMDFmYmY3ODdmMWJhMmMzMmJjZjU1OWEwMTUzMzJmYzVkYjUwIn19fQ==";

    private GuiDesign() {
    }

    public static ItemStack head(String texture, String name) {
        return HeadUtil.create(texture, name);
    }

    public static void hideAttributes(ItemMeta meta) {
        if (meta == null) return;
        meta.setAttributeModifiers(ImmutableMultimap.of());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
            // This flag is unavailable on Minecraft versions before 1.20.5.
        }
    }

    public static ItemStack animation(AnimationType type) {
        String texture = switch (type) {
            case ANVIL -> ANVIL;
            case DYNAMITE -> DYNAMITE;
            case PORTAL -> PORTAL;
            case POISON -> POISON;
            case FORTUNE_RING -> FORTUNE;
            case PILLAGER_RAID -> PILLAGER;
            case AQUARIUM -> AQUARIUM;
            case MOB_HUNT -> MOB_HUNT;
            case DRAGON -> DRAGON;
            case MIMIC -> CONTENT;
            case STORM_DUEL -> PILLAGER;
            case TIME_DISTORTION -> THEME;
        };
        return head(texture, animationName(type));
    }

    public static ItemStack showcaseTheme(IdleParticleSettings.Theme theme) {
        String texture = switch (theme) {
            case MAGIC -> ANIMATION;
            case PORTAL -> PORTAL;
            case ELECTRIC -> THEME;
            case FIRE -> DYNAMITE;
            case TOXIC -> POISON;
            case FROST -> FORTUNE;
            case SOUL -> MOB_HUNT;
            case OCEAN -> AQUARIUM;
            case WINGS -> STANDARD_ANIMATIONS;
            case DRAGON -> DRAGON;
        };
        return head(texture, theme.displayName());
    }

    public static String animationName(AnimationType type) {
        return switch (type) {
            case ANVIL -> gradient("Наковальня", 0xFFD865, 0xF08A45);
            case DYNAMITE -> gradient("Динамит", 0xFF7165, 0xFCB865);
            case PORTAL -> gradient("Чёрная дыра", 0xB675FF, 0x655EFC);
            case POISON -> gradient("Отравление", 0x9EFC65, 0x53C86A);
            case FORTUNE_RING -> gradient("Круг фортуны", 0xFFE66D, 0xFCA865);
            case PILLAGER_RAID -> gradient("Осада разбойников", 0xFC8C65, 0xD94848);
            case AQUARIUM -> gradient("Водяная капсула", 0x65D1FC, 0x4F83FF);
            case MOB_HUNT -> gradient("Охота на стражей", 0xFC7165, 0xB83C6F);
            case DRAGON -> gradient("Дыхание дракона", 0xD56BFF, 0x655EFC);
            case MIMIC -> gradient("Голодный мимик", 0xFFB347, 0xE05252);
            case STORM_DUEL -> gradient("Грозовая дуэль", 0x65D1FC, 0xA978FF);
            case TIME_DISTORTION -> gradient("Искажение времени", 0xFFE38A, 0x65D1FC);
        };
    }

    public static String animationPlainName(AnimationType type) {
        return switch (type) {
            case ANVIL -> "Наковальня";
            case DYNAMITE -> "Динамит";
            case PORTAL -> "Чёрная дыра";
            case POISON -> "Отравление";
            case FORTUNE_RING -> "Круг фортуны";
            case PILLAGER_RAID -> "Осада разбойников";
            case AQUARIUM -> "Водяная капсула";
            case MOB_HUNT -> "Охота на стражей";
            case DRAGON -> "Дыхание дракона";
            case MIMIC -> "Голодный мимик";
            case STORM_DUEL -> "Грозовая дуэль";
            case TIME_DISTORTION -> "Искажение времени";
        };
    }

    public static String animationSelectionName(AnimationType type) {
        String name = animationPlainName(type);
        return type == AnimationType.STORM_DUEL || type == AnimationType.TIME_DISTORTION
                ? name + " &8" + NEW_TAG
                : name;
    }

    public static List<String> animationDescription(AnimationType type) {
        return switch (type) {
            case ANVIL -> List.of(
                    "С неба падает огромная наковальня.",
                    "Мощный удар разрушает кейс",
                    "и выбивает из него награду.");
            case DYNAMITE -> List.of(
                    "Динамит летит к кейсу по дуге.",
                    "После взрыва, дыма и огня",
                    "появляется награда.");
            case PORTAL -> List.of(
                    "Предметы разлетаются вокруг кейса.",
                    "Чёрная дыра поглощает их",
                    "и выпускает награду.");
            case POISON -> List.of(
                    "Кейс окутывает ядовитый туман.",
                    "Появляется куб слизи, который",
                    "после взрыва оставляет награду.");
            case FORTUNE_RING -> List.of(
                    "Награды вращаются по светящейся орбите.",
                    "Круг постепенно замедляется",
                    "и выбирает итоговый предмет.");
            case PILLAGER_RAID -> List.of(
                    "Разбойники окружают кейс",
                    "и по очереди разбивают его.",
                    "Из осколков появляется награда.");
            case AQUARIUM -> List.of(
                    "Кейс превращается в водяную капсулу.",
                    "Внутри загорается сердце моря",
                    "и из воды поднимается награда.");
            case MOB_HUNT -> List.of(
                    "Вокруг кейса появляются стражи.",
                    "Выберите одного моба ударом —",
                    "из него выпадет награда.");
            case DRAGON -> List.of(
                    "Дракон из частиц облетает кейс.",
                    "Он снижается и выдыхает пламя,",
                    "из которого появляется награда.");
            case MIMIC -> List.of(
                    "Сундук оживает и превращается в мимика.",
                    "Он проглатывает ложные награды,",
                    "а затем выплёвывает выигранную.");
            case STORM_DUEL -> List.of(
                    "Два воина сходятся у кейса.",
                    "Грозовой удар разрывает их клинки",
                    "и освобождает награду.");
            case TIME_DISTORTION -> List.of(
                    "Часовщик собирает кейс из прошлого.",
                    "Его будущий двойник создаёт парадокс,",
                    "а обратный ход освобождает награду.");
        };
    }

    private static String gradient(String text, int startRgb, int endRgb) {
        int[] codePoints = text.codePoints().toArray();
        if (codePoints.length == 0) return "";

        StringBuilder result = new StringBuilder(codePoints.length * 16);
        int last = Math.max(1, codePoints.length - 1);
        for (int index = 0; index < codePoints.length; index++) {
            double progress = (double) index / last;
            int red = interpolate(startRgb >> 16 & 0xFF, endRgb >> 16 & 0xFF, progress);
            int green = interpolate(startRgb >> 8 & 0xFF, endRgb >> 8 & 0xFF, progress);
            int blue = interpolate(startRgb & 0xFF, endRgb & 0xFF, progress);
            result.append(String.format(Locale.ROOT, "&#%02X%02X%02X&l", red, green, blue))
                    .appendCodePoint(codePoints[index]);
        }
        return result.toString();
    }

    private static int interpolate(int start, int end, double progress) {
        return (int) Math.round(start + (end - start) * progress);
    }
}
