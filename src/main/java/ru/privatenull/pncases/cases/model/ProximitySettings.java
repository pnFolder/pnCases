package ru.privatenull.pncases.cases.model;

import org.bukkit.Material;

import java.util.List;

/** Ambient scene that plays around a case while a player stands near it. */
public record ProximitySettings(
        boolean enabled,
        Scene scene,
        double radius,
        double spread,
        int rotation,
        double fadeSeconds,
        Music music,
        Sounds sounds,
        Particles particles,
        Decor decor,
        Santa santa,
        Terrain terrain
) {

    public static ProximitySettings defaults(Scene scene) {
        if (scene != Scene.AUTUMN) return defaults();
        return new ProximitySettings(
                false,
                Scene.AUTUMN,
                8.0,
                1.0,
                0,
                3.0,
                new Music(true, 0.3, 4.0),
                new Sounds(true, 1.0),
                new Particles(1.0, true, true, true, true),
                new Decor(11, 5, 4, 3, 0, 0, 4),
                new Santa(true, Material.ORANGE_TERRACOTTA, List.of("&6Осень на дворе", "&eСегодня прохладно", "&7Одевайтесь теплее!", "&cНе забудьте шарф!")),
                new Terrain(true, 7, CaseStyle.BASKET, Material.ORANGE_CONCRETE, Material.HAY_BLOCK, 1.0, true, false,
                        autumnGround(), true, true, true, 0.35)
        );
    }

    public static ProximitySettings defaults() {
        return new ProximitySettings(
                false,
                Scene.WINTER,
                8.0,
                1.0,
                0,
                3.0,
                new Music(true, 0.3, 4.0),
                new Sounds(true, 1.0),
                new Particles(1.0, true, true, true, true),
                new Decor(11, 4, 1, 4, 18, 10, 4),
                new Santa(true, Material.BLUE_CONCRETE, List.of("&bХо-хо-хо!", "&fС наступающим Новым годом!", "&eСкоро Новый год!", "&aГотовь подарки!")),
                new Terrain(true, 7, CaseStyle.GIFT, Material.RED_CONCRETE, Material.BLUE_ICE, 1.0, true, false, defaultGround(),
                        true, true, true, 0.35)
        );
    }

    public static List<Weighted> defaultGround() {
        // CALCITE does not exist before 1.17, so it is looked up by name.
        Material calcite = Material.matchMaterial("CALCITE");
        List<Weighted> ground = new java.util.ArrayList<>(List.of(
                new Weighted(Material.SNOW_BLOCK, 66),
                new Weighted(Material.WHITE_CONCRETE_POWDER, 20),
                new Weighted(Material.LIGHT_BLUE_CONCRETE_POWDER, 6)));
        if (calcite != null) ground.add(new Weighted(calcite, 8));
        return List.copyOf(ground);
    }

    public static List<Weighted> autumnGround() {
        return List.of(
                new Weighted(Material.PODZOL, 40),
                new Weighted(Material.COARSE_DIRT, 28),
                new Weighted(Material.ROOTED_DIRT, 14),
                new Weighted(Material.BROWN_CONCRETE_POWDER, 12),
                new Weighted(Material.ORANGE_TERRACOTTA, 6));
    }

    /** Looped background tune. {@code volume} 0..1 is relative to normal note block loudness. */
    public record Music(boolean enabled, double volume, double pauseSeconds) {
    }

    /** One-shot effect sounds: blocks appearing, crystals, Santa. */
    public record Sounds(boolean enabled, double volume) {
    }

    /** {@code density} multiplies every particle count, 0 disables particles. */
    public record Particles(double density, boolean snowfall, boolean swirl, boolean frostRing, boolean glints) {
    }

    public record Decor(int mounds, int trees, int snowmen, int crystals, int garland, int snowflakes, int gifts) {
    }

    public record Santa(boolean enabled, Material coat, List<String> messages) {
    }

    /**
     * Client-side block changes around the case. Nothing is written to the world; each player
     * just sees different blocks while they stand near the case.
     */
    public record Terrain(boolean enabled, int radius, CaseStyle caseStyle, Material caseColor, Material caseBlock,
                          double caseScale, boolean keepCase, boolean keepDuringOpening,
                          List<Weighted> ground, boolean snowLayer, boolean allBlocks, boolean morphShapes,
                          double sideCoverage) {
    }

    /** How the case block itself looks to players standing near it. */
    public enum CaseStyle {
        NONE("Без изменений", "Блок кейса остаётся обычным"),
        BLOCK("Простой блок", "Блок кейса выглядит как выбранный блок"),
        GIFT("Подарок", "Огромная подарочная коробка с бантом, крышка приподнимается"),
        GLOBE("Снежный шар", "Стеклянный купол с ёлочкой, подарком и снегом внутри"),
        CRYSTAL("Ледяной кристалл", "Парящий светящийся кристалл на снежном постаменте"),
        BASKET("Корзина с урожаем", "Плетёная корзина с тыквами, яблоками и колосьями, крышка приподнимается");

        /** Styles that suit the scene, in the order the Machine button cycles them. */
        public static List<CaseStyle> forScene(Scene scene) {
            return scene == Scene.AUTUMN
                    ? List.of(BASKET, BLOCK, NONE)
                    : List.of(GIFT, GLOBE, CRYSTAL, BLOCK, NONE);
        }

        private final String displayName;
        private final String description;

        CaseStyle(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }
    }

    public record Weighted(Material material, int weight) {
    }

    public enum Scene {
        WINTER("Зимняя сказка",
                List.of("Вокруг кейса идёт снег, земля покрывается снегом.",
                        "Дед Мороз машет рукой, играет новогодняя музыка.")),
        AUTUMN("Осенняя сказка",
                List.of("Вокруг кейса падают листья, земля покрывается листвой и подзолом.",
                        "Пугало качается на ветру, среди клёнов светятся тыквы, играет тихая мелодия."));

        private final String displayName;
        private final List<String> description;

        Scene(String displayName, List<String> description) {
            this.displayName = displayName;
            this.description = List.copyOf(description);
        }

        public String displayName() {
            return displayName;
        }

        public List<String> description() {
            return description;
        }
    }
}
