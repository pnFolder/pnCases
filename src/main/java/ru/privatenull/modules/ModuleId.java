package ru.privatenull.modules;

public enum ModuleId {
    RARITY("rarity"),
    PREVIEW("preview"),
    GUI_ANIMATION("gui-animation"),
    TITLE("title");

    private final String configKey;

    ModuleId(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
