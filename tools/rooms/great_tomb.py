"""The Great Tomb of Nazarick: overworld pieces that connect the surface to the Throne Room and King's Quarters.

World layout, relative to S = mausoleum centre at surface level (x east, z south), Yd = S.y - 70:
  surface      61x34x61  at (S.x-30, S.y-6, S.z-30)   walled graveyard + mausoleum (floor at local y=6)
  shaft         9x70x9   at (S.x-4,  Yd,    S.z-4)    spiral stair from the floor of the tomb up to the mausoleum
  antechamber  21x30x22  at (S.x-10, Yd,    S.z+5)    Hall of Lemegeton: shaft (north) -> throne doors (south), corridor (east)
  throne_room  63x52x125 at (S.x-31, Yd,    S.z+27)
  corridor     47x8x6    at (S.x+11, Yd,    S.z+6)    antechamber -> King's Quarters
  kings_quarters 35x28x54 at (S.x+36, Yd,   S.z+12)
"""
import math, random
from lib import Build, pointed, ring_around, banner_nbt

rnd = random.Random(1001)

# ====================================================================== surface
def surface():
    W, H, D = 61, 34, 61
    B = Build(W, H, D, "tomb_surface")
    C = 30
    F = 6  # floor level
    for x in range(W):
        for z in range(D):
            for y in range(0, 3): B.set(x, y, z, "stone")
            for y in range(3, F): B.set(x, y, z, "dirt")
            r = rnd.random()
            B.set(x, F, z, "grass_block" if r < .55 else "coarse_dirt" if r < .8 else "podzol" if r < .93 else "rooted_dirt")
    # path from the north gate to the mausoleum, and a ring path
    for z in range(0, 22):
        for x in range(C - 2, C + 3):
            B.set(x, F, z, "stone_bricks" if (x + z) % 3 else "cracked_stone_bricks")
    for x in range(W):
        for z in range(D):
            d = max(abs(x - C), abs(z - C))
            if d in (13, 14): B.set(x, F, z, "gravel" if rnd.random() < .7 else "stone_bricks")
    # perimeter wall with pillars and a gate
    for i in range(W):
        for (x, z) in ((i, 0), (i, D - 1), (0, i), (W - 1, i)):
            pillar = (i % 6 == 0)
            top = F + (8 if pillar else 5)
            for y in range(F + 1, top + 1):
                r = rnd.random()
                B.set(x, y, z, "chiseled_stone_bricks" if pillar and y == top else
                      ("mossy_stone_bricks" if r < .35 else "cracked_stone_bricks" if r < .45 else "stone_bricks"))
            if not pillar:
                B.set(x, top + 1, z, "stone_brick_wall")
            else:
                B.set(x, top + 1, z, "soul_lantern[hanging=false]")
    for x in range(C - 2, C + 3):     # gate
        for y in range(F + 1, F + 6): B.set(x, y, 0, "air")
    for x in (C - 3, C + 3):
        for y in range(F + 1, F + 11): B.set(x, y, 0, "polished_deepslate")
        B.set(x, F + 11, 0, "wither_skeleton_skull[rotation=8]")
    for x in range(C - 3, C + 4): B.set(x, F + 7, 0, "deepslate_brick_slab[type=bottom]") if B.get(x, F + 7, 0) == "air" else None
    for x in range(C - 2, C + 3): B.set(x, F + 6, 0, "deepslate_bricks")
    # graves
    for gx in range(4, W - 4, 4):
        for gz in range(4, D - 4, 5):
            if abs(gx - C) <= 3 and gz < 22: continue
            if max(abs(gx - C), abs(gz - C)) <= 15: continue
            if rnd.random() < .25: continue
            stone = rnd.choice(["cobblestone_wall", "mossy_cobblestone_wall", "stone_brick_wall", "andesite_wall"])
            B.set(gx, F + 1, gz, stone)
            if rnd.random() < .6: B.set(gx, F + 2, gz, rnd.choice(["cobblestone_wall", "candle[candles=1,lit=false]", "stone_brick_slab[type=bottom]"]))
            for dz in (1, 2): B.set(gx, F, gz + dz, "coarse_dirt" if rnd.random() < .6 else "podzol")
    # dead trees
    for tx, tz in ((8, 40), (52, 18), (10, 12), (50, 50)):
        h = rnd.randint(5, 8)
        for y in range(F + 1, F + 1 + h): B.set(tx, y, tz, "dark_oak_log[axis=y]")
        for k in range(3):
            dx, dz = rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
            yb = F + 3 + rnd.randint(0, h - 3)
            for L in range(1, rnd.randint(2, 4)):
                B.set(tx + dx * L, yb + (L > 1), tz + dz * L, "dark_oak_fence")
        B.set(tx, F + 1 + h, tz, "dark_oak_fence")
    for _ in range(14):
        B.set(rnd.randint(2, W - 3), F + 1, rnd.randint(2, D - 3), "cobweb") if rnd.random() < .5 else None

    # ---------------- mausoleum (x/z 20..40), white stone with black trim
    M0, M1 = C - 10, C + 10
    for x in range(M0, M1 + 1):
        for z in range(M0, M1 + 1):
            B.set(x, F, z, "polished_andesite" if (x + z) % 2 else "smooth_stone")
            edge = x in (M0, M1) or z in (M0, M1)
            if edge:
                for y in range(F + 1, F + 12):
                    col = (x - M0) % 4 == 0 and (z - M0) % 4 == 0
                    B.set(x, y, z, "polished_deepslate" if col else ("smooth_quartz" if y not in (F + 1, F + 11) else "polished_blackstone_bricks"))
    # steps up to the platform around the mausoleum
    for x in range(M0 - 1, M1 + 2):
        for z in range(M0 - 1, M1 + 2):
            if x in (M0 - 1, M1 + 1) or z in (M0 - 1, M1 + 1):
                B.set(x, F, z, "smooth_stone")
    # doorway (north) with a pointed arch
    door = pointed(C - 2, C + 2, F + 1, F + 6)
    for x, y in door: B.set(x, y, M0, "air")
    for x, y in ring_around(door, diag=False):
        if y > F: B.set(x, y, M0, "polished_blackstone_bricks")
    B.set(C, F + 10, M0, "gold_block")
    # roof: stepped pyramid + spire
    for k in range(0, 8):
        y = F + 12 + k
        a, b = M0 + k, M1 - k
        if a > b: break
        for x in range(a, b + 1):
            for z in range(a, b + 1):
                if x in (a, b) or z in (a, b) or k == 7:
                    B.set(x, y, z, "polished_blackstone_bricks" if k % 2 == 0 else "smooth_quartz")
    for y in range(F + 20, F + 26): B.set(C, y, C, "polished_blackstone_wall" if y < F + 25 else "gold_block")
    B.set(C, F + 26, C, "end_rod[facing=up]")
    for x in range(M0 + 1, M1):
        for z in range(M0 + 1, M1):
            B.set(x, F + 11, z, "smooth_quartz")       # ceiling
    # interior: stairwell opening over the shaft (7x7 around a 3x3 pillar), railing
    for x in range(C - 3, C + 4):
        for z in range(C - 3, C + 4):
            if abs(x - C) <= 1 and abs(z - C) <= 1:
                B.set(x, F, z, "polished_deepslate")
                B.set(x, F + 1, z, "polished_deepslate" if (x, z) != (C, C) else "chiseled_polished_blackstone")
            else:
                for y in range(0, F + 1): B.set(x, y, z, "air")
    B.set(C, F + 2, C, "soul_lantern[hanging=false]")
    for x in range(C - 4, C + 5):
        for z in range(C - 4, C + 5):
            if max(abs(x - C), abs(z - C)) == 4 and not (z == C - 4 and abs(x - C) <= 1):
                B.set(x, F + 1, z, "polished_blackstone_wall")
                for y in range(0, F): B.set(x, y, z, "deepslate_bricks")
    for (x, z) in ((M0 + 2, M0 + 2), (M1 - 2, M0 + 2), (M0 + 2, M1 - 2), (M1 - 2, M1 - 2)):
        B.set(x, F + 10, z, "chain[axis=y]"); B.set(x, F + 9, z, "soul_lantern[hanging=true]")
    # engraved guild banner inside, facing the door
    B.set(C, F + 7, M1 - 1, "black_wall_banner[facing=north]",
          banner_nbt([("bo", "yellow"), ("mc", "purple"), ("sku", "white"), ("tts", "yellow")]))
    # guards of the graveyard
    B.entity(12, F + 1, 46, "nazarick:elder_lich", 0.0)
    B.entity(48, F + 1, 46, "nazarick:elder_lich", 0.0)
    B.anchor("surface_entrance", C, F + 1, 2)
    B.anchor("mausoleum_door", C, F + 1, M0 - 2)
    return B

