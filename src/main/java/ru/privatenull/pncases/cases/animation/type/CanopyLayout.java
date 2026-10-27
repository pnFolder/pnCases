package ru.privatenull.pncases.cases.animation.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure geometry of the autumn tree: a tapering, leaning trunk, roots, three levels of branches, the lobes of the
 * canopy, and the thin leaf cards that cover each lobe. Everything is plain numbers, so the shape can be tested
 * without a server; the animation only turns it into display entities.
 */
final class CanopyLayout {

    /** The bottom and the top of the canopy, used to colour cards by height. */
    static final double CANOPY_LOW = 1.9;
    static final double CANOPY_HIGH = 5.0;

    static final int SHADE_YELLOW = 0;
    static final int SHADE_ORANGE = 1;
    static final int SHADE_RED = 2;
    static final int SHADE_BROWN = 3;

    private CanopyLayout() {
    }

    /** A straight piece of wood from one point to another. */
    record Segment(double x0, double y0, double z0, double x1, double y1, double z1, double width) {
        double length() {
            double dx = x1 - x0;
            double dy = y1 - y0;
            double dz = z1 - z0;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** A rounded clump of foliage. */
    record Lobe(double x, double y, double z, double radius) {
    }

    /** A thin leaf: its centre, size, the direction it faces and its turn about that direction. */
    record Card(double x, double y, double z, double size, double nx, double ny, double nz, double roll, int shade) {
    }

    record Shape(List<Segment> trunk, List<Segment> roots, List<Segment> boughs, List<Segment> branches,
                 List<Segment> twigs, List<Lobe> lobes) {
    }

    static Shape tree(long seed) {
        Random random = new Random(seed);
        List<Segment> trunk = new ArrayList<>();
        List<Segment> roots = new ArrayList<>();
        List<Segment> boughs = new ArrayList<>();
        List<Segment> branches = new ArrayList<>();
        List<Segment> twigs = new ArrayList<>();
        List<Lobe> lobes = new ArrayList<>();

        int steps = 8;
        for (int i = 0; i < steps; i++) {
            double t0 = i / (double) steps;
            double t1 = (i + 1) / (double) steps;
            double width = 0.62 - 0.32 * (t0 + t1) / 2.0;
            trunk.add(new Segment(trunkX(t0), 2.3 * t0, trunkZ(t0), trunkX(t1), 2.3 * t1, trunkZ(t1), width));
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI * 2.0 / 6.0 + random.nextDouble(0.4);
            double reach = 0.7 + random.nextDouble(0.35);
            roots.add(new Segment(0.12 * Math.cos(a), 0.32, 0.12 * Math.sin(a),
                    reach * Math.cos(a), 0.0, reach * Math.sin(a), 0.22 - 0.03 * random.nextDouble()));
        }

        double topX = trunkX(1.0);
        double topZ = trunkZ(1.0);
        trunk.add(new Segment(topX, 2.3, topZ, 0.12, 2.9, 0.05, 0.26));
        trunk.add(new Segment(0.12, 2.9, 0.05, 0.1, 3.7, 0.0, 0.17));
        lobes.add(new Lobe(0.1, 4.0, 0.0, 1.25));
        lobes.add(new Lobe(0.0, 3.3, 0.0, 1.55));

        List<double[]> boughPoints = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2.0 / 3.0 + 0.5 + random.nextDouble(0.3);
            double t = 0.72 + 0.08 * k;
            double dx = Math.cos(a);
            double dz = -Math.sin(a);
            double[] p0 = {trunkX(t), 2.3 * t, trunkZ(t)};
            double[] p1 = {p0[0] + dx * 0.55, p0[1] + 0.45, p0[2] + dz * 0.55};
            double[] p2 = {p1[0] + dx * 0.5, p1[1] + 0.5, p1[2] + dz * 0.5};
            double[] p3 = {p2[0] + dx * 0.45, p2[1] + 0.5, p2[2] + dz * 0.45};
            boughs.add(new Segment(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0.26));
            boughs.add(new Segment(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], 0.2));
            boughs.add(new Segment(p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], 0.14));
            boughPoints.add(p1);
            boughPoints.add(p2);
            lobes.add(new Lobe(p3[0], p3[1] + 0.1, p3[2], 1.25 + random.nextDouble(0.15)));
        }
        // Two lower lobes between the boughs round off the skirt of the canopy.
        for (int k = 0; k < 2; k++) {
            double a = Math.PI * 2.0 * (k + 0.5) / 2.0 + 1.0 + random.nextDouble(0.4);
            lobes.add(new Lobe(Math.cos(a) * 1.15, 2.75, -Math.sin(a) * 1.15, 1.05));
        }

