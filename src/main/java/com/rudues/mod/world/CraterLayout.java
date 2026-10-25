package com.rudues.mod.world;

/**
 * The Eye of the Storm: a great scorched crater, black ash and cinders with fires burning here and there, ringed by a
 * dark wood. Pure Java (no Minecraft classes) so it can be built in the game and previewed outside it.
 * <pre>
 *   r <= R            the bowl: ash and cinder, magma and fires scattered over it, charred stakes and fallen trunks
 *   R .. R + 7        the rim, thrown up a few blocks by the blast; burnt stumps
 *   R + 7 .. EXTENT   the wood: tall dark spruce and dark oak, a few dead trunks at its edge
 * </pre>
 * The flat world's surface is the top of y = 63 (first air 64); the bowl is carved down into it.
 */
public final class CraterLayout {
    public interface Sink {
        void set(int x, int y, int z, String block);
    }

    public static final int TOP = 63;                // the flat world's surface block
    public static final double R = 64.0;             // the bowl's radius (the Infernal Hero's floor is 56)
    public static final double DEPTH = 9.0;
    public static final int EXTENT = 132;
    /** fighters are kept inside this */
    public static final double BOUNDARY_R = 60.0;
    /** where the hero rises, facing north across the bowl */
    public static final int SPAWN_X = 0, SPAWN_Z = 18;
    private static final long SEED = 0x6A7D1DA5L;

    private CraterLayout() {
    }

