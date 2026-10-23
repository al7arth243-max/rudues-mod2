package com.rudues.mod.world;

import java.util.Random;

/**
 * The Infernal Trial arena, described block by block. Pure Java (no Minecraft classes) so the exact same layout can
 * be generated and previewed outside the game. {@link ArenaBuilder} maps each {@link Blk} to a block state.
 *
 * Layout (centred on x = 0, z = 0; the floor's top surface is at y = FLOOR):
 *   r <= 56          cracked dark stone floor, radial cracks around the centre where the sword stands
 *   56 < r <= 59.3   a channel of lava, flush with the floor
 *   59.3 < r <= 61.5 a lip of netherrack with eternal fire burning on it (the fire circling the arena)
 *   beyond           scorched basalt ground with burning patches, collapsed buildings, rubble and towers;
 *                    the largest, a broken spire with hanging scaffolding, stands directly behind the arena (north)
 */
public final class ArenaLayout {
    public enum Blk {
        AIR, SMOOTH_BASALT, POLISHED_BLACKSTONE, BLACKSTONE, CRACKED_BRICKS, DEEPSLATE, LAVA, NETHERRACK, FIRE, MAGMA,
        BASALT, BLACKSTONE_BRICKS, CRACKED_BLACKSTONE_BRICKS, DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS,
        DEEPSLATE_TILES, POLISHED_BASALT, CHAIN, IRON_BARS, LIGHT, GILDED_BLACKSTONE, CRYING_OBSIDIAN, POLISHED_BLACKSTONE_BRICKS
    }

    public interface Sink {
        void set(int x, int y, int z, Blk b);
    }

    public static final int FLOOR = 64;
    public static final double FLOOR_R = 56.0;
    public static final double LAVA_R = 59.3;
    public static final double FIRE_R = 61.5;
    /** players are kept inside this radius */
    public static final double BOUNDARY_R = 55.0;
    public static final int EXTENT = 175;
    /** where players arrive, facing the ruined spire to the north */
    public static final int SPAWN_X = 0, SPAWN_Z = 46;
    private static final long SEED = 0x1BFE11D5L;

    private ArenaLayout() {
    }