# ====================================================================== spiral shaft
def shaft():
    W, H, D = 9, 70, 9
    B = Build(W, H, D, "tomb_shaft")
    cx = cz = 4
    for x in range(W):
        for z in range(D):
            B.set(x, 0, z, "polished_deepslate")
            if x in (0, W - 1) or z in (0, D - 1):
                for y in range(1, H):
                    lit = (y % 6 == 3) and (x == cx or z == cz)
                    B.set(x, y, z, "sea_lantern" if lit else ("deepslate_tiles" if y % 6 else "polished_deepslate"))
    for x in range(3, 6):
        for z in range(3, 6):
            for y in range(1, H): B.set(x, y, z, "polished_deepslate" if (x, z) != (cx, cz) else "deepslate_bricks")
    def step_index(x, z):
        a = math.atan2(z - cz, x - cx)            # +pi/2 = south
        return int(((a - math.pi / 2) % (2 * math.pi)) / (2 * math.pi) * 24) % 24
    for x in range(1, 8):
        for z in range(1, 8):
            if 3 <= x <= 5 and 3 <= z <= 5: continue
            s = step_index(x, z)
            a = math.atan2(z - cz, x - cx)
            tx, tz = -math.sin(a), math.cos(a)  # counter-clockwise tangent (direction of ascent)
            facing = ("east" if tx > 0 else "west") if abs(tx) >= abs(tz) else ("south" if tz > 0 else "north")
            y = 1 + s
            while y < H:
                B.set(x, y, z, f"deepslate_tile_stairs[facing={facing},half=bottom]")
                y += 24
    # exit to the antechamber (south wall)
    for x in range(3, 6):
        for y in range(1, 5): B.set(x, y, D - 1, "air")
    return B

