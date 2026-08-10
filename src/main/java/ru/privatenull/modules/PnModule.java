package ru.privatenull.modules;

import ru.privatenull.PnCasesPlugin;

/** A pnCases feature that can be switched through config.yml. */
public interface PnModule {

    ModuleId id();

    default boolean enabledByDefault() {
        return true;
    }

    default void enable(PnCasesPlugin plugin) {
    }

    default void reload(PnCasesPlugin plugin) {
    }

    default void disable(PnCasesPlugin plugin) {
    }
}
