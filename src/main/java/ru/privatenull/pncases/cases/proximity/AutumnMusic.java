package ru.privatenull.pncases.cases.proximity;

import java.util.List;

/**
 * Two slow tunes in a minor key for the autumn scene, written in the notation of {@link WinterMusic}:
 * {@code degree[L|H]:ticks}, with {@code R} for a rest.
 */
final class AutumnMusic {

    private static final int TONIC = 5;
    private static final int[] MINOR = {0, 2, 3, 5, 7, 8, 10};

    private static final List<WinterMusic.Tune> TUNES = List.of(
            WinterMusic.parse("""
                    1:16 3:8 5:16 3:8 6:16 5:8 3:24 R:8
                    2:16 4:8 6:16 4:8 5:16 3:8 1:32 R:8
                    1:16 3:8 5:16 7L:8 1H:12 7:4 6:16 5:8 3:16 2:8 3:32 R:8
                    6L:16 1:8 3:16 1:8 2:16 7L:8 1:48""", TONIC, MINOR),
            WinterMusic.parse("""
                    5:12 3:12 1:24 5:12 3:12 2:24 3:12 2:12 1:12 7L:12 6L:48 R:12
                    3:12 5:12 6:24 5:12 3:12 2:24 3:12 1:12 2:12 7L:12 1:48 R:12""", TONIC, MINOR)
    );

    private AutumnMusic() {
    }

    static int tunes() {
        return TUNES.size();
    }

    static WinterMusic.Tune tune(int index) {
        return TUNES.get(Math.floorMod(index, TUNES.size()));
    }
}
