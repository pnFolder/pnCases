package ru.privatenull.pncases.cases.proximity;

/** Which part of a scene one instance builds. */
enum SceneMode {
    /** Everything: terrain, decoration and the case model; fades with the player's presence. */
    ALL,
    /** Terrain and decoration only; the case model lives in its own {@link #MODEL} scene. */
    DECOR,
    /** The case model and its block only; it can stay after the player leaves. */
    MODEL
}