    // ------------------------------------------------------------------ deterministic value noise
    private static double hash(int x, int z, int salt) {
        long h = x * 374761393L + z * 668265263L + salt * 2147483647L + SEED;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    static double noise(double x, double z, double scale, int salt) {
        double fx = x / scale, fz = z / scale;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
        double tx = smooth(fx - x0), tz = smooth(fz - z0);
        double a = hash(x0, z0, salt), b = hash(x0 + 1, z0, salt), c = hash(x0, z0 + 1, salt), d = hash(x0 + 1, z0 + 1, salt);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    static double fbm(double x, double z, double scale, int salt) {
        return 0.55 * noise(x, z, scale, salt) + 0.3 * noise(x, z, scale / 2.3, salt + 7) + 0.15 * noise(x, z, scale / 5.1, salt + 13);
    }

    /** ground top height outside the ring */
    public static int groundTop(int x, int z) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        double rise = Math.min(1.0, Math.max(0.0, (r - FIRE_R) / 18.0));
        return FLOOR + (int) Math.round(rise * (1.0 + 3.0 * fbm(x, z, 26, 3) - 0.6));
    }

    // ------------------------------------------------------------------ build
    public static void build(Sink out) {
        Random rnd = new Random(SEED);
        ground(out, rnd);
        centreCracks(out, rnd);
        spire(out, new Random(SEED + 1), 0, -104);
        buildings(out, new Random(SEED + 2));
        lights(out);
    }

    private static void ground(Sink out, Random rnd) {
        for (int x = -EXTENT; x <= EXTENT; x++) {
            for (int z = -EXTENT; z <= EXTENT; z++) {
                double r = Math.sqrt((double) x * x + (double) z * z);
                if (r > EXTENT) {
                    continue;
                }
                for (int y = FLOOR - 5; y < FLOOR; y++) {
                    out.set(x, y, z, Blk.DEEPSLATE);
                }
                if (r <= FLOOR_R) {
                    out.set(x, FLOOR, z, floorBlock(x, z));
                } else if (r <= LAVA_R) {
                    out.set(x, FLOOR, z, Blk.LAVA);
                } else if (r <= FIRE_R) {
                    out.set(x, FLOOR, z, Blk.NETHERRACK);
                    if (hash(x, z, 41) < 0.86) {
                        out.set(x, FLOOR + 1, z, Blk.FIRE);
                    }
                } else {
                    int top = groundTop(x, z);
                    for (int y = FLOOR; y < top; y++) {
                        out.set(x, y, z, Blk.BLACKSTONE);
                    }
                    double burn = fbm(x, z, 14, 21);
                    double vein = Math.abs(noise(x, z, 9, 33) - 0.5);
                    Blk surface;
                    if (burn > 0.71) {
                        surface = Blk.NETHERRACK;
                    } else if (vein < 0.014 && burn > 0.45) {
                        surface = Blk.MAGMA;
                    } else {
                        surface = hash(x, z, 5) < 0.25 ? Blk.BLACKSTONE : Blk.BASALT;
                    }
                    out.set(x, top, z, surface);
                    if (surface == Blk.NETHERRACK && hash(x, z, 6) < 0.28) {
                        out.set(x, top + 1, z, Blk.FIRE);
                    }
                }
            }
        }
        // scattered rubble outside the ring
        for (int i = 0; i < 260; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 64 + rnd.nextDouble() * 105;
            int cx = (int) Math.round(Math.cos(a) * r), cz = (int) Math.round(Math.sin(a) * r);
            int size = 1 + rnd.nextInt(3);
            Blk m = rnd.nextBoolean() ? Blk.CRACKED_DEEPSLATE_BRICKS : Blk.BLACKSTONE_BRICKS;
            for (int dx = -size; dx <= size; dx++) {
                for (int dz = -size; dz <= size; dz++) {
                    int h = size - Math.max(Math.abs(dx), Math.abs(dz)) + (rnd.nextInt(3) == 0 ? 1 : 0);
                    int top = groundTop(cx + dx, cz + dz);
                    for (int y = 1; y <= h; y++) {
                        out.set(cx + dx, top + y, cz + dz, rnd.nextInt(4) == 0 ? Blk.BLACKSTONE : m);
                    }
                }
            }
        }
    }

    private static Blk floorBlock(int x, int z) {
        double n = fbm(x, z, 11, 1);
        double crack = Math.abs(noise(x, z, 7.5, 2) - 0.5);
        if (crack < 0.022) {
            return Blk.CRACKED_BRICKS;
        }
        if (n > 0.66) {
            return Blk.POLISHED_BLACKSTONE;
        }
        if (n < 0.33) {
            return Blk.BLACKSTONE;
        }
        return Blk.SMOOTH_BASALT;
    }

    /** a worn circle and radial cracks around the centre, where the sword appears */
    private static void centreCracks(Sink out, Random rnd) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= 3.6) {
                    out.set(x, FLOOR, z, r < 1.0 ? Blk.CRYING_OBSIDIAN : (r <= 2.4 ? Blk.POLISHED_BLACKSTONE_BRICKS : Blk.CRACKED_BLACKSTONE_BRICKS));
                }
            }
        }
        for (int i = 0; i < 7; i++) {
            double a = i * (Math.PI * 2 / 7) + rnd.nextDouble() * 0.5;
            double len = 10 + rnd.nextDouble() * 12;
            double px = 0, pz = 0;
            for (double s = 3.5; s < len; s += 0.7) {
                a += (rnd.nextDouble() - 0.5) * 0.25;
                px = Math.cos(a) * s;
                pz = Math.sin(a) * s;
                out.set((int) Math.round(px), FLOOR, (int) Math.round(pz), s < len * 0.35 ? Blk.GILDED_BLACKSTONE : Blk.CRACKED_BRICKS);
            }
        }
    }

    // ------------------------------------------------------------------ the great broken spire behind the arena
    private static void spire(Sink out, Random rnd, int cx, int cz) {
        int baseR = 13, wallT = 3;
        int base = groundTop(cx, cz);
        int fullTop = FLOOR + 86;
        for (int x = -baseR - 2; x <= baseR + 2; x++) {
            for (int z = -baseR - 2; z <= baseR + 2; z++) {
                double r = Math.sqrt(x * x + z * z);
                double ang = Math.atan2(z, x);
                // the spire is broken off: a big wedge has collapsed on the arena side, the rest is jagged
                double facing = Math.cos(ang - Math.PI / 2);               // +1 = side facing the arena (south)
                int top = fullTop - (int) (Math.max(0, facing) * 38) - (int) (fbm(ang * 9, 0, 1.3, 51) * 16);
                // flared base
                double rr = baseR;
                for (int y = base; y <= top; y++) {
                    double taper = rr - (y - base) * 0.045;
                    double outer = taper + (y < base + 6 ? (base + 6 - y) * 0.35 : 0);
                    if (r > outer || r < outer - wallT) {
                        continue;
                    }
                    int ring = (y - base) % 12;
                    boolean window = ring >= 5 && ring <= 8 && Math.abs(Math.sin(ang * 6)) < 0.22 && r > outer - 1.5;
                    boolean breach = facing > 0.5 && y > base + 18 && fbm(ang * 12, y, 2.0, 61) > 0.62;
                    if (window || breach) {
                        continue;
                    }
                    Blk m;
                    if (Math.abs(Math.sin(ang * 4)) < 0.12 && r > outer - 1.5) {
                        m = Blk.POLISHED_BASALT;
                    } else if (ring == 0) {
                        m = Blk.DEEPSLATE_TILES;
                    } else {
                        m = hash(x * 3 + y, z, 71) < 0.3 ? Blk.CRACKED_DEEPSLATE_BRICKS : Blk.DEEPSLATE_BRICKS;
                    }
                    out.set(cx + x, y, cz + z, m);
                }
                // fire burning on the broken rim
                if (r <= baseR && r >= baseR - wallT - 0.5 && hash(x, z, 77) < 0.35) {
                    int t = top;
                    out.set(cx + x, t, cz + z, Blk.NETHERRACK);
                    out.set(cx + x, t + 1, cz + z, Blk.FIRE);
                }
            }
        }
        // scaffolding and chains hanging from the broken side
        for (int i = 0; i < 9; i++) {
            double ang = Math.PI / 2 + (rnd.nextDouble() - 0.5) * 2.2;
            double r = baseR + 1.5;
            int x = cx + (int) Math.round(Math.cos(ang) * r), z = cz + (int) Math.round(Math.sin(ang) * r);
            int topY = FLOOR + 40 + rnd.nextInt(28);
            int len = 8 + rnd.nextInt(18);
            for (int y = topY; y > topY - len; y--) {
                out.set(x, y, z, i % 3 == 0 ? Blk.IRON_BARS : Blk.CHAIN);
            }
        }
        // a few collapsed slabs of the spire lying in front of it
        for (int i = 0; i < 14; i++) {
            int x = cx + rnd.nextInt(34) - 17, z = cz + baseR + 3 + rnd.nextInt(14);
            int w = 2 + rnd.nextInt(4), h = 1 + rnd.nextInt(4);
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < 2 + rnd.nextInt(2); dz++) {
                    int g = groundTop(x + dx, z + dz);
                    for (int y = 1; y <= h; y++) {
                        out.set(x + dx, g + y, z + dz, rnd.nextInt(3) == 0 ? Blk.CRACKED_DEEPSLATE_BRICKS : Blk.DEEPSLATE_BRICKS);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ burning, destroyed buildings all around
    private static void buildings(Sink out, Random rnd) {
        int placed = 0, tries = 0;
        int[][] spots = new int[40][3];
        while (placed < 30 && tries++ < 600) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 74 + rnd.nextDouble() * 92;
            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
            // leave room around the spire and keep the view from the arrival point open-ish
            if (Math.hypot(x, z + 104) < 30) {
                continue;
            }
            int w = 7 + rnd.nextInt(10), d = 7 + rnd.nextInt(10);
            boolean ok = true;
            for (int i = 0; i < placed; i++) {
                if (Math.abs(spots[i][0] - x) < (spots[i][2] + w) / 2 + 5 && Math.abs(spots[i][1] - z) < (spots[i][2] + d) / 2 + 5) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            spots[placed] = new int[]{x, z, Math.max(w, d)};
            placed++;
            // taller buildings toward the back (north) and the sides, like the reference
            double back = Math.max(0, -Math.sin(a));
            int h = 8 + rnd.nextInt(12) + (int) (back * (14 + rnd.nextInt(26)));
            boolean round = rnd.nextInt(4) == 0;
            ruin(out, rnd, x, z, w, d, h, round);
        }
    }

    private static void ruin(Sink out, Random rnd, int cx, int cz, int w, int d, int h, boolean round) {
        boolean deep = rnd.nextBoolean();
        Blk wall = deep ? Blk.DEEPSLATE_BRICKS : Blk.BLACKSTONE_BRICKS;
        Blk cracked = deep ? Blk.CRACKED_DEEPSLATE_BRICKS : Blk.CRACKED_BLACKSTONE_BRICKS;
        int salt = rnd.nextInt(10000);
        int hx = w / 2, hz = d / 2;
        int base = groundTop(cx, cz);
        for (int x = -hx; x <= hx; x++) {
            for (int z = -hz; z <= hz; z++) {
                boolean shell;
                if (round) {
                    double rr = Math.sqrt(x * x + z * z);
                    shell = rr <= hx && rr > hx - 1.6;
                } else {
                    shell = Math.abs(x) == hx || Math.abs(z) == hz;
                }
                if (!shell) {
                    continue;
                }
                // jagged, collapsed top: each wall column breaks off at its own height
                double jag = fbm(cx + x * 1.0, cz + z * 1.0, 3.2, salt);
                int top = base + (int) (h * (0.25 + 0.75 * jag));
                if (jag < 0.3) {
                    top = base + 1 + rnd.nextInt(3);        // a fully collapsed section
                }
                boolean corner = !round && Math.abs(x) == hx && Math.abs(z) == hz;
                for (int y = base + 1; y <= top; y++) {
                    int ring = (y - base) % 7;
                    boolean window = !corner && ring >= 3 && ring <= 4 && ((x + z) & 3) == 1;
                    if (window) {
                        if (rnd.nextInt(3) == 0) {
                            out.set(cx + x, y, cz + z, Blk.IRON_BARS);
                        }
                        continue;
                    }
                    Blk m = corner ? Blk.POLISHED_BASALT : (ring == 0 ? Blk.DEEPSLATE_TILES : (rnd.nextInt(10) < 3 ? cracked : wall));
                    out.set(cx + x, y, cz + z, m);
                }
                if (top > base + 4 && rnd.nextInt(5) == 0) {
                    out.set(cx + x, top, cz + z, Blk.NETHERRACK);
                    out.set(cx + x, top + 1, cz + z, Blk.FIRE);
                }
            }
        }
        // rubble and fire inside the shell
        for (int i = 0; i < (w * d) / 6; i++) {
            int x = cx + rnd.nextInt(Math.max(1, w - 2)) - hx + 1, z = cz + rnd.nextInt(Math.max(1, d - 2)) - hz + 1;
            int g = groundTop(x, z);
            int hh = 1 + rnd.nextInt(3);
            for (int y = 1; y <= hh; y++) {
                out.set(x, g + y, z, rnd.nextBoolean() ? cracked : Blk.BLACKSTONE);
            }
            if (rnd.nextInt(3) == 0) {
                out.set(x, g + hh + 1, z, Blk.NETHERRACK);
                out.set(x, g + hh + 2, z, Blk.FIRE);
            }
        }
    }

    /** invisible light sources over the arena so the fight is readable in the gloom */
    private static void lights(Sink out) {
        for (int x = -50; x <= 50; x += 10) {
            for (int z = -50; z <= 50; z += 10) {
                if (Math.sqrt(x * x + z * z) <= 52) {
                    out.set(x, FLOOR + 7, z, Blk.LIGHT);
                }
            }
        }
    }

    /** standalone preview export: java ArenaLayout out.bin  (x,y,z as shorts, block as byte) */
    public static void main(String[] args) throws Exception {
        java.io.DataOutputStream os = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(args[0])));
        int[] n = {0};
        build((x, y, z, b) -> {
            try {
                os.writeShort(x);
                os.writeShort(y);
                os.writeShort(z);
                os.writeByte(b.ordinal());
                n[0]++;
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        os.close();
        System.out.println("blocks written: " + n[0]);
    }
}