# ====================================================================== antechamber (Hall of Lemegeton)
def statue(B, x, z, facing_dx):
    """A demon statue: plinth, body, horned head, folded wings, glowing eyes."""
    B.set(x, 1, z, "polished_blackstone_bricks"); B.set(x, 2, z, "gold_block")
    for y in (3, 4, 5): B.set(x, y, z, "polished_blackstone")
    B.set(x, 6, z, "blackstone")
    B.set(x + facing_dx, 6, z, "crying_obsidian")
    for dz in (-1, 1):
        B.set(x, 7, z + dz, "polished_blackstone_wall")
        for y in (3, 4, 5): B.set(x - facing_dx, y, z + dz, "polished_blackstone_brick_stairs[facing=north,half=top]" if dz < 0 else "polished_blackstone_brick_stairs[facing=south,half=top]")

def antechamber():
    W, H, D = 21, 30, 22
    B = Build(W, H, D, "tomb_antechamber")
    for x in range(W):
        for z in range(D):
            B.set(x, 0, z, "polished_blackstone" if (x + z) % 2 else "polished_deepslate")
            if x in (0, W - 1) or z in (0, D - 1):
                for y in range(1, H): B.set(x, y, z, "polished_blackstone_bricks" if y < 4 or y % 7 == 0 else "deepslate_tiles")
            B.set(x, H - 1, z, "deepslate_tiles")
    # pointed vault over the hall
    for x in range(1, W - 1):
        top = 22 + round(6 * math.sqrt(max(0, 1 - ((x - 10) / 10.5) ** 2)))
        for z in range(1, D - 1):
            for y in range(top, H - 1): B.set(x, y, z, "deepslate_tiles")
            B.set(x, top - 1, z, "polished_blackstone_bricks" if z % 5 == 0 else "air")
    # north door (from the shaft)
    for x in range(9, 12):
        for y in range(1, 5): B.set(x, y, 0, "air")
    # east door (to the King's Quarters corridor)
    for z in range(2, 6):
        for y in range(1, 6): B.set(W - 1, y, z, "air")
    # south: the great portal of the Throne Room (matches the throne room's own opening)
    OPEN = pointed(4, 16, 1, 19)
    for x, y in OPEN: B.set(x, y, D - 1, "air")
    for x, y in ring_around(OPEN):
        if y >= 1: B.set(x, y, D - 2, "gold_block" if y > 3 else "polished_blackstone_bricks")
    # carpet runway
    for z in range(1, D - 1):
        for x in range(7, 14):
            B.set(x, 1, z, "yellow_carpet" if x in (7, 13) else "red_carpet")
    for z in range(1, 6):
        for x in range(14, W - 1): B.set(x, 1, z, "red_carpet" if 3 <= z <= 4 else "yellow_carpet")
    # demon statues along both walls (the Lemegeton)
    for z in (4, 8, 12, 16):
        statue(B, 3, z, +1)
        if z > 6: statue(B, W - 4, z, -1)
    # chandeliers
    for zc in (6, 15):
        for y in range(18, 26): B.set(10, y, zc, "chain[axis=y]")
        B.set(10, 17, zc, "gold_block")
        for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            B.set(10 + dx, 17, zc + dz, "gold_block"); B.set(10 + dx, 16, zc + dz, "soul_lantern[hanging=true]")
        for d in (1,):
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                B.set(10 + dx, 17, zc + dz, "chain[axis=x]" if dx else "chain[axis=z]")
    # guardians of the throne doors
    B.entity(5, 1, 18, "nazarick:death_knight", 180.0)
    B.entity(15, 1, 18, "nazarick:death_knight", 180.0)
    return B