    static double hash(int x, int z, int salt) {
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

    /** the surface block's height at (x, z) */
    public static int surface(int x, int z) {
        double r = Math.sqrt(x * (double) x + z * (double) z);
        double n = noise(x, z, 9, 1) - 0.5;
        if (r <= R) {
            double k = r / R;
            return (int) Math.round(TOP - DEPTH * (1 - k * k) + n * 0.8);
        }
        if (r <= R + 8) {
            double u = (r - R) / 8.0;
            return (int) Math.round(TOP + 3.2 * Math.sin(Math.PI * Math.min(1.0, u * 1.2)) * (1 - 0.4 * u) + n);
        }
        return TOP + (noise(x, z, 14, 2) > 0.62 ? 1 : 0);
    }

    public static void build(Sink out) {
        for (int x = -EXTENT; x <= EXTENT; x++) {
            for (int z = -EXTENT; z <= EXTENT; z++) {
                column(out, x, z);
            }
        }
        // the wood: a jittered grid of trees beyond the rim
        for (int gx = -EXTENT; gx <= EXTENT; gx += 5) {
            for (int gz = -EXTENT; gz <= EXTENT; gz += 5) {
                int x = gx + (int) (hash(gx, gz, 11) * 4) - 2, z = gz + (int) (hash(gx, gz, 12) * 4) - 2;
                double r = Math.sqrt(x * (double) x + z * (double) z);
                if (r < R + 9 || Math.abs(x) > EXTENT - 4 || Math.abs(z) > EXTENT - 4) {
                    continue;
                }
                double roll = hash(x, z, 13);
                if (r < R + 15) {
                    if (roll < 0.55) {
                        deadTree(out, x, surface(x, z) + 1, z, 5 + (int) (hash(x, z, 14) * 7));
                    }
                } else if (roll < 0.86) {
                    int h = 11 + (int) (hash(x, z, 15) * 10);
                    if (hash(x, z, 16) < 0.6) {
                        spruce(out, x, surface(x, z) + 1, z, h);
                    } else {
                        darkOak(out, x, surface(x, z) + 1, z, h - 4);
                    }
                }
            }
        }
        // fallen, burnt trunks across the bowl
        for (int i = 0; i < 26; i++) {
            double a = hash(i, 0, 21) * Math.PI * 2, r = 12 + hash(i, 0, 22) * (R - 18);
            int x = (int) (Math.cos(a) * r), z = (int) (Math.sin(a) * r);
            boolean alongX = hash(i, 0, 23) < 0.5;
            int len = 4 + (int) (hash(i, 0, 24) * 5);
            for (int j = 0; j < len; j++) {
                int xx = x + (alongX ? j : 0), zz = z + (alongX ? 0 : j);
                out.set(xx, surface(xx, zz) + 1, zz, "minecraft:stripped_dark_oak_log[axis=" + (alongX ? "x" : "z") + "]");
            }
        }
    }

    private static void column(Sink out, int x, int z) {
        double r = Math.sqrt(x * (double) x + z * (double) z);
        int s = surface(x, z);
        // carve the bowl / raise the rim
        for (int y = s + 1; y <= TOP; y++) {
            out.set(x, y, z, "minecraft:air");
        }
        double n = noise(x, z, 5, 3), h = hash(x, z, 4);
        String top, under;
        if (r <= R + 2) {
            // ash and cinder; darker towards the heart
            top = n < 0.3 ? "minecraft:coarse_dirt" : n < 0.55 ? "minecraft:black_concrete_powder" : n < 0.72 ? "minecraft:blackstone"
                    : n < 0.86 ? "minecraft:smooth_basalt" : "minecraft:tuff";
            if (r < 9 && n > 0.25) {
                top = "minecraft:blackstone";
            }
            under = "minecraft:blackstone";
            // cinders smoulder in patches
            if (h < 0.05 && noise(x, z, 11, 5) > 0.62) {
                top = "minecraft:magma_block";
            }
        } else if (r <= R + 8) {
            top = n < 0.5 ? "minecraft:coarse_dirt" : n < 0.75 ? "minecraft:podzol" : "minecraft:rooted_dirt";
            under = "minecraft:dirt";
        } else {
            top = n < 0.45 ? "minecraft:podzol" : n < 0.8 ? null : "minecraft:coarse_dirt";      // null: the world's own grass
            under = "minecraft:dirt";
        }
        // under the surface: the flat world already has dirt; fill only what the rim raised, and one layer in the bowl
        for (int y = Math.max(s - 3, r <= R + 2 ? s - 1 : TOP + 1); y < s; y++) {
            out.set(x, y, z, under);
        }
        if (top != null) {
            out.set(x, s, z, top);
        }
        // fires here and there in the bowl; charred stakes; ash heaps
        if (r <= R - 2) {
            double f = hash(x, z, 6);
            if (f < 0.006) {
                out.set(x, s, z, "minecraft:netherrack");
                out.set(x, s + 1, z, "minecraft:fire");
            } else if (f < 0.009) {
                out.set(x, s + 1, z, "minecraft:campfire[lit=true,signal_fire=false]");
            } else if (f < 0.015) {
                int hh = 1 + (int) (hash(x, z, 7) * 3);
                for (int y = 1; y <= hh; y++) {
                    out.set(x, s + y, z, y == hh && hash(x, z, 8) < 0.5 ? "minecraft:blackstone_wall" : "minecraft:basalt[axis=y]");
                }
            } else if (f < 0.04) {
                out.set(x, s + 1, z, "minecraft:dead_bush");
            } else if (f < 0.07 && r > 20) {
                out.set(x, s + 1, z, "minecraft:black_carpet");
            }
        } else if (r <= R + 8 && hash(x, z, 9) < 0.02) {
            out.set(x, s + 1, z, "minecraft:dead_bush");
        }
    }

    private static void spruce(Sink out, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            out.set(x, y + i, z, "minecraft:spruce_log[axis=y]");
        }
        // conical, layered foliage
        int top = y + h;
        for (int dy = -h + 4; dy <= 1; dy++) {
            int yy = top + dy;
            double k = (1 - dy) / (double) (h - 2);
            int rad = (int) Math.round(Math.max(0, k * 3.4) * ((dy & 1) == 0 ? 1.0 : 0.75));
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dz * dz <= rad * rad + 1 && !(dx == 0 && dz == 0 && dy < 0)) {
                        out.set(x + dx, yy, z + dz, "minecraft:spruce_leaves[persistent=true]");
                    }
                }
            }
        }
        out.set(x, top + 1, z, "minecraft:spruce_leaves[persistent=true]");
    }

    private static void darkOak(Sink out, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            out.set(x, y + i, z, "minecraft:dark_oak_log[axis=y]");
            out.set(x + 1, y + i, z, "minecraft:dark_oak_log[axis=y]");
            out.set(x, y + i, z + 1, "minecraft:dark_oak_log[axis=y]");
            out.set(x + 1, y + i, z + 1, "minecraft:dark_oak_log[axis=y]");
        }
        int top = y + h;
        for (int dy = -3; dy <= 1; dy++) {
            int rad = dy == 1 ? 2 : dy == -3 ? 3 : 4;
            for (int dx = -rad; dx <= rad + 1; dx++) {
                for (int dz = -rad; dz <= rad + 1; dz++) {
                    double dd = Math.hypot(dx - 0.5, dz - 0.5);
                    if (dd <= rad + 0.3 && hash(x + dx, z + dz + dy * 31, 17) < 0.92) {
                        out.set(x + dx, top + dy, z + dz, "minecraft:dark_oak_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    private static void deadTree(Sink out, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            out.set(x, y + i, z, i < h - 2 || hash(x, z, 18) < 0.5 ? "minecraft:stripped_dark_oak_log[axis=y]" : "minecraft:basalt[axis=y]");
        }
        // a broken branch or two
        if (h > 6) {
            int b = y + h - 3;
            boolean ax = hash(x, z, 19) < 0.5;
            for (int j = 1; j <= 2; j++) {
                out.set(x + (ax ? j : 0), b + (j == 2 ? 1 : 0), z + (ax ? 0 : j), "minecraft:stripped_dark_oak_log[axis=" + (ax ? "x" : "z") + "]");
            }
        }
    }
}
