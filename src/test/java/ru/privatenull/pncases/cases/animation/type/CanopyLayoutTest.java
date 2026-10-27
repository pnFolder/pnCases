package ru.privatenull.pncases.cases.animation.type;

import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.animation.type.CanopyLayout.Card;
import ru.privatenull.pncases.cases.animation.type.CanopyLayout.Lobe;
import ru.privatenull.pncases.cases.animation.type.CanopyLayout.Shape;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanopyLayoutTest {

    @Test
    void theTrunkTapersAndEveryLevelOfBranchesIsThinnerThanTheLast() {
        Shape shape = CanopyLayout.tree(7L);

        for (int i = 1; i < 8; i++) {
            assertTrue(shape.trunk().get(i).width() < shape.trunk().get(i - 1).width(), "trunk " + i);
        }
        double trunkTop = shape.trunk().get(7).width();
        double bough = shape.boughs().stream().mapToDouble(CanopyLayout.Segment::width).max().orElseThrow();
        double branch = shape.branches().stream().mapToDouble(CanopyLayout.Segment::width).max().orElseThrow();
        double twig = shape.twigs().stream().mapToDouble(CanopyLayout.Segment::width).max().orElseThrow();
        assertTrue(bough < shape.trunk().get(0).width() && trunkTop > twig);
        assertTrue(branch < bough && twig < branch);
    }

    @Test
    void theTreeHasTheAnnouncedNumberOfParts() {
        Shape shape = CanopyLayout.tree(11L);

        assertEquals(6, shape.roots().size());
        assertEquals(9, shape.boughs().size());
        assertEquals(16, shape.branches().size());
        assertEquals(shape.lobes().size() * 5, shape.twigs().size());
        assertEquals(7, shape.lobes().size());
        for (CanopyLayout.Segment segment : shape.boughs()) assertTrue(segment.length() > 0.5);
    }

    @Test
    void everyLeafCardSitsOnItsLobeShellAndFacesOutward() {
        List<Lobe> lobes = CanopyLayout.tree(3L).lobes();
        int perLobe = 22;
        List<Card> cards = CanopyLayout.foliage(lobes, perLobe, 5L);

        assertEquals(lobes.size() * perLobe, cards.size());
        for (int i = 0; i < cards.size(); i++) {
            Lobe lobe = lobes.get(i / perLobe);
            Card card = cards.get(i);
            double distance = Math.sqrt(Math.pow(card.x() - lobe.x(), 2) + Math.pow(card.y() - lobe.y(), 2)
                    + Math.pow(card.z() - lobe.z(), 2));
            assertTrue(distance <= lobe.radius() + 1.0e-9 && distance >= lobe.radius() * 0.77, "card " + i);
            double normal = Math.sqrt(card.nx() * card.nx() + card.ny() * card.ny() + card.nz() * card.nz());
            assertEquals(1.0, normal, 1.0e-9);
            assertTrue(card.size() >= 0.38 && card.size() <= 0.62);
        }
    }

    @Test
    void leavesAreYellowerLowDownAndRedderHighUp() {
        List<Card> cards = CanopyLayout.foliage(CanopyLayout.tree(3L).lobes(), 200, 9L);

        double yellow = averageHeight(cards, CanopyLayout.SHADE_YELLOW);
        double orange = averageHeight(cards, CanopyLayout.SHADE_ORANGE);
        double red = averageHeight(cards, CanopyLayout.SHADE_RED);
        assertTrue(yellow < orange && orange < red, yellow + " < " + orange + " < " + red);
    }

    @Test
    void theCanopyIsFilledInsideAsWellAsOnTheShell() {
        List<Lobe> lobes = CanopyLayout.tree(3L).lobes();
        List<Card> inner = CanopyLayout.fill(lobes, 8, 4L);

        assertEquals(lobes.size() * 8, inner.size());
        for (int i = 0; i < inner.size(); i++) {
            Lobe lobe = lobes.get(i / 8);
            Card card = inner.get(i);
            double distance = Math.sqrt(Math.pow(card.x() - lobe.x(), 2) + Math.pow(card.y() - lobe.y(), 2)
                    + Math.pow(card.z() - lobe.z(), 2));
            assertTrue(distance <= lobe.radius() * 0.65 + 1.0e-9, "inner card " + i);
        }
    }

    @Test
    void groundLitterStaysInItsRingAndLiesFlat() {
        List<Card> cards = CanopyLayout.litter(90, 0.5, 3.2, 1L);

        assertEquals(90, cards.size());
        for (Card card : cards) {
            double radius = Math.sqrt(card.x() * card.x() + card.z() * card.z());
            assertTrue(radius >= 0.3 && radius <= 3.6, "radius " + radius);
            assertTrue(card.ny() > 0.85 && card.y() < 0.25);
        }
    }

    @Test
    void fruitHangsJustOutsideEveryLobe() {
        List<Lobe> lobes = CanopyLayout.tree(2L).lobes();
        for (int i = 0; i < 8; i++) {
            double angle = TreeLottery.angle(i, 8);
            double height = TreeLottery.height(i, 8, 2.4, 3.7);
            double[] point = CanopyLayout.fruitPoint(lobes, angle, height);

            for (Lobe lobe : lobes) {
                double distance = Math.sqrt(Math.pow(point[0] - lobe.x(), 2) + Math.pow(point[1] - lobe.y(), 2)
                        + Math.pow(point[2] - lobe.z(), 2));
                assertTrue(distance >= lobe.radius() - 1.0e-9, "fruit " + i + " is inside a lobe");
            }
        }
    }

    private static double averageHeight(List<Card> cards, int shade) {
        return cards.stream().filter(card -> card.shade() == shade).mapToDouble(Card::y).average().orElseThrow();
    }
}