# ====================================================================== corridor to the King's Quarters
def corridor():
    W, H, D = 47, 8, 6
    B = Build(W, H, D, "tomb_corridor")
    for x in range(W):
        for z in range(D):
            B.set(x, 0, z, "polished_blackstone_bricks")
            B.set(x, H - 1, z, "deepslate_tiles")
            if z in (0, D - 1) or x == W - 1:
                for y in range(1, H - 1):
                    B.set(x, y, z, "polished_deepslate" if x % 5 == 0 else ("polished_blackstone_bricks" if y == 1 else "deepslate_tiles"))
        if 1 <= x < W - 1:
            for z in (2, 3): B.set(x, 1, z, "red_carpet")
            for z in (1, 4): B.set(x, 1, z, "yellow_carpet")
            if x % 5 == 0:
                B.set(x, H - 2, 2, "soul_lantern[hanging=true]")
    for z in range(1, 5):
        for y in range(1, 6): B.set(0, y, z, "air")      # west end: open into the antechamber
    for x in range(41, 44):
        for y in range(1, 6): B.set(x, y, D - 1, "air")  # south wall: the King's Quarters entrance
    return B

BUILDS = {"tomb_surface": surface(), "tomb_shaft": shaft(), "tomb_antechamber": antechamber(), "tomb_corridor": corridor()}
