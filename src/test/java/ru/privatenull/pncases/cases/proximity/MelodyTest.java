package ru.privatenull.pncases.cases.proximity;

import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.model.ProximitySettings;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MelodyTest {

    private static final int[] MINOR = {0, 2, 3, 5, 7, 8, 10};

    @Test
    void parsesDegreesOctavesAndRests() {
        WinterMusic.Tune tune = WinterMusic.parse("1:8 R:4 3:8 1H:4", 5, MINOR);

        assertEquals(24, tune.length());
        assertEquals(3, tune.notes().size());
        assertEquals(new WinterMusic.Note(0, 5, 8), tune.notes().get(0));
        assertEquals(new WinterMusic.Note(12, 8, 8), tune.notes().get(1));
        assertEquals(new WinterMusic.Note(20, 17, 4), tune.notes().get(2));
    }

    @Test
    void notesStayInsideTheNoteBlockRange() {
        for (WinterMusic.Tune tune : List.of(WinterMusic.tune(0), WinterMusic.tune(1),
                AutumnMusic.tune(0), AutumnMusic.tune(1))) {
            assertTrue(tune.length() > 0);
            for (WinterMusic.Note note : tune.notes()) {
                assertTrue(note.semitone() >= 0 && note.semitone() <= 24, "semitone " + note.semitone());
                assertTrue(note.tick() < tune.length());
            }
        }
    }

    @Test
    void autumnHasItsOwnTunes() {
        assertEquals(2, AutumnMusic.tunes());
        assertEquals(AutumnMusic.tune(0), AutumnMusic.tune(2));
    }

    @Test
    void caseStylesFollowTheScene() {
        List<ProximitySettings.CaseStyle> autumn = ProximitySettings.CaseStyle.forScene(ProximitySettings.Scene.AUTUMN);
        List<ProximitySettings.CaseStyle> winter = ProximitySettings.CaseStyle.forScene(ProximitySettings.Scene.WINTER);

        assertTrue(autumn.contains(ProximitySettings.CaseStyle.BASKET));
        assertTrue(!autumn.contains(ProximitySettings.CaseStyle.GIFT));
        assertTrue(winter.contains(ProximitySettings.CaseStyle.GIFT));
        assertTrue(!winter.contains(ProximitySettings.CaseStyle.BASKET));
    }
}
