package com.rudues.mod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Feathered Knight's castle, described block by block. Pure Java (no Minecraft classes) so the exact same layout
 * can be generated and previewed outside the game; {@link CastleBuilder} parses each block string ("minecraft:x[a=b]")
 * into a block state. North is -z. Two hills, split by a gorge and joined by one great arched bridge:
 *
 *   east hill   where they arrive (the court, the exit ring). Its palace is the only way in; the palace's first floor
 *               joins, by a covered walkway, the stair tower at the bridge's east end, which climbs to the deck
 *   the bridge  a single pointed arch over the gorge and its stream, an arcade along the deck
 *   west hill   the stair tower down to the lower ward; the hall whose first floor opens onto the keep's terrace; the
 *               keep - seven storeys whose stairs alternate sides - and its roof, the arena
 */
public final class CastleLayout {
    public interface Sink {
        void set(int x, int y, int z, String block);
    }

    /** a room the castle fills with defenders when someone first walks in */
    public record Room(String name, int x0, int y0, int z0, int x1, int y1, int z1, int[] spawns, String[] mobs) {
    }

    // ---------------------------------------------------------------- the plan
    public static final int G = 64;                 // the forest floor (first air)
    public static final int P = 92;                 // both lower wards
    public static final int U = 104;                // the keep's terrace (west)
    public static final int KX0 = -96, KX1 = -56, KZ0 = -80, KZ1 = -40;  // the keep's footprint (inclusive)
    public static final int KFLOOR = 12;            // storey height inside the keep
    public static final int KSTOREYS = 7;
    public static final int ROOF = U + KFLOOR * KSTOREYS;               // the arena floor's first air (188)
    public static final int ARENA_CX = (KX0 + KX1) / 2, ARENA_CZ = (KZ0 + KZ1) / 2;
    public static final int ARENA_HALF = (KX1 - KX0) / 2 - 3;           // the walkable half-width inside the parapet
    public static final int X_MIN = -150, X_MAX = 150, Z_MIN = -140, Z_MAX = 130, Y_MIN = 40, Y_MAX = 225;
    private static final long SEED = 0x5EA7_4E12L;

    public static final List<Room> ROOMS = new ArrayList<>();

    private CastleLayout() {
    }

    // ---------------------------------------------------------------- the voxel buffer (later writes win)
    private static final int SX = X_MAX - X_MIN + 1, SY = Y_MAX - Y_MIN + 1, SZ = Z_MAX - Z_MIN + 1;
    private static short[] vox;
    private static final List<String> PAL = new ArrayList<>();
    private static final Map<String, Short> PIDX = new HashMap<>();

    private static int idx(int x, int y, int z) {
        return ((x - X_MIN) * SY + (y - Y_MIN)) * SZ + (z - Z_MIN);
    }

    private static boolean in(int x, int y, int z) {
        return x >= X_MIN && x <= X_MAX && y >= Y_MIN && y <= Y_MAX && z >= Z_MIN && z <= Z_MAX;
    }

    static void set(int x, int y, int z, String b) {
        if (!in(x, y, z)) {
            return;
        }
        Short i = PIDX.get(b);
        if (i == null) {
            i = (short) PAL.size();
            PAL.add(b);
            PIDX.put(b, i);
        }
        vox[idx(x, y, z)] = i;
    }

    static String get(int x, int y, int z) {
        if (!in(x, y, z)) {
            return null;
        }
        short i = vox[idx(x, y, z)];
        return i == 0 ? null : PAL.get(i);
    }

    static boolean solid(int x, int y, int z) {
        String b = get(x, y, z);
        return b != null && !b.equals(AIR);
    }

