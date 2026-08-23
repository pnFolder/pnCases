package ru.privatenull.pncases.cases.model;

import java.util.List;

public record IdleParticleSettings(
        boolean enabled,
        Theme theme,
        int intervalTicks,
        double radius,
        double height,
        double speed,
        double viewDistance
) {

    public static IdleParticleSettings defaults() {
        return new IdleParticleSettings(true, Theme.MAGIC, 2, 0.85, 1.35, 0.14, 28.0);
    }

    public enum Theme {
        MAGIC("Арканный ритуал",
                List.of("Пентаграмма собирается над кейсом.", "Руны поднимаются и замыкают ритуал.")),
        PORTAL("Разлом пустоты",
                List.of("Разлом удерживается над кейсом.", "Его контур пульсирует и втягивает энергию.")),
        ELECTRIC("Штормовая клетка",
                List.of("Над кейсом замыкается клетка из молний.", "Энергия скачет между её узлами.")),
        FIRE("Огненный феникс",
                List.of("Крупный феникс летит вокруг кейса.", "Видны тело, голова, хохолок, крылья и хвост.")),
        TOXIC("Ядовитая кобра",
                List.of("Кобра охраняет кейс, свернувшись кольцами.", "Видны капюшон, голова, глаза и раздвоенный язык.")),
        FROST("Ледяная корона",
                List.of("Большая снежинка вращается над кейсом.", "Каждый луч покрывается ледяными ветвями.")),
        SOUL("Плач душ",
                List.of("Призрачный страж парит над кейсом.", "Видны лицо, руки и рваная мантия.")),
        OCEAN("Зов левиафана",
                List.of("Миниатюрный левиафан плывёт по кругу.", "Водные течения закручиваются следом.")),
        WINGS("Небесные крылья",
                List.of("Большие крылья раскрываются за кейсом.", "Каркас и отдельные перья двигаются при взмахе.")),
        DRAGON("Драконья орбита",
                List.of("Миниатюрный дракон облетает витрину.", "Видны тело, хвост, голова и взмахи крыльев."));

        private final String displayName;
        private final List<String> description;

        Theme(String displayName, List<String> description) {
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