        List<double[]> branchEnds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double[] start = boughPoints.get(i % boughPoints.size());
            double a = random.nextDouble(Math.PI * 2.0);
            double dx = Math.cos(a);
            double dz = -Math.sin(a);
            double[] mid = {start[0] + dx * 0.5, start[1] + 0.3, start[2] + dz * 0.5};
            double[] end = {mid[0] + dx * 0.4, mid[1] + 0.25 + random.nextDouble(0.2), mid[2] + dz * 0.4};
            branches.add(new Segment(start[0], start[1], start[2], mid[0], mid[1], mid[2], 0.1));
            branches.add(new Segment(mid[0], mid[1], mid[2], end[0], end[1], end[2], 0.07));
            branchEnds.add(end);
        }
        // Spokes: from the heart of every lobe out to its leaves, so the leaves hang on wood you can see.
        for (Lobe lobe : lobes) {
            for (int i = 0; i < 5; i++) {
                double a = random.nextDouble(Math.PI * 2.0);
                double v = random.nextDouble(-0.2, 0.9);
                double ring = Math.sqrt(1.0 - v * v);
                double reach = lobe.radius() * (0.7 + random.nextDouble(0.2));
                twigs.add(new Segment(lobe.x(), lobe.y(), lobe.z(), lobe.x() + Math.cos(a) * ring * reach,
                        lobe.y() + v * reach, lobe.z() - Math.sin(a) * ring * reach, 0.06));
            }
        }
        return new Shape(trunk, roots, boughs, branches, twigs, lobes);
    }

    private static double trunkX(double t) {
        return 0.12 * Math.sin(t * 3.0);
    }

    private static double trunkZ(double t) {
        return 0.09 * Math.cos(t * 2.4);
    }

    /** Leaf cards on the shell of every lobe, each facing out of it and drooping a little. */
    static List<Card> foliage(List<Lobe> lobes, int perLobe, long seed) {
        Random random = new Random(seed);
        List<Card> cards = new ArrayList<>();
        for (Lobe lobe : lobes) {
            for (int i = 0; i < perLobe; i++) {
                double a = random.nextDouble(Math.PI * 2.0);
                double v = Math.min(1.0, -0.6 + random.nextDouble() * 1.6);
                double ring = Math.sqrt(Math.max(0.0, 1.0 - v * v));
                double ux = Math.cos(a) * ring;
                double uy = v;
                double uz = Math.sin(a) * ring;
                double r = lobe.radius() * (0.78 + random.nextDouble(0.22));
                double x = lobe.x() + ux * r;
                double y = lobe.y() + uy * r;
                double z = lobe.z() + uz * r;
                double nx = ux;
                double ny = uy - 0.3;
                double nz = uz;
                double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double yFraction = (y - CANOPY_LOW) / (CANOPY_HIGH - CANOPY_LOW);
                cards.add(new Card(x, y, z, 0.38 + random.nextDouble(0.24), nx / length, ny / length, nz / length,
                        Math.PI / 4.0 + random.nextDouble(-0.4, 0.4), shade(yFraction, random)));
            }
        }
        return cards;
    }

    /** Leaves inside every lobe, so the canopy is full and you cannot see through it. */
    static List<Card> fill(List<Lobe> lobes, int perLobe, long seed) {
        Random random = new Random(seed);
        List<Card> cards = new ArrayList<>();
        for (Lobe lobe : lobes) {
            for (int i = 0; i < perLobe; i++) {
                double a = random.nextDouble(Math.PI * 2.0);
                double v = random.nextDouble(-0.7, 1.0);
                double ring = Math.sqrt(Math.max(0.0, 1.0 - v * v));
                double r = lobe.radius() * (0.3 + random.nextDouble(0.35));
                double ux = Math.cos(a) * ring;
                double uz = Math.sin(a) * ring;
                double y = lobe.y() + v * r;
                double yFraction = (y - CANOPY_LOW) / (CANOPY_HIGH - CANOPY_LOW);
                cards.add(new Card(lobe.x() + ux * r, y, lobe.z() + uz * r, 0.4 + random.nextDouble(0.25),
                        ux, v, uz, random.nextDouble(Math.PI), shade(yFraction, random)));
            }
        }
        return cards;
    }

    /**
     * Fallen leaves on the ground: a loose scatter, thickest round the trunk, and several heaps where the leaves
     * lie on top of each other and tilt.
     */
    static List<Card> litter(int count, double inner, double outer, long seed) {
        Random random = new Random(seed);
        List<Card> cards = new ArrayList<>();
        int heaps = 6;
        int perHeap = Math.min(10, count / (heaps * 2));
        for (int h = 0; h < heaps; h++) {
            double a = h * Math.PI * 2.0 / heaps + random.nextDouble(0.5);
            double r = inner + 0.4 + random.nextDouble(outer - inner - 0.8);
            double cx = Math.cos(a) * r;
            double cz = Math.sin(a) * r;
            for (int i = 0; i < perHeap; i++) {
                double da = random.nextDouble(Math.PI * 2.0);
                double dr = Math.abs(random.nextGaussian()) * 0.22;
                double tilt = random.nextDouble(-0.5, 0.5);
                double length = Math.sqrt(1.0 + tilt * tilt);
                cards.add(new Card(cx + Math.cos(da) * dr, 0.03 + random.nextDouble(0.16) * Math.max(0.0, 1.0 - dr * 3.0),
                        cz + Math.sin(da) * dr, 0.34 + random.nextDouble(0.24), tilt / length, 1.0 / length, 0.0,
                        random.nextDouble(Math.PI), pickLitterShade(random)));
            }
        }
        while (cards.size() < count) {
            double a = random.nextDouble(Math.PI * 2.0);
            double r = inner + (outer - inner) * Math.pow(random.nextDouble(), 1.6);
            double tilt = random.nextDouble(-0.25, 0.25);
            double length = Math.sqrt(1.0 + tilt * tilt);
            cards.add(new Card(Math.cos(a) * r, 0.02 + random.nextDouble(0.03), Math.sin(a) * r,
                    0.34 + random.nextDouble(0.24), tilt / length, 1.0 / length, 0.0, random.nextDouble(Math.PI),
                    pickLitterShade(random)));
        }
        return cards;
    }

    private static int pickLitterShade(Random random) {
        double pick = random.nextDouble();
        return pick < 0.25 ? SHADE_YELLOW : pick < 0.6 ? SHADE_ORANGE : pick < 0.8 ? SHADE_RED : SHADE_BROWN;
    }

    /** Yellow low in the canopy, orange in the middle, red on top, with a few brown leaves everywhere. */
    static int shade(double yFraction, Random random) {
        if (random.nextDouble() < 0.07) return SHADE_BROWN;
        double value = yFraction + random.nextDouble(-0.18, 0.18);
        return value < 0.3 ? SHADE_YELLOW : value < 0.62 ? SHADE_ORANGE : SHADE_RED;
    }

    /**
     * A point just outside the canopy at the given angle and height: the ray from the trunk axis outwards, past
     * the far side of every lobe it crosses. Fruit hangs here, so it is never buried in the leaves.
     */
    static double[] fruitPoint(List<Lobe> lobes, double angle, double height) {
        double dx = Math.cos(angle);
        double dz = -Math.sin(angle);
        double exit = 0.0;
        for (Lobe lobe : lobes) {
            double ox = -lobe.x();
            double oy = height - lobe.y();
            double oz = -lobe.z();
            double b = ox * dx + oz * dz;
            double c = ox * ox + oy * oy + oz * oz - lobe.radius() * lobe.radius();
            double discriminant = b * b - c;
            if (discriminant < 0.0) continue;
            exit = Math.max(exit, -b + Math.sqrt(discriminant));
        }
        if (exit <= 0.0) exit = 1.5;
        double distance = exit + 0.08;
        return new double[]{dx * distance, height, dz * distance};
    }
}
