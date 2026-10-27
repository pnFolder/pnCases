package ru.privatenull.pncases.cases.proximity;

import java.util.ArrayList;
import java.util.List;

/**
 * Two short carols written as note block melodies. Notes are written as {@code degree[L|H]:ticks},
 * where degree 1-7 is a step of the major scale, {@code L} is an octave lower, {@code H} an
 * octave higher and {@code R} a rest.
 */
final class WinterMusic {

    /** Semitone index on the note block range (0-24) that degree 1 maps to. */
    private static final int TONIC = 7;
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};

    private static final List<Tune> TUNES = List.of(
            tune("""
                    5L:8 1:8 1:4 2:4 1:4 7L:4 6L:12 6L:4 6L:8 2:8 2:4 3:4 2:4 1:4 7L:12 5L:4 5L:8
                    3:8 3:4 4:4 3:4 2:4 1:12 6L:4 5L:4 5L:4 6L:8 2:8 7L:8 1:24
                    5L:8 1:8 1:4 2:4 1:4 7L:4 6L:12 6L:4 6L:8 2:8 2:4 3:4 2:4 1:4 7L:12 5L:4 5L:8
                    3:8 3:4 4:4 3:4 2:4 1:12 6L:4 5L:4 5L:4 6L:8 2:8 7L:8 1:24"""),
            tune("""
                    3:8 3:8 3:16 3:8 3:8 3:16 3:8 5:8 1:12 2:4 3:32
                    4:8 4:8 4:12 4:4 4:8 3:8 3:8 3:4 3:4 3:8 2:8 2:8 3:8 2:16 5:16
                    3:8 3:8 3:16 3:8 3:8 3:16 3:8 5:8 1:12 2:4 3:32
                    4:8 4:8 4:12 4:4 4:8 3:8 3:8 3:4 3:4 5:8 5:8 4:8 2:8 1:32""")
    );

    private WinterMusic() {
    }

    static int tunes() {
        return TUNES.size();
    }

    static Tune tune(int index) {
        return TUNES.get(Math.floorMod(index, TUNES.size()));
    }

    /** Pitch multiplier of a note block note, 0.5 to 2.0. */
    static float pitch(int semitone) {
        return (float) Math.pow(2.0, (semitone - 12) / 12.0);
    }

    private static Tune tune(String text) {
        return parse(text, TONIC, MAJOR);
    }

    /** Parses a melody written in the notation above; {@code scale} holds the semitones of the seven degrees. */
    static Tune parse(String text, int tonic, int[] scale) {
        List<Note> notes = new ArrayList<>();
        int time = 0;
        for (String token : text.trim().split("\\s+")) {
            String[] parts = token.split(":");
            int length = Integer.parseInt(parts[1]);
            String key = parts[0];
            if (!key.equals("R")) {
                int octave = 0;
                if (key.endsWith("L")) octave = -12;
                if (key.endsWith("H")) octave = 12;
                int degree = Integer.parseInt(key.substring(0, 1));
                int semitone = tonic + scale[degree - 1] + octave;
                notes.add(new Note(time, Math.max(0, Math.min(24, semitone)), length));
            }
            time += length;
        }
        return new Tune(List.copyOf(notes), time);
    }

    record Note(int tick, int semitone, int length) {
    }

    record Tune(List<Note> notes, int length) {
    }
}