    static void fill(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, b);
                }
            }
        }
    }

    interface Mat {
        String at(int x, int y, int z);
    }

    static void fill(int x0, int y0, int z0, int x1, int y1, int z1, Mat m) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, m.at(x, y, z));
                }
            }
        }
    }

    // ---------------------------------------------------------------- blocks
    static final String AIR = "minecraft:air";
    static final String BRICK = "minecraft:stone_bricks", MOSSY = "minecraft:mossy_stone_bricks", CRACKED = "minecraft:cracked_stone_bricks";
    static final String CHISELED = "minecraft:chiseled_stone_bricks", ANDESITE = "minecraft:polished_andesite";
    static final String COBBLE = "minecraft:cobblestone", MOSSY_COBBLE = "minecraft:mossy_cobblestone";
    static final String STONE = "minecraft:stone", ROCK2 = "minecraft:andesite", ROCK3 = "minecraft:tuff", GRAVEL = "minecraft:gravel";
    static final String GRASS = "minecraft:grass_block", DIRT = "minecraft:dirt", PATH = "minecraft:dirt_path", COARSE = "minecraft:coarse_dirt";
    static final String MOSS = "minecraft:moss_block", PODZOL = "minecraft:podzol";
    static final String TILE = "minecraft:deepslate_tiles", TILE_SLAB = "minecraft:deepslate_tile_slab";
    static final String PLANK = "minecraft:spruce_planks", DARK_PLANK = "minecraft:dark_oak_planks", LOG = "minecraft:spruce_log";
    static final String DARK_LOG = "minecraft:dark_oak_log";
    static final String BARS = "minecraft:iron_bars", GLASS = "minecraft:gray_stained_glass_pane", CHAIN = "minecraft:chain";
    static final String LANTERN_HANG = "minecraft:lantern[hanging=true]", LANTERN = "minecraft:lantern[hanging=false]";
    static final String SOUL_HANG = "minecraft:soul_lantern[hanging=true]";
    static final String SHELF = "minecraft:bookshelf", BARREL = "minecraft:barrel[facing=up]";
    static final String LEAVES_SPRUCE = "minecraft:spruce_leaves[persistent=true]", LEAVES_DARK = "minecraft:dark_oak_leaves[persistent=true]";
    static final String COBWEB = "minecraft:cobweb";
    static final String VINE_N = "minecraft:vine[south=true]";

    static String stairs(String base, String facing, boolean top) {
        return "minecraft:" + base + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    static String slab(String base, boolean top) {
        return "minecraft:" + base + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    // ---------------------------------------------------------------- deterministic noise
    static double hash(int x, int z, int salt) {
        long h = x * 374761393L + z * 668265263L + salt * 2147483647L + SEED;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    static double hash3(int x, int y, int z, int salt) {
        return hash(x * 31 + y * 7919, z * 17 - y * 104729, salt);
    }

    static double noise(double x, double z, double scale, int salt) {
        double fx = x / scale, fz = z / scale;
        int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
        double tx = fx - x0, tz = fz - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = hash(x0, z0, salt), b = hash(x0 + 1, z0, salt), c = hash(x0, z0 + 1, salt), d = hash(x0 + 1, z0 + 1, salt);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    static double fbm(double x, double z, double scale, int salt) {
        return 0.55 * noise(x, z, scale, salt) + 0.3 * noise(x, z, scale / 2.3, salt + 7) + 0.15 * noise(x, z, scale / 5.1, salt + 13);
    }

    /** weathered masonry: mostly stone bricks, mossier low down and on the outside */
    static final Mat WALL = (x, y, z) -> {
        double h = hash3(x, y, z, 1);
        double moss = 0.12 + 0.25 * Math.max(0, Math.min(1, (P + 6 - y) / 12.0)) + 0.1 * noise(x + y, z - y, 9, 4);
        return h < moss ? MOSSY : h < moss + 0.1 ? CRACKED : BRICK;
    };
    static final Mat ROCK = (x, y, z) -> {
        double h = hash3(x, y, z, 2), n = noise(x * 0.7 + y, z * 0.7 - y, 7, 9);
        return n < 0.3 ? ROCK3 : n < 0.62 ? STONE : h < 0.5 ? ROCK2 : "minecraft:cobblestone";
    };

    // ================================================================ build
    public static synchronized void build(Sink out) {
        vox = new short[SX * SY * SZ];
        PAL.clear();
        PIDX.clear();
        PAL.add("<unset>");
        ROOMS.clear();
        terrain();
        forest(new Random(SEED + 1));
        bridge();
        eastSide(new Random(SEED + 2));
        westSide(new Random(SEED + 3));
        keep(new Random(SEED + 6));
        weather(new Random(SEED));
        // emit
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int y = Y_MIN; y <= Y_MAX; y++) {
                for (int z = Z_MIN; z <= Z_MAX; z++) {
                    short i = vox[idx(x, y, z)];
                    if (i != 0) {
                        out.set(x, y, z, PAL.get(i));
                    }
                }
            }
        }
        vox = null;
    }

    // ---------------------------------------------------------------- the two hills and the gorge between them
    static double rect(int x, int z, int x0, int x1, int z0, int z1) {
        double dx = Math.max(Math.max(x0 - x, x - x1), 0), dz = Math.max(Math.max(z0 - z, z - z1), 0);
        double inside = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1));
        return inside > 0 ? Math.sqrt(dx * dx + dz * dz) : inside;
    }

    /** distance outside the nearer plateau's rim (negative inside); the rims are ragged */
    static double rim(int x, int z) {
        double e = rect(x, z, 30, 114, -46, 88), w = rect(x, z, -116, -30, -88, 88);
        return Math.min(e, w) - 4 - 6 * fbm(x, z, 22, 21);
    }

    static boolean west(int x) {
        return x < 0;
    }

    /** top of the ground (the first air above it) */
    static int groundAt(int x, int z) {
        double d = rim(x, z);
        int top = west(x) && z < -20 ? U : P;
        if (d <= 0) {
            return top;
        }
        // the gorge walls are sheer; the outer slopes fall more gently into the forest
        double len = (Math.abs(x) < 34 && z > -50 && z < 92 ? 7 : 18) + 6 * noise(x, z, 30, 23);
        double fall = Math.pow(Math.min(1.0, d / len), 0.75);
        int h = (int) Math.round(top - (top - G) * fall + (1 - fall) * 3 * (noise(x, z, 6, 25) - 0.5));
        return Math.max(h, G + (int) Math.round(2 * fbm(x, z, 40, 27) - 0.8));
    }

    static int streamX(int z) {
        return (int) Math.round(3 * Math.sin(z / 17.0));
    }

    static void terrain() {
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int z = Z_MIN; z <= Z_MAX; z++) {
                int top = groundAt(x, z);
                boolean cliff = rim(x, z) > 0 && top > G + 3;
                for (int y = G - 3; y < top; y++) {
                    String b;
                    if (y == top - 1 && !cliff) {
                        b = top > G + 4 ? (hash(x, z, 31) < 0.12 ? COARSE : GRASS) : (hash(x, z, 33) < 0.18 ? PODZOL : hash(x, z, 34) < 0.08 ? MOSS : GRASS);
                    } else if (y >= top - 3 && !cliff && top <= G + 4) {
                        b = DIRT;
                    } else {
                        b = ROCK.at(x, y, z);
                    }
                    set(x, y, z, b);
                }
                if (cliff && hash(x, z, 35) < 0.35) {
                    set(x, top - 1, z, hash(x, z, 36) < 0.5 ? MOSS : MOSSY_COBBLE);
                }
                // the stream along the bottom of the gorge
                int sx = streamX(z);
                if (Math.abs(x) < 30 && top <= G + 1) {
                    if (Math.abs(x - sx) <= 1) {
                        set(x, G - 1, z, "minecraft:water");
                        set(x, G - 2, z, GRAVEL);
                    } else if (Math.abs(x - sx) == 2) {
                        set(x, G - 1, z, GRAVEL);
                    }
                }
            }
        }
        // the west hill's upper terrace (the keep's ward) stands over its lower ward on a retaining wall
        fill(-114, P, -21, -30, U - 1, -20, WALL);
        for (int x = -114; x <= -30; x += 2) {
            set(x, U, -20, BRICK);
        }
    }

    static void forest(Random r) {
        for (int i = 0; i < 2400; i++) {
            int x = X_MIN + 3 + r.nextInt(SX - 6), z = Z_MIN + 3 + r.nextInt(SZ - 6);
            if (rim(x, z) < 6 || (Math.abs(x) < 22 && z > -30 && z < 46) || Math.abs(x - streamX(z)) < 4) {
                continue;
            }
            tree(r, x, z, r.nextFloat() < 0.3);
        }
    }

    // ---------------------------------------------------------------- the great bridge: one arch over the gorge
    public static final int D = 128;                    // the deck's first air
    static final int BZ0 = 2, BZ1 = 12, ARCH_HALF = 15, ARCH_TOP = 116;

    static boolean underArch(int x, int y) {
        // a tall pointed arch: straight jambs, then two arcs meeting at ARCH_TOP
        int spring = ARCH_TOP - 22;
        if (y < G - 2) {
            return false;
        }
        if (y <= spring) {
            return Math.abs(x) <= ARCH_HALF;
        }
        double r = ARCH_HALF * 1.6, c = r - ARCH_HALF;               // two circles, centres at +-c
        double dy = y - spring;
        double reach = Math.sqrt(Math.max(0, r * r - dy * dy * (r * r - c * c) / (22.0 * 22.0))) - c;
        return Math.abs(x) <= reach;
    }

    static void bridge() {
        for (int x = -24; x <= 24; x++) {
            for (int z = BZ0; z <= BZ1; z++) {
                for (int y = G - 3; y < D; y++) {
                    if (!underArch(x, y)) {
                        set(x, y, z, WALL.at(x, y, z));
                    } else if (y >= G) {
                        set(x, y, z, AIR);
                    }
                }
                set(x, D - 1, z, z == BZ0 || z == BZ1 ? WALL.at(x, D - 1, z) : (hash(x, z, 91) < 0.18 ? MOSSY : ANDESITE));
            }
            // the arcade along both sides of the deck (the reference's row of little arches), crenellated
            for (int z : new int[]{BZ0, BZ1}) {
                for (int y = D; y <= D + 5; y++) {
                    boolean arch = Math.floorMod(x, 4) != 0 && y >= D + 1 && y <= D + 3 && !(y == D + 3 && Math.floorMod(x, 4) != 2);
                    set(x, y, z, arch ? AIR : WALL.at(x, y, z));
                }
                if ((x & 1) == 0) {
                    set(x, D + 6, z, BRICK);
                }
            }
            for (int z = BZ0 + 1; z < BZ1; z++) {
                for (int y = D; y <= D + 6; y++) {
                    set(x, y, z, AIR);
                }
            }
        }
        // the archivolt round both mouths, and string courses
        for (int x = -ARCH_HALF - 1; x <= ARCH_HALF + 1; x++) {
            for (int y = G; y <= ARCH_TOP + 1; y++) {
                if (!underArch(x, y) && (underArch(x - 1, y) || underArch(x + 1, y) || underArch(x, y - 1))) {
                    set(x, y, BZ0 - 1, (y & 1) == 0 ? CHISELED : ANDESITE);
                    set(x, y, BZ1 + 1, (y & 1) == 0 ? CHISELED : ANDESITE);
                }
            }
        }
        for (int x = -24; x <= 24; x++) {
            set(x, D - 2, BZ0 - 1, stairs("stone_brick", "south", true));
            set(x, D - 2, BZ1 + 1, stairs("stone_brick", "north", true));
            set(x, ARCH_TOP + 6, BZ0 - 1, ANDESITE);
            set(x, ARCH_TOP + 6, BZ1 + 1, ANDESITE);
        }
        for (int x = -21; x <= 21; x += 6) {
            set(x, D + 6, BZ0, LANTERN);
            set(x, D + 6, BZ1, LANTERN);
        }
        ROOMS.add(new Room("the bridge", -23, D, BZ0 + 1, 23, D + 5, BZ1 - 1,
                new int[]{-12, D, 6, -4, D, 8, 6, D, 6, 14, D, 8}, new String[]{"skeleton_archer", "vindicator", "skeleton_archer", "zombie_soldier"}));
    }

    /**
     * A square stair tower at a bridge's end: thick walls from the cliff to the top, a switchback stair hugging the
     * walls from yFrom to yTo (with a floor at each), windows, a crenellated top with corner turrets.
     */
    static void stairTower(int x0, int z0, int x1, int z1, int yFrom, int yTo, int top, Random r) {
        int base = Math.min(groundAt(x0, z0), Math.min(groundAt(x1, z1), Math.min(groundAt(x0, z1), groundAt(x1, z0)))) - 3;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean shell = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = base; y < top; y++) {
                    if (shell || y < yFrom) {
                        set(x, y, z, WALL.at(x, y, z));
                    } else if (y == yFrom - 1 || y == yTo - 1 || y == top - 1) {
                        set(x, y, z, ANDESITE);
                    } else {
                        set(x, y, z, AIR);
                    }
                }
                set(x, top, z, ANDESITE);
                if (shell && ((x + z) & 1) == 0) {
                    set(x, top + 1, z, BRICK);
                }
            }
        }
        // the switchback: four flights round the walls, 2 wide, with landings in the corners
        int ix0 = x0 + 1, ix1 = x1 - 1, iz0 = z0 + 1, iz1 = z1 - 1;
        int y = yFrom, side = 0;
        while (y < yTo) {
            int n = Math.min(Math.min(ix1 - ix0, iz1 - iz0) - 4, yTo - y);
            int cx, cz;
            switch (side) {
                case 0 -> {
                    cx = ix0;
                    cz = iz0;
                }
                case 1 -> {
                    cx = ix1 - 1;
                    cz = iz0;
                }
                case 2 -> {
                    cx = ix1 - 1;
                    cz = iz1 - 1;
                }
                default -> {
                    cx = ix0;
                    cz = iz1 - 1;
                }
            }
            if (y > yFrom) {
                fill(cx, y - 1, cz, cx + 1, y - 1, cz + 1, BRICK);
                fill(cx, y, cz, cx + 1, y + 3, cz + 1, AIR);
            }
            switch (side) {
                case 0 -> stairRun(ix0 + 2, y, iz0, 1, 0, n, 2, "stone_brick");
                case 1 -> stairRun(ix1 - 1, y, iz0 + 2, 0, 1, n, 2, "stone_brick");
                case 2 -> stairRun(ix1 - 2, y, iz1 - 1, -1, 0, n, 2, "stone_brick");
                default -> stairRun(ix0, y, iz1 - 2, 0, -1, n, 2, "stone_brick");
            }
            y += n;
            side = (side + 1) % 4;
        }
        // tall slit windows on every face; a chain hangs down the well from the roof, lanterns along it
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        for (int yy = yFrom + 4; yy < top - 4; yy += 8) {
            for (int[] w : new int[][]{{mx, z0}, {mx, z1}, {x0, mz}, {x1, mz}}) {
                fill(w[0], yy, w[1], w[0], yy + 2, w[1], BARS);
            }
        }
        for (int start : new int[]{top - 2, yTo - 2}) {
            for (int yy = start; yy >= yFrom + 3; yy--) {
                if (!AIR.equals(get(mx, yy, mz))) {
                    break;
                }
                set(mx, yy, mz, Math.floorMod(start - yy, 7) == 6 ? LANTERN_HANG : CHAIN);
            }
        }
        for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            roundTower(c[0], c[1], 2, top - 5, top + 6, true);
        }
        for (int x = x0; x <= x1; x++) {
            set(x, top - 1, z0 - 1, stairs("stone_brick", "south", true));
            set(x, top - 1, z1 + 1, stairs("stone_brick", "north", true));
        }
        for (int z = z0; z <= z1; z++) {
            set(x0 - 1, top - 1, z, stairs("stone_brick", "east", true));
            set(x1 + 1, top - 1, z, stairs("stone_brick", "west", true));
        }
    }

    static void doorway(int x, int y, int z, boolean alongX, String facing) {
        // a 3-wide, 3-tall opening through a 1-thick wall with a door in its middle
        if (alongX) {
            fill(x - 1, y, z, x + 1, y + 2, z, AIR);
            set(x - 1, y + 2, z, CHISELED);
            set(x + 1, y + 2, z, CHISELED);
        } else {
            fill(x, y, z - 1, x, y + 2, z + 1, AIR);
            set(x, y + 2, z - 1, CHISELED);
            set(x, y + 2, z + 1, CHISELED);
        }
        door(x, y, z, facing);
    }

    // ---------------------------------------------------------------- the east hill: where they arrive
    public static final int SPAWN_X = 70, SPAWN_Y = P, SPAWN_Z = 66;
    /** the way home: a calcite ring, always open, in the arrival court */
    public static final int EXIT_X = 70, EXIT_Y = P - 1, EXIT_Z = 74;

    static void eastSide(Random r) {
        // curtain walls (the gorge side is broken by the stair tower), towers
        wallX(30, 112, 86, P - 6, P + 14, 1);
        wallX(30, 112, -44, P - 6, P + 14, -1);
        wallZ(-44, 86, 112, P - 6, P + 14, 1);
        wallZ(-44, -2, 30, G, P + 14, -1);
        wallZ(16, 86, 30, G, P + 14, -1);
        roundTower(30, 86, 5, G, P + 24, true);
        roundTower(112, 86, 5, P - 8, P + 22, false);
        roundTower(71, 86, 4, P - 8, P + 20, true);
        roundTower(112, 20, 4, P - 8, P + 20, true);
        roundTower(112, -44, 5, P - 8, P + 24, true);
        roundTower(30, -44, 5, G, P + 26, true);
        roundTower(30, 46, 4, G + 4, P + 20, false);
        // the arrival court: paving, the exit ring, braziers
        for (int x = 34; x <= 108; x++) {
            for (int z = 38; z <= 82; z++) {
                if (Math.abs(x - 64) <= 3 || (z >= 62 && z <= 66) || hash(x, z, 101) < 0.04) {
                    set(x, P - 1, z, hash(x, z, 102) < 0.3 ? COBBLE : hash(x, z, 103) < 0.2 ? MOSSY_COBBLE : ANDESITE);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ax = Math.abs(dx), az = Math.abs(dz);
                if (Math.max(ax, az) == 2 && !(ax == 2 && az == 2)) {
                    set(EXIT_X + dx, EXIT_Y, EXIT_Z + dz, "minecraft:calcite");
                } else if (Math.max(ax, az) <= 1) {
                    set(EXIT_X + dx, EXIT_Y, EXIT_Z + dz, "rudues:castle_portal");
                    set(EXIT_X + dx, EXIT_Y - 1, EXIT_Z + dz, "minecraft:calcite");
                }
            }
        }
        for (int s : new int[]{-4, 4}) {
            set(EXIT_X + s, P, EXIT_Z, "minecraft:polished_blackstone_wall");
            set(EXIT_X + s, P + 1, EXIT_Z, "minecraft:soul_campfire[lit=true]");
        }
        roundWell(92, 56);
        for (int i = 0; i < 16; i++) {
            int x = 36 + r.nextInt(70), z = 40 + r.nextInt(42);
            if (Math.abs(x - 64) < 5 || Math.abs(z - 64) < 4 || (Math.abs(x - EXIT_X) < 6 && Math.abs(z - EXIT_Z) < 6)) {
                continue;
            }
            set(x, P, z, r.nextBoolean() ? BARREL : "minecraft:hay_block");
        }
        // the palace: the only way in is its front door; its first floor joins the stair tower by a covered walkway
        int x0 = 44, x1 = 84, z0 = -8, z1 = 34;
        hall(x0, z0, x1, z1, P, 3, 10, false, DARK_PLANK);
        doorway(64, P, z1, true, "south");
        for (int x = x0 + 6; x < x1 - 2; x += 8) {
            for (int z = z0 + 6; z < z1 - 2; z += 8) {
                fill(x, P, z, x, P + 8, z, "minecraft:polished_deepslate");
                set(x, P + 8, z, "minecraft:chiseled_deepslate");
            }
        }
        for (int z = z0 + 4; z < z1; z += 8) {
            set(x0 + 12, P + 8, z, CHAIN);
            set(x0 + 12, P + 7, z, SOUL_HANG);
            set(x1 - 12, P + 8, z, CHAIN);
            set(x1 - 12, P + 7, z, SOUL_HANG);
        }
        // the grand stair: from the hall's east side up to the first floor
        stairRun(x1 - 8, P, z1 - 4, 0, -1, 10, 4, "dark_oak");
        furnishRow(r, x0 + 2, z0 + 2, x0 + 3, z1 - 2, P, "crates");
        // first floor: a long gallery and a library; the west door to the walkway
        int f1 = P + 10;
        fill(x0 + 1, f1, 12, x1 - 1, f1 + 8, 12, WALL);
        opening(x1 - 16, f1, 12, x1 - 15, f1 + 2, 12);
        furnishRow(r, x0 + 3, z0 + 3, x1 - 3, 10, f1, "shelves");
        for (int z = 14; z < z1 - 1; z++) {
            set(x0 + 20, f1, z, "minecraft:green_carpet");
            set(x0 + 21, f1, z, "minecraft:green_carpet");
        }
        doorway(x0, f1, 7, false, "west");
        // the walkway across to the tower: floored, walled, roofed
        fill(38, f1 - 1, 5, x0, f1 - 1, 9, ANDESITE);
        fill(38, f1, 6, x0, f1 + 3, 8, AIR);
        fill(38, f1, 5, x0, f1 + 3, 5, WALL);
        fill(38, f1, 9, x0, f1 + 3, 9, WALL);
        fill(38, f1 + 4, 5, x0, f1 + 4, 9, slab("stone_brick", false));
        set(41, f1 + 1, 5, BARS);
        set(41, f1 + 1, 9, BARS);
        // the stair tower at the bridge's east end: up from the walkway to the deck
        stairTower(24, 0, 38, 14, f1, D, D + 22, r);
        doorway(38, f1, 7, false, "west");
        doorway(24, D, 7, false, "west");
        // its ground floor is sealed (the court cannot reach it)
        room("palace hall", x0 + 1, P, z0 + 1, x1 - 1, P + 8, z1 - 1, r, 6, "zombie_soldier", "zombie_soldier", "skeleton_archer");
        room("palace gallery", x0 + 1, f1, 13, x1 - 1, f1 + 8, z1 - 1, r, 4, "vindicator", "skeleton_archer");
        room("palace library", x0 + 1, f1, z0 + 1, x1 - 1, f1 + 8, 11, r, 4, "witch", "skeleton_archer");
        room("east stair tower", 25, f1, 1, 37, D - 1, 13, r, 3, "zombie_soldier", "skeleton_archer");
        furnishRow(r, x0 + 2, z0 + 2, x1 - 2, z1 - 2, P + 20, "crates");
        furnishRow(r, x0 + 2, z0 + 2, x1 - 2, z1 - 2, P + 21, "webs");
        // the crown of towers at the top right
        squareTower(86, -40, 102, -24, P - 4, P + 34, true);
        squareTower(94, -26, 108, -12, P - 4, P + 46, true);
        roundTower(90, -12, 5, P - 4, P + 30, true);
        squareTower(76, -42, 86, -32, P - 4, P + 26, true);
        hall(88, -10, 108, 14, P, 2, 7, true, PLANK);
        doorway(98, P, 14, true, "south");
        furnishRow(r, 89, -9, 107, 13, P, "crates");
        room("east hall", 89, P, -9, 107, P + 5, 13, r, 4, "skeleton_archer", "witch");
    }

    // ---------------------------------------------------------------- the west hill: the keep's half
    static void westSide(Random r) {
        // walls: lower ward to P + 14, the upper ward's to U + 12
        wallX(-116, -30, 88, P - 6, P + 14, 1);
        wallZ(-20, 88, -116, P - 6, P + 14, -1);
        wallZ(-88, -20, -116, U - 6, U + 12, -1);
        wallX(-116, -30, -88, U - 6, U + 12, -1);
        wallZ(-88, -22, -30, G, U + 12, 1);
        wallZ(-20, -2, -30, G, P + 14, 1);
        wallZ(16, 88, -30, G, P + 14, 1);
        roundTower(-116, 88, 5, P - 8, P + 22, true);
        roundTower(-73, 88, 4, P - 8, P + 20, false);
        roundTower(-30, 88, 5, G, P + 24, true);
        roundTower(-30, 46, 4, G + 4, P + 20, true);
        roundTower(-116, 34, 4, P - 8, P + 20, true);
        roundTower(-116, -20, 5, P - 8, U + 18, true);
        roundTower(-30, -20, 5, G, U + 18, true);
        squareTower(-121, -93, -111, -83, U - 6, U + 24, true);
        roundTower(-30, -88, 5, G, U + 26, true);
        roundTower(-73, -88, 4, U - 6, U + 18, false);
        // the stair tower at the bridge's west end: from the deck down to the lower ward
        stairTower(-38, 0, -24, 14, P, D, D + 22, r);
        doorway(-24, D, 7, false, "east");
        doorway(-31, P, 14, true, "south");
        room("west stair tower", -37, P, 1, -25, D - 1, 13, r, 3, "zombie_soldier", "skeleton_archer");
        // the lower ward: the yard between the tower's door and the hall up to the terrace
        for (int z = 15; z <= 30; z++) {
            for (int x = -33; x <= -29; x++) {
                set(x, P - 1, z, hash(x, z, 111) < 0.3 ? COBBLE : ANDESITE);
            }
        }
        for (int x = -66; x <= -29; x++) {
            for (int z = 26; z <= 30; z++) {
                set(x, P - 1, z, hash(x, z, 112) < 0.3 ? COBBLE : ANDESITE);
            }
        }
        for (int z = 15; z <= 30; z++) {
            for (int x = -66; x <= -62; x++) {
                set(x, P - 1, z, hash(x, z, 113) < 0.3 ? MOSSY_COBBLE : ANDESITE);
            }
        }
        roundWell(-48, 52);
        room("west ward", -110, P, -14, -34, P + 4, 82, r, 6, "zombie_soldier", "zombie_soldier", "skeleton_archer");
        // barracks along the south wall (optional, loot)
        hall(-110, 40, -84, 82, P, 2, 6, false, PLANK);
        doorway(-84, P, 60, false, "east");
        furnishRow(r, -108, 42, -108, 80, P, "beds");
        furnishRow(r, -107, 42, -87, 80, P, "crates");
        stairRun(-87, P, 42, 0, 1, 6, 2, "spruce");
        opening(-87, P + 5, 42, -86, P + 5, 48);
        furnishRow(r, -108, 42, -86, 80, P + 6, "crates");
        room("west barracks", -109, P, 41, -85, P + 4, 81, r, 5, "zombie_soldier", "skeleton_archer");
        // the hall up to the terrace: in at the south door, up its stair, out of its first floor's north door
        int x0 = -84, x1 = -44, z0 = -16, z1 = 14;
        hall(x0, z0, x1, z1, P, 2, U - P, true, PLANK);
        doorway(-64, P, z1, true, "south");
        for (int x = x0 + 6; x < x1 - 2; x += 8) {
            fill(x, P, 0, x, U - 2, 0, "minecraft:polished_deepslate");
        }
        stairRun(x0 + 4, P, z1 - 3, 0, -1, U - P, 4, "spruce");
        furnishRow(r, x0 + 10, z0 + 2, x1 - 2, z0 + 4, P, "crates");
        furnishRow(r, x0 + 10, z0 + 2, x1 - 2, z1 - 2, U, "shelves");
        doorway(-64, U, z0, true, "north");
        fill(-66, U - 1, -19, -62, U - 1, -17, ANDESITE);
        fill(-66, U, -19, -62, U + 3, -17, AIR);
        fill(-66, U, -20, -62, U, -20, AIR);
        room("west hall", x0 + 1, P, z0 + 1, x1 - 1, U - 2, z1 - 1, r, 5, "vindicator", "zombie_soldier", "skeleton_archer");
        room("west hall library", x0 + 1, U, z0 + 1, x1 - 1, U + 8, z1 - 1, r, 4, "witch", "skeleton_archer");
        // the terrace before the keep
        for (int x = KX0 - 4; x <= -32; x++) {
            for (int z = KZ1 + 1; z <= -22; z++) {
                if (Math.abs(x - (KX0 + KX1) / 2) <= 3 || (z >= -27 && z <= -23 && x <= -60) || hash(x, z, 121) < 0.25) {
                    set(x, U - 1, z, hash(x, z, 122) < 0.3 ? MOSSY_COBBLE : hash(x, z, 123) < 0.3 ? COBBLE : ANDESITE);
                }
            }
        }
        int dx = (KX0 + KX1) / 2;
        for (int s : new int[]{-4, 4}) {
            set(dx + s, U, KZ1 + 3, "minecraft:polished_blackstone_wall");
            set(dx + s, U + 1, KZ1 + 3, "minecraft:soul_campfire[lit=true]");
        }
        room("keep terrace", KX0 - 4, U, KZ1 + 1, -34, U + 4, -22, r, 4, "vindicator", "skeleton_archer");
    }

    // ---------------------------------------------------------------- the forest
    static void tree(Random r, int x, int z, boolean dark) {
        int y = groundAt(x, z);
        if (y > G + 6) {
            return;
        }
        int h = dark ? 6 + r.nextInt(4) : 9 + r.nextInt(9);
        String log = dark ? DARK_LOG : LOG;
        String leaf = dark ? LEAVES_DARK : LEAVES_SPRUCE;
        for (int i = 0; i < h; i++) {
            set(x, y + i, z, log);
            if (dark) {
                set(x + 1, y + i, z, log);
                set(x, y + i, z + 1, log);
                set(x + 1, y + i, z + 1, log);
            }
        }
        if (dark) {
            for (int dy = h - 3; dy <= h + 1; dy++) {
                int rr = dy >= h ? 2 : 3;
                for (int dx = -rr; dx <= rr + 1; dx++) {
                    for (int dz = -rr; dz <= rr + 1; dz++) {
                        if (Math.abs(dx - 0.5) + Math.abs(dz - 0.5) <= rr + 1.2 && r.nextFloat() < 0.92 && get(x + dx, y + dy, z + dz) == null) {
                            set(x + dx, y + dy, z + dz, leaf);
                        }
                    }
                }
            }
        } else {
            // a spruce cone
            for (int dy = 3; dy <= h + 1; dy++) {
                double k = (h + 1 - dy) / (double) (h - 2);
                int rr = (int) Math.round(0.6 + 3.2 * k * (dy % 2 == 0 ? 1.0 : 0.75));
                for (int dx = -rr; dx <= rr; dx++) {
                    for (int dz = -rr; dz <= rr; dz++) {
                        if (dx * dx + dz * dz <= rr * rr + 1 && (dx != 0 || dz != 0 || dy > h) && get(x + dx, y + dy, z + dz) == null) {
                            set(x + dx, y + dy, z + dz, leaf);
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- walls and towers
    static void crenels(int x0, int z0, int x1, int z1, int y) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (((x + z) & 1) == 0) {
                    set(x, y, z, BRICK);
                }
            }
        }
    }

    /** a straight curtain wall along x or z, walk on top, crenellated outer face */
    static void wallX(int x0, int x1, int z, int y0, int top, int outward) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            fill(x, y0, z - 1, x, top - 1, z + 1, WALL);
            set(x, top - 1, z, ANDESITE);
            set(x, top, z + outward, WALL.at(x, top, z + outward));
            if ((x & 1) == 0) {
                set(x, top + 1, z + outward, BRICK);
            }
            if (Math.floorMod(x, 7) == 0) {
                set(x, top - 6, z + outward, AIR);     // arrow slits
                set(x, top - 5, z + outward, AIR);
            }
        }
    }

    static void wallZ(int z0, int z1, int x, int y0, int top, int outward) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            fill(x - 1, y0, z, x + 1, top - 1, z, WALL);
            set(x, top - 1, z, ANDESITE);
            set(x + outward, top, z, WALL.at(x + outward, top, z));
            if ((z & 1) == 0) {
                set(x + outward, top + 1, z, BRICK);
            }
            if (Math.floorMod(z, 7) == 0) {
                set(x + outward, top - 6, z, AIR);
                set(x + outward, top - 5, z, AIR);
            }
        }
    }

    /** a round tower, hollow, with floors and a crenellated top or a cone roof */
    static void roundTower(int cx, int cz, int r, int y0, int top, boolean cone) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > r + 0.4) {
                    continue;
                }
                boolean shell = d > r - 1.6;
                for (int y = y0; y < top; y++) {
                    set(x, y, z, shell ? WALL.at(x, y, z) : (y - y0) % 7 == 0 ? PLANK : AIR);
                }
                set(x, top, z, ANDESITE);
                if (shell && !cone && ((x + z) & 1) == 0) {
                    set(x, top + 1, z, BRICK);
                }
            }
        }
        // a ring of corbels under the parapet and narrow windows
        for (int a = 0; a < 360; a += 30) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.cos(rad) * (r + 0.6)), z = cz + (int) Math.round(Math.sin(rad) * (r + 0.6));
            set(x, top - 1, z, stairs("stone_brick", "north", true));
            for (int y = y0 + 6; y < top - 3; y += 9) {
                int wx = cx + (int) Math.round(Math.cos(rad) * r), wz = cz + (int) Math.round(Math.sin(rad) * r);
                if (a % 60 == 0) {
                    set(wx, y, wz, BARS);
                    set(wx, y + 1, wz, BARS);
                }
            }
        }
        if (cone) {
            int h = r * 3;
            for (int dy = 0; dy <= h; dy++) {
                double rr = (r + 1) * (1 - dy / (double) h);
                for (int x = cx - r - 1; x <= cx + r + 1; x++) {
                    for (int z = cz - r - 1; z <= cz + r + 1; z++) {
                        double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                        if (d <= rr + 0.3 && d > rr - 1.3) {
                            set(x, top + 1 + dy, z, TILE);
                        }
                    }
                }
            }
            set(cx, top + h + 2, cz, "minecraft:lightning_rod");
        }
    }

    /** a square tower (hollow, floors every 7) with a crenellated top and corner bartizans */
    static void squareTower(int x0, int z0, int x1, int z1, int y0, int top, boolean turrets) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean shell = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = y0; y < top; y++) {
                    set(x, y, z, shell ? WALL.at(x, y, z) : (y - y0) % 7 == 0 ? PLANK : AIR);
                }
                set(x, top, z, ANDESITE);
                if (shell && ((x + z) & 1) == 0) {
                    set(x, top + 1, z, BRICK);
                }
            }
        }
        // string course and windows
        for (int x = x0; x <= x1; x++) {
            set(x, top - 1, z0 - 1, stairs("stone_brick", "south", true));
            set(x, top - 1, z1 + 1, stairs("stone_brick", "north", true));
        }
        for (int z = z0; z <= z1; z++) {
            set(x0 - 1, top - 1, z, stairs("stone_brick", "east", true));
            set(x1 + 1, top - 1, z, stairs("stone_brick", "west", true));
        }
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        for (int y = y0 + 4; y < top - 3; y += 7) {
            for (int[] w : new int[][]{{mx, z0}, {mx, z1}, {x0, mz}, {x1, mz}}) {
                set(w[0], y, w[1], BARS);
                set(w[0], y + 1, w[1], BARS);
            }
        }
        if (turrets) {
            for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
                roundTower(c[0], c[1], 2, top - 4, top + 5, true);
            }
        }
    }

    static void roundWell(int cx, int cz) {
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= 2.4) {
                    set(x, P, z, d > 1.4 ? MOSSY_COBBLE : AIR);
                    if (d <= 1.4) {
                        fill(x, P - 8, z, x, P - 1, z, AIR);
                        set(x, P - 9, z, "minecraft:water");
                    }
                }
            }
        }
        set(cx - 2, P + 1, cz, "minecraft:cobblestone_wall");
        set(cx + 2, P + 1, cz, "minecraft:cobblestone_wall");
        set(cx - 2, P + 2, cz, "minecraft:cobblestone_wall");
        set(cx + 2, P + 2, cz, "minecraft:cobblestone_wall");
        fill(cx - 2, P + 3, cz, cx + 2, P + 3, cz, slab("spruce", false));
        set(cx, P + 2, cz, CHAIN);
    }

    // ---------------------------------------------------------------- a generic hall: walls, floors, windows, gable roof
    static void hall(int x0, int z0, int x1, int z1, int y0, int storeys, int storeyH, boolean ridgeAlongX, String floor) {
        int top = y0 + storeys * storeyH;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean shell = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = y0 - 1; y < top; y++) {
                    if (shell) {
                        set(x, y, z, WALL.at(x, y, z));
                    } else {
                        set(x, y, z, (y - y0 + 1) % storeyH == 0 ? floor : AIR);
                    }
                }
                set(x, top, z, shell ? CHISELED : floor);
            }
        }
        // windows: pairs of tall slots in every storey, glazed, with a sill
        for (int s = 0; s < storeys; s++) {
            int wy = y0 + s * storeyH + 2;
            for (int x = x0 + 3; x <= x1 - 3; x += 5) {
                for (int z : new int[]{z0, z1}) {
                    fill(x, wy, z, x, wy + storeyH - 5, z, GLASS);
                    set(x, wy - 1, z + (z == z0 ? -1 : 1), slab("stone_brick", true));
                }
            }
            for (int z = z0 + 3; z <= z1 - 3; z += 5) {
                for (int x : new int[]{x0, x1}) {
                    fill(x, wy, z, x, wy + storeyH - 5, z, GLASS);
                    set(x + (x == x0 ? -1 : 1), wy - 1, z, slab("stone_brick", true));
                }
            }
        }
        // the roof: a steep gable of deepslate tiles
        if (ridgeAlongX) {
            int half = (z1 - z0) / 2 + 1;
            for (int k = 0; k <= half; k++) {
                int y = top + 1 + (k * 5) / 4;
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    set(x, y, z0 - 1 + k, stairs("deepslate_tile", "south", false));
                    set(x, y, z1 + 1 - k, stairs("deepslate_tile", "north", false));
                    if (k > 0) {
                        set(x, y - 1, z0 - 1 + k, TILE);
                        set(x, y - 1, z1 + 1 - k, TILE);
                    }
                }
                for (int z = z0 - 1 + k; z <= z1 + 1 - k; z++) {
                    for (int yy = top + 1; yy < y; yy++) {
                        set(x0, yy, z, WALL.at(x0, yy, z));
                        set(x1, yy, z, WALL.at(x1, yy, z));
                    }
                }
            }
            // dormer windows along the slopes
            for (int x = x0 + 4; x <= x1 - 4; x += 6) {
                set(x, top + 3, z0 + 1, GLASS);
                set(x, top + 3, z1 - 1, GLASS);
            }
        } else {
            int half = (x1 - x0) / 2 + 1;
            for (int k = 0; k <= half; k++) {
                int y = top + 1 + (k * 5) / 4;
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    set(x0 - 1 + k, y, z, stairs("deepslate_tile", "east", false));
                    set(x1 + 1 - k, y, z, stairs("deepslate_tile", "west", false));
                    if (k > 0) {
                        set(x0 - 1 + k, y - 1, z, TILE);
                        set(x1 + 1 - k, y - 1, z, TILE);
                    }
                }
                for (int x = x0 - 1 + k; x <= x1 + 1 - k; x++) {
                    for (int yy = top + 1; yy < y; yy++) {
                        set(x, yy, z0, WALL.at(x, yy, z0));
                        set(x, yy, z1, WALL.at(x, yy, z1));
                    }
                }
            }
            for (int z = z0 + 4; z <= z1 - 4; z += 6) {
                set(x0 + 1, top + 3, z, GLASS);
                set(x1 - 1, top + 3, z, GLASS);
            }
        }
    }

    static void door(int x, int y, int z, String facing) {
        set(x, y, z, "minecraft:spruce_door[facing=" + facing + ",half=lower,hinge=left,open=false]");
        set(x, y + 1, z, "minecraft:spruce_door[facing=" + facing + ",half=upper,hinge=left,open=false]");
    }

    static void opening(int x0, int y0, int z0, int x1, int y1, int z1) {
        fill(x0, y0, z0, x1, y1, z1, AIR);
    }

    // stairs climbing from (x, y, z) toward +dz/-dz or +dx/-dx: n steps, w wide; clears headroom
    static void stairRun(int x, int y, int z, int dx, int dz, int n, int w, String base) {
        String facing = dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < w; k++) {
                int sx = x + dx * i + (dz != 0 ? k : 0), sz = z + dz * i + (dx != 0 ? k : 0);
                set(sx, y + i, sz, stairs(base, facing, false));
                for (int h = 1; h <= 4; h++) {
                    set(sx, y + i + h, sz, AIR);
                }
                for (int yy = y + i - 1; yy >= y - 1 && yy >= y + i - 2; yy--) {
                    if (get(sx, yy, sz) == null || AIR.equals(get(sx, yy, sz))) {
                        set(sx, yy, sz, PLANK);
                    }
                }
            }
        }
    }

    static void furnishRow(Random r, int x0, int z0, int x1, int z1, int y, String kind) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                switch (kind) {
                    case "shelves" -> {
                        if ((z - z0) % 4 == 0 && x != x0 && x != x1) {
                            fill(x, y, z, x, y + 3, z, SHELF);
                        }
                    }
                    case "beds" -> {
                        if ((x - x0) % 3 == 0 && (z == z0)) {
                            set(x, y, z, "minecraft:green_bed[facing=north,part=head]");
                            set(x, y, z + 1, "minecraft:green_bed[facing=north,part=foot]");
                        }
                    }
                    case "crates" -> {
                        if (r.nextFloat() < 0.18) {
                            set(x, y, z, r.nextBoolean() ? BARREL : "minecraft:chest[facing=south]");
                        }
                    }
                    case "webs" -> {
                        if (r.nextFloat() < 0.05) {
                            set(x, y, z, COBWEB);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    static void room(String name, int x0, int y0, int z0, int x1, int y1, int z1, Random r, int count, String... mobs) {
        int[] spawns = new int[count * 3];
        for (int i = 0; i < count; i++) {
            spawns[i * 3] = x0 + 1 + r.nextInt(Math.max(1, x1 - x0 - 1));
            spawns[i * 3 + 1] = y0;
            spawns[i * 3 + 2] = z0 + 1 + r.nextInt(Math.max(1, z1 - z0 - 1));
        }
        ROOMS.add(new Room(name, x0, y0, z0, x1, y1, z1, spawns, mobs));
    }

    // ---------------------------------------------------------------- the keep: seven storeys, the roof arena
    static void keep(Random r) {
        int top = ROOF;                       // first air of the roof floor
        for (int x = KX0; x <= KX1; x++) {
            for (int z = KZ0; z <= KZ1; z++) {
                boolean outer = x <= KX0 + 1 || x >= KX1 - 1 || z <= KZ0 + 1 || z >= KZ1 - 1;
                for (int y = U - 8; y < top; y++) {
                    if (outer) {
                        set(x, y, z, WALL.at(x, y, z));
                    } else if (y < U - 1) {
                        set(x, y, z, ROCK.at(x, y, z));
                    } else if ((y - (U - 1)) % KFLOOR == 0) {
                        set(x, y, z, y == top - 1 ? ANDESITE : (((x + z) & 3) == 0 ? "minecraft:polished_deepslate" : ANDESITE));
                    } else {
                        set(x, y, z, AIR);
                    }
                }
            }
        }
        // buttresses (the reference's vertical ribs): every 6 blocks along each face, standing proud
        for (int x = KX0 + 3; x <= KX1 - 3; x += 6) {
            for (int y = U - 8; y < top - 2; y++) {
                set(x, y, KZ0 - 1, WALL.at(x, y, KZ0 - 1));
                set(x, y, KZ1 + 1, WALL.at(x, y, KZ1 + 1));
            }
        }
        for (int z = KZ0 + 3; z <= KZ1 - 3; z += 6) {
            for (int y = U - 8; y < top - 2; y++) {
                set(KX0 - 1, y, z, WALL.at(KX0 - 1, y, z));
                set(KX1 + 1, y, z, WALL.at(KX1 + 1, y, z));
            }
        }
        // tall lancet windows between the ribs on every storey, a string course at every floor
        for (int s = 0; s < KSTOREYS; s++) {
            int fy = U + s * KFLOOR;
            for (int x = KX0; x <= KX1; x++) {
                set(x, fy - 1, KZ0 - 1, CHISELED.equals(get(x, fy - 1, KZ0 - 1)) ? CHISELED : ANDESITE);
                set(x, fy - 1, KZ1 + 1, ANDESITE);
            }
            for (int z = KZ0; z <= KZ1; z++) {
                set(KX0 - 1, fy - 1, z, ANDESITE);
                set(KX1 + 1, fy - 1, z, ANDESITE);
            }
            for (int x = KX0 + 6; x <= KX1 - 6; x += 6) {
                lancet(x, fy + 3, KZ0, 0, -1);
                lancet(x, fy + 3, KZ1, 0, 1);
            }
            for (int z = KZ0 + 6; z <= KZ1 - 6; z += 6) {
                lancet(KX0, fy + 3, z, -1, 0);
                lancet(KX1, fy + 3, z, 1, 0);
            }
        }
        // the door (south face, centre) with a pointed hood
        int dx = (KX0 + KX1) / 2;
        for (int x = dx - 2; x <= dx + 2; x++) {
            for (int y = U; y <= U + 5; y++) {
                if (y <= U + 3 || Math.abs(x - dx) <= (U + 5 - y)) {
                    fill(x, y, KZ1 - 1, x, y, KZ1 + 1, AIR);
                }
            }
        }
        fill(dx - 3, U + 6, KZ1 + 1, dx + 3, U + 6, KZ1 + 1, CHISELED);
        // inside: storeys joined by stairs that alternate corners (each floor must be crossed), and themed rooms
        String[] themes = {"entrance", "armoury", "library", "barracks", "chapel", "gallery", "armoury"};
        for (int s = 0; s < KSTOREYS; s++) {
            int fy = U + s * KFLOOR;
            boolean east = s % 2 == 0;
            int ix0 = KX0 + 2, ix1 = KX1 - 2, iz0 = KZ0 + 2, iz1 = KZ1 - 2;
            // the stair up hugs the north wall on even storeys and the south wall on odd ones, so the arrival from
            // below and the way on up are always on opposite sides of the cross wall: every floor must be crossed
            int n = KFLOOR;
            boolean north = s % 2 == 0;
            int sz = north ? iz0 : iz1 - 2;
            int sx0 = east ? ix1 - 5 - n : ix0 + 5 + n;
            if (east) {
                stairRun(sx0, fy, sz, 1, 0, n, 3, "stone_brick");
                opening(sx0 + 3, fy + n - 1, sz, sx0 + n - 1, fy + n - 1, sz + 2);
            } else {
                stairRun(sx0, fy, sz, -1, 0, n, 3, "stone_brick");
                opening(sx0 - n + 1, fy + n - 1, sz, sx0 - 3, fy + n - 1, sz + 2);
            }
            // a railing on the floor above round the stairwell
            int rz = north ? sz + 3 : sz - 1;
            for (int x = Math.min(sx0, sx0 + (east ? n : -n)); x <= Math.max(sx0, sx0 + (east ? n : -n)); x++) {
                if (s < KSTOREYS - 1 && solid(x, fy + KFLOOR - 1, rz)) {
                    set(x, fy + KFLOOR, rz, "minecraft:stone_brick_wall");
                }
            }
            // a cross wall splitting the floor, the way through at the other side from the stair: walk the whole floor
            int midZ = (iz0 + iz1) / 2 - 2;
            fill(ix0, fy, midZ, ix1, fy + KFLOOR - 2, midZ, WALL);
            int gap = east ? ix0 + 2 : ix1 - 5;
            opening(gap, fy, midZ, gap + 3, fy + 3, midZ);
            // pillars, lights, and the theme
            for (int x = ix0 + 5; x <= ix1 - 5; x += 9) {
                for (int z = midZ + 5; z <= iz1 - 3; z += 8) {
                    fill(x, fy, z, x, fy + KFLOOR - 2, z, "minecraft:polished_deepslate");
                }
            }
            for (int x = ix0 + 4; x <= ix1 - 4; x += 8) {
                set(x, fy + KFLOOR - 2, midZ + 8, CHAIN);
                set(x, fy + KFLOOR - 3, midZ + 8, s % 2 == 0 ? SOUL_HANG : LANTERN_HANG);
                set(x, fy + KFLOOR - 2, iz0 + 6, LANTERN_HANG);
            }
            String theme = themes[s % themes.length];
            switch (theme) {
                case "armoury" -> {
                    furnishRow(r, ix0 + 1, midZ + 1, ix0 + 1, iz1 - 1, fy, "crates");
                    for (int z = midZ + 3; z < iz1; z += 3) {
                        set(ix1 - 1, fy, z, "minecraft:anvil[facing=north]");
                        set(ix1 - 1, fy + 2, z, "minecraft:green_wall_banner[facing=west]");
                    }
                }
                case "library" -> furnishRow(r, ix0 + 3, midZ + 2, ix1 - 3, iz1 - 2, fy, "shelves");
                case "barracks" -> furnishRow(r, ix0 + 1, midZ + 1, ix1 - 1, midZ + 1, fy, "beds");
                case "chapel" -> {
                    for (int z = midZ + 3; z < iz1 - 3; z += 2) {
                        for (int x = ix0 + 3; x < ix1 - 3; x++) {
                            if (Math.abs(x - (ix0 + ix1) / 2) > 2) {
                                set(x, fy, z, stairs("dark_oak", "north", false));
                            }
                        }
                    }
                    set((ix0 + ix1) / 2, fy, iz1 - 2, "minecraft:lectern[facing=north]");
                }
                case "gallery" -> {
                    for (int x = ix0 + 1; x <= ix1 - 1; x++) {
                        set(x, fy, (midZ + iz1) / 2, "minecraft:green_carpet");
                    }
                }
                default -> furnishRow(r, ix0 + 1, midZ + 1, ix1 - 1, iz1 - 1, fy, "webs");
            }
            furnishRow(r, ix0 + 1, iz0 + 4, ix1 - 1, midZ - 1, fy, "webs");
            String[] mobs = s < 2 ? new String[]{"zombie_soldier", "skeleton_archer"} : s < 4 ? new String[]{"vindicator", "skeleton_archer", "zombie_soldier"}
                    : new String[]{"vindicator", "stray_archer", "witch"};
            room("keep " + (s + 1) + " (" + theme + ")", ix0, fy, iz0, ix1, fy + KFLOOR - 2, iz1, r, 4 + s, mobs);
        }
        // the roof: the arena. Paved in a ring pattern, a parapet, corner turrets, the stair hatch in its corner
        for (int x = KX0; x <= KX1; x++) {
            for (int z = KZ0; z <= KZ1; z++) {
                int dxa = Math.abs(x - ARENA_CX), dza = Math.abs(z - ARENA_CZ);
                int ring = Math.max(dxa, dza);
                String paving = ring % 6 == 0 ? "minecraft:polished_deepslate" : ring % 6 == 3 ? CHISELED : (hash(x, z, 81) < 0.15 ? CRACKED : BRICK);
                if (dxa <= 1 && dza <= 1) {
                    paving = "minecraft:emerald_block";     // her mark, at the heart of the arena
                }
                set(x, top - 1, z, paving);
                for (int y = top; y < top + 6; y++) {
                    set(x, y, z, AIR);
                }
                boolean edge = x == KX0 || x == KX1 || z == KZ0 || z == KZ1;
                if (edge) {
                    set(x, top, z, WALL.at(x, top, z));
                    set(x, top + 1, z, WALL.at(x, top + 1, z));
                    if (((x + z) & 1) == 0) {
                        set(x, top + 2, z, BRICK);
                    }
                }
            }
        }
        // corbelled parapet overhang
        for (int x = KX0; x <= KX1; x++) {
            set(x, top - 2, KZ0 - 1, stairs("stone_brick", "south", true));
            set(x, top - 2, KZ1 + 1, stairs("stone_brick", "north", true));
        }
        for (int z = KZ0; z <= KZ1; z++) {
            set(KX0 - 1, top - 2, z, stairs("stone_brick", "east", true));
            set(KX1 + 1, top - 2, z, stairs("stone_brick", "west", true));
        }
        for (int[] c : new int[][]{{KX0, KZ0}, {KX1, KZ0}, {KX0, KZ1}, {KX1, KZ1}}) {
            squareCornerTurret(c[0], c[1], top);
        }
        // the hatch: the last stair comes up in the north-east corner (the top storey's stair runs east)
        // green-flamed braziers at the arena's four sides
        for (int[] b : new int[][]{{ARENA_CX, KZ0 + 3}, {ARENA_CX, KZ1 - 3}, {KX0 + 3, ARENA_CZ}, {KX1 - 3, ARENA_CZ}}) {
            set(b[0], top, b[1], "minecraft:polished_blackstone_wall");
            set(b[0], top + 1, b[1], "minecraft:soul_campfire[lit=true]");
        }
    }

    static void lancet(int x, int y, int z, int ox, int oz) {
        // a 1-wide, 6-tall window slot with a pointed head, glazed, cut through the 2-thick wall
        for (int d = 0; d <= 1; d++) {
            int wx = x - ox * d, wz = z - oz * d;
            for (int h = 0; h < 6; h++) {
                set(wx, y + h, wz, d == 0 ? GLASS : AIR);
            }
        }
        set(x + ox, y - 1, z + oz, slab("stone_brick", true));
        set(x, y + 6, z, CHISELED);
    }

    static void squareCornerTurret(int cx, int cz, int top) {
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                boolean shell = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
                for (int y = top - 8; y < top + 12; y++) {
                    boolean insideKeep = x > KX0 + 1 && x < KX1 - 1 && z > KZ0 + 1 && z < KZ1 - 1;
                    if (y < top && insideKeep) {
                        continue;
                    }
                    set(x, y, z, shell ? WALL.at(x, y, z) : (y < top ? WALL.at(x, y, z) : AIR));
                }
                set(x, top + 12, z, ANDESITE);
                if (shell && ((x + z) & 1) == 0) {
                    set(x, top + 13, z, BRICK);
                }
            }
        }
        for (int[] w : new int[][]{{cx, cz - 3}, {cx, cz + 3}, {cx - 3, cz}, {cx + 3, cz}}) {
            fill(w[0], top + 4, w[1], w[0], top + 7, w[1], BARS);
        }
        // a slender spire on top
        for (int dy = 0; dy < 8; dy++) {
            int rr = dy < 3 ? 2 : dy < 6 ? 1 : 0;
            for (int x = cx - rr; x <= cx + rr; x++) {
                for (int z = cz - rr; z <= cz + rr; z++) {
                    set(x, top + 14 + dy, z, TILE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- age: vines and moss on the masonry, rubble
    static void weather(Random r) {
        for (int i = 0; i < 16000; i++) {
            int x = -122 + r.nextInt(244), z = -96 + r.nextInt(192), y = P - 6 + r.nextInt(64);
            if (!solid(x, y, z) || !get(x, y, z).contains("stone_brick")) {
                continue;
            }
            // a vine hanging in the air beside the wall, naming the side the wall is on
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int[] d = dirs[r.nextInt(4)];
            int ax = x + d[0], az = z + d[1];
            String side = d[0] == 1 ? "west" : d[0] == -1 ? "east" : d[1] == 1 ? "north" : "south";
            int len = 2 + r.nextInt(7);
            for (int k = 0; k < len; k++) {
                if (get(ax, y - k, az) == null && solid(x, y - k, z)) {
                    set(ax, y - k, az, "minecraft:vine[" + side + "=true]");
                } else {
                    break;
                }
            }
        }
    }
}
