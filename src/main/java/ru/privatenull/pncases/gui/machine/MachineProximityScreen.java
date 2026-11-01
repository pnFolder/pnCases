package ru.privatenull.pncases.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.ProximitySettings;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.SLOT_BACK;

/**
 * Settings of the animation that plays around a case when a player comes close. A hub with one card per
 * category opens a small screen per category, so every screen holds only a handful of large buttons.
 */
final class MachineProximityScreen {

    enum Section {
        HUB("Анимация у кейса"),
        GENERAL("Основное"),
        AUDIO("Музыка и звуки"),
        PARTICLES("Частицы"),
        DECOR("Украшения"),
        SANTA("Дед Мороз"),
        WORLD("Мир и блок кейса");

        final String title;

        Section(String title) {
            this.title = title;
        }

        static Section at(int index) {
            Section[] all = values();
            return index >= 0 && index < all.length ? all[index] : HUB;
        }
    }

    // Hub.
    static final int HUB_SCENE = 4;
    static final int HUB_ENABLED = 10;
    static final int HUB_GENERAL = 12;
    static final int HUB_AUDIO = 14;
    static final int HUB_PARTICLES = 16;
    static final int HUB_DECOR = 20;
    static final int HUB_SANTA = 22;
    static final int HUB_WORLD = 24;
    // General.
    static final int GENERAL_RADIUS = 11;
    static final int GENERAL_SPREAD = 13;
    static final int GENERAL_FADE = 15;
    static final int GENERAL_ROTATION = 22;
    // Audio.
    static final int AUDIO_MUSIC = 10;
    static final int AUDIO_MUSIC_VOLUME = 11;
    static final int AUDIO_MUSIC_PAUSE = 12;
    static final int AUDIO_SOUNDS = 14;
    static final int AUDIO_SOUNDS_VOLUME = 15;
    // Particles.
    static final int PARTICLES_DENSITY = 11;
    static final int PARTICLES_SNOWFALL = 12;
    static final int PARTICLES_SWIRL = 13;
    static final int PARTICLES_RING = 14;
    static final int PARTICLES_GLINTS = 15;
    static final int PARTICLES_AURORA = 16;
    // Decor, seven counters in a row.
    static final int DECOR_MOUNDS = 10;
    static final int DECOR_TREES = 11;
    static final int DECOR_SNOWMEN = 12;
    static final int DECOR_CRYSTALS = 13;
    static final int DECOR_GARLAND = 14;
    static final int DECOR_SNOWFLAKES = 15;
    static final int DECOR_GIFTS = 16;
    // Ded Moroz.
    static final int SANTA_ENABLED = 12;
    static final int SANTA_COAT = 14;
    // World.
    static final int WORLD_ENABLED = 10;
    static final int WORLD_RADIUS = 11;
    static final int WORLD_STYLE = 12;
    static final int WORLD_COLOR = 13;
    static final int WORLD_BLOCK = 14;
    static final int WORLD_SNOW_LAYER = 15;
    static final int WORLD_ALL_BLOCKS = 16;
    static final int WORLD_MORPH = 20;
    static final int WORLD_SIDES = 21;
    static final int WORLD_SCALE = 23;
    static final int WORLD_KEEP = 24;
    static final int WORLD_OPENING = 25;

    static final int BACK = 40;

    /** Coats Ded Moroz can wear. */
    static final List<Material> COATS = List.of(
            Material.BLUE_CONCRETE, Material.RED_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.PURPLE_CONCRETE, Material.GREEN_CONCRETE, Material.WHITE_CONCRETE);

    /** Shirts the scarecrow can wear. */
    static final List<Material> SHIRTS = List.of(
            Material.ORANGE_TERRACOTTA, Material.RED_TERRACOTTA, Material.YELLOW_TERRACOTTA,
            Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.BLUE_TERRACOTTA);

    /** Looks of the plain block style in the autumn scene. */
    static final List<Material> AUTUMN_CASE_BLOCKS = Arrays.asList(
            Material.HAY_BLOCK, Material.PUMPKIN, Material.BARREL, Material.SHROOMLIGHT, null);

    /** Wrapping colours of the gift case. */
    static final List<Material> GIFT_COLORS = List.of(
            Material.RED_CONCRETE, Material.GREEN_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE,
            Material.ORANGE_CONCRETE, Material.PINK_CONCRETE, Material.CYAN_CONCRETE, Material.YELLOW_CONCRETE);

    /** Looks of the plain block style; {@code null} keeps the real block. */
    static final List<Material> CASE_BLOCKS = Arrays.asList(
            Material.BLUE_ICE, Material.PACKED_ICE, Material.SNOW_BLOCK,
            Material.SEA_LANTERN, Material.PRISMARINE_BRICKS, Material.QUARTZ_BLOCK, null);

    private final CaseManager caseManager;
    private final MachineItemFactory items;

    MachineProximityScreen(CaseManager caseManager, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.items = items;
    }

    void open(Player player, String caseName, Section section) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        if (MachineGuiSupport.refreshIfOpen(
                caseManager, player, GuiSession.Screen.MACHINE_PROXIMITY, definition.name(), -1,
                section.ordinal(), inventory -> fill(inventory, definition, section))) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(
                GuiSession.machineProximity(caseName, section.ordinal()), 45,
                ColorUtil.colorize("&0" + titleOf(section, definition.proximity()) + " | " + definition.displayName()));
        fill(inventory, definition, section);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    private void fill(Inventory inventory, CaseDefinition definition, Section section) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        ProximitySettings p = definition.proximity();
        switch (section) {
            case HUB -> fillHub(inventory, definition, p);
            case GENERAL -> fillGeneral(inventory, definition, p);
            case AUDIO -> fillAudio(inventory, definition, p);
            case PARTICLES -> fillParticles(inventory, definition, p);
            case DECOR -> fillDecor(inventory, definition, p);
            case SANTA -> fillSanta(inventory, definition, p);
            case WORLD -> fillWorld(inventory, definition, p);
        }
        inventory.setItem(BACK, items.backButton(definition));
    }

    private void fillHub(Inventory inventory, CaseDefinition definition, ProximitySettings p) {
        boolean fall = autumn(p);
        inventory.setItem(HUB_SCENE, items.configuredButton("machine.proximity.scene",
                Material.PAINTING, items.name("Сцена: " + p.scene().displayName()),
                items.hintLore(p.scene().description(),
                        List.of("Сейчас", p.scene().displayName(),
                                "Важно", "Смена сцены ставит её заготовки украшений, музыки и блоков"),
                        Hint.left("сменить сцену")), definition));
        inventory.setItem(HUB_ENABLED, items.selectable("machine.proximity.enabled", Material.LEVER,
                p.enabled(), items.name("Анимация " + (p.enabled() ? "включена" : "выключена")), items.hintLore(
                        List.of("Когда игрок подходит к кейсу, вокруг него разворачивается сцена",
                                "«" + p.scene().displayName() + "»: " + (fall
                                        ? "листопад, музыка, пугало и смена блоков"
                                        : "снег, музыка, Дед Мороз и смена блоков")),
                        List.of("Состояние", state(p.enabled())),
                        Hint.left(p.enabled() ? "выключить" : "включить")), definition));

        inventory.setItem(HUB_GENERAL, card("general", Material.COMPASS, "Основное",
                "Расстояние, масштаб и скорость появления сцены",
                List.of("Срабатывание", p.radius() + " блоков", "Масштаб", percent(p.spread())), definition));
        inventory.setItem(HUB_AUDIO, card("audio", Material.MUSIC_DISC_CAT, "Музыка и звуки",
                autumn(p) ? "Осенняя мелодия и звуки появления предметов" : "Новогодняя мелодия и звуки появления предметов",
                List.of("Музыка", state(p.music().enabled()), "Звуки", state(p.sounds().enabled())), definition));
        inventory.setItem(HUB_PARTICLES, card("particles", Material.FIREWORK_STAR, "Частицы",
                autumn(p) ? "Листопад, вихрь листьев, тёплое кольцо и искры" : "Снегопад, вихрь, иней и искры",
                List.of("Количество", percent(p.particles().density())), definition));
        ProximitySettings.Decor d = p.decor();
        inventory.setItem(HUB_DECOR, card("decor", Material.FLOWER_POT, "Украшения",
                autumn(p) ? "Кучи листьев, клёны, тыквы, грибы, фонари, сено"
                        : "Сугробы, ёлки, снеговики, кристаллы, гирлянда, подарки",
                List.of("Всего предметов", String.valueOf(d.mounds() + d.trees() + d.snowmen() + d.crystals()
                        + d.garland() + d.snowflakes() + d.gifts())), definition));
        inventory.setItem(HUB_SANTA, card("santa", Material.LEATHER_HELMET,
                autumn(p) ? "Пугало" : "Дед Мороз",
                autumn(p) ? "Машет рукой и говорит про урожай" : "Машет рукой и желает удачи",
                List.of("Состояние", state(p.santa().enabled()), autumn(p) ? "Рубаха" : "Шуба", coatName(p.santa().coat())),
                definition));
        ProximitySettings.Terrain t = p.terrain();
        inventory.setItem(HUB_WORLD, card("world", Material.GRASS_BLOCK, "Мир и блок кейса",
                autumn(p) ? "Осенние блоки вокруг и внешний вид самого кейса" : "Зимние блоки вокруг и внешний вид самого кейса",
                List.of("Блоки", state(t.enabled()), "Радиус", t.radius() + " блоков", "Кейс", t.caseStyle().displayName()),
                definition));
    }

    private void fillGeneral(Inventory inventory, CaseDefinition definition, ProximitySettings p) {
        inventory.setItem(GENERAL_RADIUS, number("radius", Material.TARGET, "Расстояние срабатывания",
                "С какого расстояния игрок запускает анимацию", p.radius() + " блоков", "на 1", "на 5", definition));
        inventory.setItem(GENERAL_SPREAD, number("spread", Material.ENDER_EYE, "Масштаб сцены",
                "Насколько далеко от кейса стоят украшения: меньше — ближе, больше — шире",
                percent(p.spread()), "на 10%", "на 30%", definition));
        inventory.setItem(GENERAL_ROTATION, items.configuredButton("machine.proximity.rotation", Material.SPYGLASS,
                items.name("Ракурс сцены"), items.hintLore(
                        List.of("Поворачивает всю сцену вокруг кейса, чтобы пугало и деревья",
                                "стояли с нужной стороны и ничего не загораживали"),
                        List.of("Сейчас", p.rotation() + "°"),
                        Hint.left("повернуть на 90° по часовой"), Hint.right("повернуть на 90° назад")), definition));
        inventory.setItem(GENERAL_FADE, number("fade", Material.CLOCK, "Скорость появления",
                "За сколько секунд сцена появляется и исчезает", p.fadeSeconds() + " сек", "на 0.5", "на 2", definition));
    }

    private void fillAudio(Inventory inventory, CaseDefinition definition, ProximitySettings p) {
        inventory.setItem(AUDIO_MUSIC, toggle("music", Material.NOTE_BLOCK, "Музыка",
                autumn(p) ? "Тихая осенняя мелодия по кругу, пока игрок рядом" : "Тихая новогодняя мелодия по кругу, пока игрок рядом", p.music().enabled(), definition));
        inventory.setItem(AUDIO_MUSIC_VOLUME, number("music-volume", Material.JUKEBOX, "Громкость музыки",
                "Чем меньше, тем тише фон", percent(p.music().volume()), "на 5%", "на 20%", definition));
        inventory.setItem(AUDIO_MUSIC_PAUSE, number("music-pause", Material.REPEATER, "Пауза между мелодиями",
                "Тишина после каждой мелодии", p.music().pauseSeconds() + " сек", "на 1", "на 5", definition));
        inventory.setItem(AUDIO_SOUNDS, toggle("sounds", Material.BELL, "Звуки",
                autumn(p) ? "Звуки появления листвы, тыкв, сена и пугала" : "Звуки появления сугробов, ёлок, льдинок и Деда Мороза", p.sounds().enabled(), definition));
        inventory.setItem(AUDIO_SOUNDS_VOLUME, number("sounds-volume", Material.NAUTILUS_SHELL, "Громкость звуков",
                "Громкость эффектов без музыки", percent(p.sounds().volume()), "на 10%", "на 50%", definition));
    }

    private void fillParticles(Inventory inventory, CaseDefinition definition, ProximitySettings p) {
        ProximitySettings.Particles c = p.particles();
        inventory.setItem(PARTICLES_DENSITY, number("density", Material.REDSTONE, "Количество частиц",
                "Множитель всех частиц, 0% — совсем без частиц", percent(c.density()), "на 25%", "на 100%", definition));
        boolean fall = autumn(p);
        inventory.setItem(PARTICLES_SNOWFALL, toggle("snowfall", fall ? Material.ORANGE_DYE : Material.SNOWBALL,
                fall ? "Листопад" : "Снегопад",
                fall ? "Листья падают вокруг кейса" : "Снежинки падают вокруг кейса", c.snowfall(), definition));
        inventory.setItem(PARTICLES_SWIRL, toggle("swirl", Material.FEATHER, "Вихрь ветра",
                fall ? "Вихрь листьев поднимается вокруг кейса" : "Снежный вихрь поднимается вокруг кейса",
                c.swirl(), definition));
        inventory.setItem(PARTICLES_RING, toggle("ring", Material.HEART_OF_THE_SEA,
                fall ? "Тёплое кольцо" : "Кольцо инея",
                "Расходящееся кольцо при появлении сцены", c.frostRing(), definition));
        inventory.setItem(PARTICLES_GLINTS, toggle("glints", fall ? Material.GLOWSTONE_DUST : Material.QUARTZ, "Искры",
                fall ? "Светлячки, огоньки тыкв и блеск на корзине" : "Блеск на льдинках, снежинках и самом кейсе",
                c.glints(), definition));
        if (!fall) {
            inventory.setItem(PARTICLES_AURORA, toggle("aurora", Material.LIME_STAINED_GLASS, "Северное сияние",
                    "Переливающиеся полосы света в небе над сценой", c.aurora(), definition));
        }
    }

    private void fillDecor(Inventory inventory, CaseDefinition definition, ProximitySettings p) {
        ProximitySettings.Decor d = p.decor();
        if (autumn(p)) {
            inventory.setItem(DECOR_MOUNDS, count("mounds", Material.ORANGE_CONCRETE_POWDER, "Кучи листьев", d.mounds(), 30, definition));
            inventory.setItem(DECOR_TREES, count("trees", Material.DARK_OAK_SAPLING, "Клёны", d.trees(), 12, definition));
            inventory.setItem(DECOR_SNOWMEN, count("snowmen", Material.CARVED_PUMPKIN, "Тыквы", d.snowmen(), 6, definition));
            inventory.setItem(DECOR_CRYSTALS, count("crystals", Material.RED_MUSHROOM, "Грибы", d.crystals(), 10, definition));
            inventory.setItem(DECOR_GIFTS, count("gifts", Material.WHEAT, "Стога сена", d.gifts(), 16, definition));
            return;
        }
        inventory.setItem(DECOR_MOUNDS, count("mounds", Material.SNOW_BLOCK, "Сугробы", d.mounds(), 30, definition));
        inventory.setItem(DECOR_TREES, count("trees", Material.SPRUCE_SAPLING, "Ёлки", d.trees(), 12, definition));
        inventory.setItem(DECOR_SNOWMEN, count("snowmen", Material.POWDER_SNOW_BUCKET, "Снеговики", d.snowmen(), 6, definition));
        inventory.setItem(DECOR_CRYSTALS, count("crystals", Material.BLUE_ICE, "Кристаллы льда", d.crystals(), 10, definition));
        inventory.setItem(DECOR_GARLAND, count("garland", Material.LANTERN, "Лампочки гирлянды", d.garland(), 40, definition));
        inventory.setItem(DECOR_SNOWFLAKES, count("snowflakes", Material.PRISMARINE_SHARD, "Кружащиеся снежинки", d.snowflakes(), 30, definition));
        inventory.setItem(DECOR_GIFTS, count("gifts", Material.RED_SHULKER_BOX, "Подарки", d.gifts(), 16, definition));
    }

    private void fillSanta(Inventory inventory, CaseDefinition definition, ProximitySettings p) {
        ProximitySettings.Santa s = p.santa();
        boolean fall = autumn(p);
        inventory.setItem(SANTA_ENABLED, items.selectable("machine.proximity.santa", fall ? Material.HAY_BLOCK : Material.RED_DYE,
                s.enabled(), items.name(fall ? "Пугало" : "Дед Мороз"), items.hintLore(
                        List.of("Стоит у кейса, машет рукой и говорит фразы",
                                "Фразы меняются в конфиге: proximity-animation.santa.messages"),
                        List.of("Состояние", state(s.enabled())),
                        Hint.left(s.enabled() ? "выключить" : "включить")), definition));
        inventory.setItem(SANTA_COAT, items.configuredButton("machine.proximity.coat", s.coat(),
                items.name(fall ? "Цвет рубахи" : "Цвет шубы"), items.hintLore(
                        List.of(fall ? "Какого цвета рубаха пугала" : "Какого цвета шуба и шапка"),
                        List.of("Сейчас", coatName(s.coat())),
                        Hint.left("следующий"), Hint.right("предыдущий")), definition));
    }

    private void fillWorld(Inventory inventory, CaseDefinition definition, ProximitySettings p) {
        ProximitySettings.Terrain t = p.terrain();
        boolean fall = autumn(p);
        inventory.setItem(WORLD_ENABLED, toggle("world", Material.DIRT, fall ? "Осенние блоки" : "Зимние блоки",
                fall ? "Земля, стены, крыши, ступени, заборы и листва вокруг кейса становятся осенними. "
                        + "Видно только игроку рядом, мир не меняется"
                        : "Земля, стены, крыши, ступени, заборы и листва вокруг кейса становятся зимними. "
                        + "Видно только игроку рядом, мир не меняется", t.enabled(), definition));
        inventory.setItem(WORLD_RADIUS, items.configuredButton("machine.proximity.world-radius", Material.MAP,
                items.name(fall ? "Радиус осенних блоков" : "Радиус зимних блоков"), items.hintLore(
                        List.of("Насколько большая область вокруг кейса меняется"),
                        List.of("Сейчас", t.radius() + " блоков"),
                        Hint.left("больше на 1"), Hint.right("меньше на 1"),
                        Hint.shiftLeft("больше на 3"), Hint.shiftRight("меньше на 3")), definition));
        inventory.setItem(WORLD_STYLE, items.configuredButton("machine.proximity.style", styleIcon(t.caseStyle(), t.caseBlock()),
                items.name("Вид кейса: " + t.caseStyle().displayName()), items.hintLore(
                        List.of(t.caseStyle().description()),
                        List.of("Сейчас", t.caseStyle().displayName()),
                        Hint.left("следующий"), Hint.right("предыдущий")), definition));
        if (!fall) {
            inventory.setItem(WORLD_COLOR, items.configuredButton("machine.proximity.color", t.caseColor(),
                    items.name("Цвет подарка"), items.hintLore(
                            List.of("Обёртка кейса в виде подарка"),
                            List.of("Сейчас", colorName(t.caseColor())),
                            Hint.left("следующий"), Hint.right("предыдущий")), definition));
        }
        inventory.setItem(WORLD_BLOCK, items.configuredButton("machine.proximity.block",
                t.caseBlock() == null ? Material.SMOOTH_STONE : t.caseBlock(),
                items.name("Блок для простого вида"), items.hintLore(
                        List.of("Какой блок показывается, если выбран «Простой блок»"),
                        List.of("Сейчас", blockName(t.caseBlock())),
                        Hint.left("следующий"), Hint.right("предыдущий")), definition));
        inventory.setItem(WORLD_SNOW_LAYER, toggle("snow-layer", fall ? Material.BROWN_CARPET : Material.SNOW,
                fall ? "Слой листвы" : "Снежный слой",
                fall ? "Тонкий слой опавших листьев на земле и крышах" : "Тонкий снег на земле, крышах и листве",
                t.snowLayer(), definition));
        inventory.setItem(WORLD_ALL_BLOCKS, toggle("all-blocks", Material.STONE_BRICKS, "Менять любые блоки",
                "Выключите, чтобы менялись только земля, трава, песок и камень", t.allBlocks(), definition));
        inventory.setItem(WORLD_MORPH, toggle("morph", Material.QUARTZ_STAIRS, "Менять формы",
                fall ? "Ступени и плиты становятся глинобитными, заборы — тёмными, листва — рыжей"
                        : "Ступени и плиты становятся кварцевыми, заборы и листва — еловыми", t.morphShapes(), definition));
        inventory.setItem(WORLD_SCALE, number("case-scale", Material.PISTON, "Размер кейса",
                fall ? "Насколько велика корзина. Украшения сдвигаются вместе с ней"
                        : "Насколько велик подарок, шар или кристалл. Украшения сдвигаются вместе с ним",
                percent(t.caseScale()), "на 10%", "на 25%", definition));
        inventory.setItem(WORLD_KEEP, toggle("keep-case", Material.ENDER_CHEST, "Кейс остаётся",
                "Когда игрок отходит, модель кейса остаётся на месте и видна издалека. Выключите, чтобы она исчезала вместе со сценой",
                t.keepCase(), definition));
        inventory.setItem(WORLD_OPENING, toggle("keep-opening", Material.TRAPPED_CHEST, "Кейс при открытии",
                "Пока игрок открывает кейс, сцена и модель остаются на месте, без миганий. Выключите, чтобы на время открытия всё исчезало",
                t.keepDuringOpening(), definition));
        inventory.setItem(WORLD_SIDES, number("sides", fall ? Material.ORANGE_WOOL : Material.WHITE_WOOL, fall ? "Листва на стенах" : "Снег на стенах",
                fall ? "Какая часть стен покрывается листвой" : "Какая часть стен покрывается снегом", percent(t.sideCoverage()), "на 5%", "на 20%", definition));
    }

    // ------------------------------------------------------------ item kit

    private ItemStack card(String key, Material icon, String name, String description, List<String> parameters,
                           CaseDefinition definition) {
        return items.configuredButton("machine.proximity." + key, icon, items.name(name), items.hintLore(
                List.of(description), parameters, Hint.left("открыть")), definition);
    }

    private ItemStack toggle(String key, Material icon, String name, String description, boolean on,
                             CaseDefinition definition) {
        return items.selectable("machine.proximity." + key, icon, on, items.name(name), items.hintLore(
                List.of(description), List.of("Состояние", state(on)),
                Hint.left(on ? "выключить" : "включить")), definition);
    }

    private ItemStack number(String key, Material icon, String name, String description, String value,
                             String step, String bigStep, CaseDefinition definition) {
        return items.configuredButton("machine.proximity." + key, icon, items.name(name), items.hintLore(
                List.of(description), List.of("Сейчас", value),
                Hint.left("больше " + step), Hint.right("меньше " + step),
                Hint.shiftLeft("больше " + bigStep), Hint.shiftRight("меньше " + bigStep)), definition);
    }

    private ItemStack count(String key, Material icon, String name, int value, int max, CaseDefinition definition) {
        return items.configuredButton("machine.proximity." + key, icon, items.name(name), items.hintLore(
                List.of(value == 0 ? "Не показывается" : "Сколько штук появится у кейса"),
                List.of("Сейчас", value + " из " + max),
                Hint.left("больше на 1"), Hint.right("меньше на 1"),
                Hint.shiftLeft("больше на 3"), Hint.shiftRight("меньше на 3")), definition);
    }

    private static Material styleIcon(ProximitySettings.CaseStyle style, Material block) {
        return switch (style) {
            case NONE -> Material.BARRIER;
            case BLOCK -> block == null ? Material.STONE : block;
            case GIFT -> Material.CHEST;
            case GLOBE -> Material.GLASS;
            case CRYSTAL -> Material.END_CRYSTAL;
            case BASKET -> Material.COMPOSTER;
        };
    }

    static boolean autumn(ProximitySettings p) {
        return p.scene() == ProximitySettings.Scene.AUTUMN;
    }

    private static String titleOf(Section section, ProximitySettings p) {
        if (section == Section.SANTA && autumn(p)) return "Пугало";
        return section.title;
    }

    static String state(boolean enabled) {
        return enabled ? "&aвключено" : "&cвыключено";
    }

    private static String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    static String coatName(Material coat) {
        return switch (coat) {
            case BLUE_CONCRETE -> "синяя";
            case RED_CONCRETE -> "красная";
            case LIGHT_BLUE_CONCRETE -> "голубая";
            case PURPLE_CONCRETE -> "фиолетовая";
            case GREEN_CONCRETE -> "зелёная";
            case WHITE_CONCRETE -> "белая";
            case ORANGE_TERRACOTTA -> "оранжевая";
            case RED_TERRACOTTA -> "красная";
            case YELLOW_TERRACOTTA -> "жёлтая";
            case BROWN_TERRACOTTA -> "коричневая";
            case GREEN_TERRACOTTA -> "зелёная";
            case BLUE_TERRACOTTA -> "синяя";
            default -> coat.name().toLowerCase(Locale.ROOT);
        };
    }

    static String colorName(Material color) {
        return switch (color) {
            case RED_CONCRETE -> "красный";
            case GREEN_CONCRETE -> "зелёный";
            case BLUE_CONCRETE -> "синий";
            case PURPLE_CONCRETE -> "фиолетовый";
            case ORANGE_CONCRETE -> "оранжевый";
            case PINK_CONCRETE -> "розовый";
            case CYAN_CONCRETE -> "бирюзовый";
            case YELLOW_CONCRETE -> "жёлтый";
            default -> color.name().toLowerCase(Locale.ROOT);
        };
    }

    static String blockName(Material block) {
        if (block == null) return "без изменений";
        return switch (block) {
            case BLUE_ICE -> "синий лёд";
            case PACKED_ICE -> "плотный лёд";
            case SNOW_BLOCK -> "снег";
            case SEA_LANTERN -> "морской фонарь";
            case PRISMARINE_BRICKS -> "призмариновые кирпичи";
            case QUARTZ_BLOCK -> "кварц";
            case HAY_BLOCK -> "стог сена";
            case PUMPKIN -> "тыква";
            case BARREL -> "бочка";
            case SHROOMLIGHT -> "светогриб";
            default -> block.name().toLowerCase(Locale.ROOT);
        };
    }
}
