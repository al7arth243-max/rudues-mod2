"""Throne Room of Nazarick (10th floor), v3: grand scale.
Entrance at north (z=0), throne at south facing the doors.
Walls are 3 layers deep: backing (0), wall (1), face (2). Interior x 3..59, z 3..121."""
import math, random
from lib import Build, banner_nbt, PATTERNS, COLOR_IDS, pointed, ring_around

W, H, D = 63, 52, 125
CX = 31
IX0, IX1, IZ0, IZ1 = 3, W - 4, 3, D - 4
B = Build(W, H, D, "throne_room")
rnd = random.Random(41)  # 41 Supreme Beings

def vault_h(x):
    return 34 + round(16 * math.sqrt(max(0.0, 1 - ((x - CX) / 28.6) ** 2)))

def wset(x, y, z, spec, nbt=None): B.mset(x, y, z, spec, nbt)
def face_toward(dx, dz):
    if abs(dx) >= abs(dz): return "west" if dx > 0 else "east"
    return "north" if dz > 0 else "south"

# ================================================================== shell
for x in range(W):
    for z in range(D):
        B.set(x, 0, z, "polished_deepslate")
        if x <= 2 or x >= W - 3 or z <= 2 or z >= D - 3:
            for y in range(1, H): B.set(x, y, z, "deepslate_tiles")
        else:
            for y in range(vault_h(x), H): B.set(x, y, z, "deepslate_tiles")

# floor: diamond lattice, borders, strips beside the runway
for x in range(IX0, IX1 + 1):
    for z in range(IZ0, IZ1 + 1):
        d1, d2 = (x - CX + z) % 8 == 0, (x - CX - z) % 8 == 0
        spec = "chiseled_deepslate" if d1 and d2 else ("deepslate_tiles" if d1 or d2 else "polished_deepslate")
        if x in (IX0, IX1) or z in (IZ0, IZ1): spec = "polished_blackstone_bricks"
        elif x in (IX0 + 1, IX1 - 1) or z in (IZ0 + 1, IZ1 - 1): spec = "polished_blackstone"
        elif x in (23, W - 1 - 23): spec = "polished_blackstone"
        B.set(x, 0, z, spec)

# ================================================================== side walls
PIL = list(range(6, IZ1, 10))          # 6,16,...,116 -> 12 pilasters, 11 bays
for p in PIL:
    for dz in range(-2, 3):
        z = p + dz
        for y in range(1, 4):
            for x in (3, 4, 5): wset(x, y, z, "polished_blackstone_bricks")
        wset(5, 4, z, "polished_blackstone_brick_stairs[facing=west,half=bottom]")
        for y in range(4, 31):
            wset(3, y, z, "polished_deepslate")
            if abs(dz) <= 1: wset(4, y, z, "polished_basalt[axis=y]")
        wset(3, 31, z, "chiseled_polished_blackstone"); wset(4, 31, z, "chiseled_polished_blackstone")
        wset(5, 31, z, "polished_blackstone_brick_stairs[facing=west,half=top]")
        wset(3, 32, z, "gold_block" if abs(dz) <= 1 else "polished_blackstone_bricks")
        wset(4, 32, z, "gold_block" if dz == 0 else "polished_blackstone_bricks")
        for y in range(33, 37): wset(3, y, z, "polished_blackstone_bricks")
    wset(5, 12, p, "chiseled_polished_blackstone"); wset(5, 13, p, "lantern[hanging=false]")
    wset(5, 24, p, "chiseled_polished_blackstone"); wset(5, 25, p, "lantern[hanging=false]")

COLORS = list(COLOR_IDS)
DARK = ["black", "purple", "red", "blue", "gray", "green", "brown", "cyan", "magenta", "white", "light_gray", "orange", "yellow"]
def random_banner():
    base = rnd.choice(DARK)
    return base, [(rnd.choice(PATTERNS), rnd.choice([c for c in COLORS if c != base])) for _ in range(rnd.randint(2, 4))]

banners = 0
for bi, p in enumerate(PIL[:-1]):
    bay = range(p + 3, p + 8)
    for z in bay:
        for y in range(1, 6): wset(2, y, z, "polished_blackstone_bricks")
        wset(3, 6, z, "polished_blackstone_brick_stairs[facing=west,half=bottom]")      # chair rail
        wset(2, 7, z, "gilded_blackstone"); wset(2, 19, z, "gilded_blackstone")         # panel frame
        for y in range(8, 19):
            wset(2, y, z, "air"); wset(1, y, z, "blue_terracotta")                     # recessed panel
        wset(2, 20, z, "polished_deepslate")                                           # string course
        wset(3, 20, z, "polished_blackstone_brick_slab[type=top]")
    for y in (8, 18): wset(1, y, p + 5, "chiseled_polished_blackstone")
    wset(1, 13, p + 5, "gilded_blackstone"); wset(1, 12, p + 5, "gold_block"); wset(1, 14, p + 5, "gold_block")
    if bi < 10:   # banners in the first ten bays: 2 per bay per side = 40
        for z in (p + 4, p + 6):
            for x, f in ((2, "east"), (W - 3, "west")):
                base, layers = random_banner()
                B.set(x, 17, z, f"{base}_wall_banner[facing={f}]", banner_nbt(layers)); banners += 1
    # clerestory window
    win = pointed(p + 3, p + 7, 22, 29)
    for z, y in win:
        wset(2, y, z, "air")
        tr = (z == p + 5 and y <= 30) or y == 26
        wset(1, y, z, "gold_block" if tr else ("purple_stained_glass" if y >= 30 else "blue_stained_glass"))
        wset(0, y, z, "sea_lantern")
    for z, y in ring_around(win, diag=False):
        if y >= 22: wset(2, y, z, "polished_blackstone_bricks")
    wset(2, 33, p + 5, "gold_block")

# ================================================================== vault
def rib(x, z, spec="polished_blackstone_bricks"):
    if IX0 <= x <= IX1: B.set(x, vault_h(x) - 1, z, spec)
for x in range(IX0, IX1 + 1):
    for z in range(IZ0, IZ1 + 1):
        r = rnd.random()
        if r < 0.022: B.set(x, vault_h(x), z, "sea_lantern")
        elif r < 0.055: B.set(x, vault_h(x), z, "amethyst_block")
for p in PIL:
    for x in range(IX0 + 1, IX1): rib(x, p)
for a, b in zip(PIL, PIL[1:]):
    for i in range(121):
        s = i / 120
        x = round(IX0 + 1 + (IX1 - IX0 - 2) * s)
        rib(x, round(a + (b - a) * s)); rib(x, round(b - (b - a) * s))
    B.set(CX, vault_h(CX) - 1, (a + b) // 2, "gold_block")
for z in range(IZ0, IZ1 + 1): rib(CX, z)
for p in PIL: B.set(CX, vault_h(CX) - 1, p, "gold_block")

# ================================================================== front wall: great doors + rose window
OPEN = pointed(25, 37, 1, 19)
for x, y in OPEN:
    for z in (0, 1, 2): B.set(x, y, z, "air")
R1 = ring_around(OPEN); R2 = ring_around(list(OPEN) + list(R1)); R3 = ring_around(list(OPEN) + list(R1) + list(R2))
for x, y in R1:
    if y >= 1: B.set(x, y, 2, "gold_block")
for x, y in R2:
    if y >= 1:
        B.set(x, y, 2, "chiseled_polished_blackstone")
        if y >= 14: B.set(x, y, 3, "polished_blackstone_bricks")
for x, y in R3:
    if y >= 1: B.set(x, y, 2, "polished_blackstone_bricks")
for x in (21, 41):
    for dx in (-1, 0, 1):
        B.set(x + dx, 1, 3, "gold_block")
        B.set(x + dx, 2, 3, "polished_blackstone_bricks")
    for y in range(3, 27): B.set(x, y, 3, "polished_basalt[axis=y]")
    B.set(x, 27, 3, "gold_block"); B.set(x, 28, 3, "chiseled_polished_blackstone"); B.set(x, 29, 3, "end_rod[facing=up]")
for xl in (25, 37):     # door leaves standing open against the jambs
    for z in range(3, 10):
        for y in range(1, 23):
            if z in (3, 9) or y in (1, 22): spec = "polished_blackstone_bricks"
            elif y in (2, 8, 14, 21): spec = "gold_block"
            elif 9 <= y <= 13: spec = "gilded_blackstone" if abs(z - 6) + abs(y - 11) <= 2 else "dark_oak_planks"
            elif 15 <= y <= 20: spec = "gilded_blackstone" if abs(z - 6) + abs(y - 17.5) <= 2 else ("chiseled_polished_blackstone" if (z + y) % 2 else "dark_oak_planks")
            else: spec = "gold_block" if z in (4, 8) and y in (3, 7) else "dark_oak_planks"
            B.set(xl, y, z, spec)
RC_Y, RR = 38, 7.5
for x in range(CX - 10, CX + 11):
    for y in range(RC_Y - 10, RC_Y + 11):
        d = math.hypot(x - CX, y - RC_Y)
        if d <= RR:
            B.set(x, y, 2, "air"); B.set(x, y, 0, "sea_lantern")
            ang = math.degrees(math.atan2(y - RC_Y, x - CX)) % 360
            spoke = min(ang % 30, 30 - ang % 30) < 4.5 and d > 2
            if d < 2 or spoke or 6.6 < d or 4.3 < d < 5.1: g = "gold_block"
            else: g = ["purple_stained_glass", "blue_stained_glass", "red_stained_glass"][int(ang // 30) % 3] if d > 5 else ["blue_stained_glass", "purple_stained_glass"][int(ang // 30) % 2]
            B.set(x, y, 1, g)
        elif d <= RR + 1.2:
            B.set(x, y, 2, "polished_blackstone_bricks")
        elif d <= RR + 2.0:
            B.set(x, y, 2, "chiseled_polished_blackstone" if int(math.degrees(math.atan2(y - RC_Y, x - CX)) // 15) % 2 else "polished_blackstone_bricks")
B.set(CX, 29, 3, "black_wall_banner[facing=south]",
      banner_nbt([("bo", "yellow"), ("mc", "purple"), ("sku", "white"), ("tts", "yellow")])); banners += 1
assert banners == 41, banners
for x in list(range(IX0, 19)) + list(range(W - 19, IX1 + 1)):
    for y in range(1, 6): B.set(x, y, 2, "polished_blackstone_bricks")

# ================================================================== back wall: starry backdrop
BACK = D - 3
BOPEN = [(u, y) for y in range(7, 35) for u in range(14, W - 14)]
a, b, y = 16, W - 17, 35
while a <= b:
    BOPEN += [(u, y) for u in range(a, b + 1)]; a += 2; b -= 2; y += 1
for x, y in BOPEN:
    if (x - 14) % 3 == 0:
        B.set(x, y, BACK, "polished_deepslate"); B.set(x, y, BACK + 1, "polished_deepslate")
    else:
        r = rnd.random()
        g = ("white_stained_glass" if r < .05 else "light_blue_stained_glass" if r < .2
             else "purple_stained_glass" if r < .3 else "blue_stained_glass")
        B.set(x, y, BACK, "air"); B.set(x, y, BACK + 1, g); B.set(x, y, BACK + 2, "sea_lantern")
BR1 = ring_around(BOPEN); BR2 = ring_around(list(BOPEN) + list(BR1))
for x, y in BR1:
    if y >= 7: B.set(x, y, BACK, "gold_block")
for x, y in BR2:
    if y >= 7:
        B.set(x, y, BACK, "polished_blackstone_bricks")
        B.set(x, y, BACK - 1, "chiseled_polished_blackstone" if y % 3 == 0 else "polished_blackstone_bricks")

# ================================================================== carpet runway (z 11..95)
RX0, RX1 = 24, W - 25
for z in range(11, 96):
    for x in range(RX0, RX1 + 1):
        B.set(x, 1, z, "yellow_carpet" if x in (RX0, RX1) else "red_carpet")
def medallion(cz):
    for x in range(RX0 + 1, RX1):
        for z in range(cz - 7, cz + 8):
            d = math.hypot(x - CX, z - cz); a = math.atan2(z - cz, x - CX)
            if abs(d - 5.6) < .55 or (1.4 < d <= 3.8 and abs(math.cos(4 * a)) > .65) or d < 1 or (abs(d - 4.6) < .4 and abs(math.cos(8 * a)) > .8):
                B.set(x, 1, z, "pink_carpet")
medallion(21); medallion(85)
for z in range(31, 76):
    if z % 6 == 0:
        for dx in (-3, 3): B.set(CX + dx, 1, z, "pink_carpet")
    if z % 6 == 3:
        B.set(CX, 1, z, "pink_carpet")
        for dx in (-5, 5): B.set(CX + dx, 1, z, "pink_carpet")
    if z % 6 in (1, 5):
        for dx in (-4, 4, -1, 1): B.set(CX + dx, 1, z, "pink_carpet") if (z % 6 == 1) == (abs(dx) == 4) else None

# ================================================================== crystal claws
def sweep(points, radius_fn, protect=()):
    for i, (px, py, pz) in enumerate(points):
        r = radius_fn(i / (len(points) - 1))
        for x in range(int(px - r) - 1, int(px + r) + 2):
            for y in range(int(py - r) - 1, int(py + r) + 2):
                for z in range(int(pz - r) - 1, int(pz + r) + 2):
                    d = math.dist((x, y, z), (px, py, pz))
                    if d > r or (x, y, z) in protect: continue
                    cur = B.get(x, y, z)
                    if cur != "air" and "glass" not in cur and cur != "crying_obsidian": continue
                    if d < r - 0.9: B.set(x, y, z, "crying_obsidian")
                    elif cur != "crying_obsidian":
                        h = (x * 7 + y * 13 + z * 5) % 20
                        B.set(x, y, z, "purple_stained_glass" if h < 3 else "light_blue_stained_glass" if h < 5 else "blue_stained_glass")

def claw(cx, cz, inward):
    for x in range(cx - 7, cx + 8):
        for z in range(cz - 7, cz + 8):
            dx, dz = x - cx, z - cz
            d = math.hypot(dx, dz); a = math.atan2(dz, dx)
            if d <= 6.2:
                B.set(x, 1, z, "polished_andesite" if int((a / (2 * math.pi) * 8 + d * .7) % 2) else "andesite")
            if 4.6 < d <= 5.6: B.set(x, 2, z, f"polished_andesite_stairs[facing={face_toward(dx, dz)},half=bottom]")
            elif d <= 4.6: B.set(x, 2, z, "polished_andesite")
            if 3.4 < d <= 4.4: B.set(x, 3, z, f"polished_andesite_stairs[facing={face_toward(dx, dz)},half=bottom]")
            elif d <= 3.4: B.set(x, 3, z, "polished_andesite")
            if 6.2 < d <= 7.0: B.set(x, 0, z, "polished_blackstone")
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            corner = abs(x - cx) == 2 and abs(z - cz) == 2
            for y in range(4, 12):
                if y in (4, 7, 11): spec = "gold_block"
                elif corner: spec = "chiseled_polished_blackstone"
                else: spec = "polished_blackstone_bricks"
                B.set(x, y, z, spec)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for s in (-1, 0, 1):
            x = cx + dx * 3 + (s if dz else 0); z = cz + dz * 3 + (s if dx else 0)
            B.set(x, 4, z, f"polished_blackstone_brick_stairs[facing={face_toward(x - cx, z - cz)},half=bottom]")
            B.set(x, 11, z, f"polished_blackstone_brick_stairs[facing={face_toward(x - cx, z - cz)},half=top]")
    for dx in (-2, 2):
        for dz in (-2, 2): B.set(cx + dx, 12, cz + dz, "lantern[hanging=false]")
    pts = [(cx + inward * 11 * t * t, 12 + 26 * t, cz + 1.5 * math.sin(t * math.pi)) for t in [i / 399 for i in range(400)]]
    sweep(pts, lambda t: 2.6 if t < .2 else 2.6 - (t - .2) / .8 * 2.1)
    for sx, sz, ln in ((-inward, -1, 6), (-inward, 1, 6), (0, -1, 4.5)):
        sp = [(cx + sx * (1 + ln * .8 * t), 12 + ln * t, cz + sz * (1 + ln * .6 * t)) for t in [i / 59 for i in range(60)]]
        sweep(sp, lambda t: 1.3 - .9 * t)
    tx, ty, tz = (round(c) for c in pts[-1])
    while B.get(tx, ty + 1, tz) != "air": ty += 1
    B.set(tx, ty + 1, tz, "amethyst_cluster[facing=up]")

CLAW_Z = [18, 32, 46, 60, 74, 88]
for z in CLAW_Z:
    claw(13, z, +1); claw(W - 1 - 13, z, -1)

# ================================================================== dais
DX0, DX1 = 15, W - 16
for i in range(6):
    z = 96 + i
    for x in range(DX0, DX1 + 1):
        for y in range(1, i + 1): B.set(x, y, z, "polished_blackstone_bricks")
        if x in (DX0, DX1):
            B.set(x, i + 1, z, "polished_blackstone_bricks"); B.set(x, i + 2, z, "polished_blackstone_brick_wall")
        else:
            B.set(x, i + 1, z, "polished_deepslate_stairs[facing=south,half=bottom]")
for x in (DX0, DX1):
    for y in range(1, 6): B.set(x, y, 95, "polished_blackstone_bricks")
    B.set(x, 6, 95, "gold_block"); B.set(x, 7, 95, "lantern[hanging=false]")
for z in range(102, IZ1 + 1):
    for x in range(DX0 - 1, DX1 + 2):
        for y in range(1, 7):
            spec = "polished_blackstone_bricks"
            if x in (DX0 - 1, DX1 + 1) and 2 <= y <= 5 and z % 3 == 0: spec = "chiseled_polished_blackstone"
            if y == 6 and z == 102: spec = "gold_block" if x % 2 == 0 else "chiseled_polished_blackstone"
            B.set(x, y, z, spec)
for z in range(103, IZ1 + 1):
    for x in (DX0 - 1, DX1 + 1):
        if (z - 103) % 4 == 0:
            B.set(x, 7, z, "polished_blackstone_bricks"); B.set(x, 8, z, "lantern[hanging=false]")
        else:
            B.set(x, 7, z, "polished_blackstone_brick_wall")
for z in range(102, 110):
    for x in range(DX0, DX1 + 1):
        B.set(x, 7, z, "yellow_carpet" if x in (DX0, DX1) or z == 102 else "green_carpet")
    for x in range(28, 35): B.set(x, 7, z, "yellow_carpet" if x in (28, 34) else "red_carpet")
for z in range(110, IZ1 + 1):
    for x in range(24, 39):
        if z == 110 and 25 <= x <= 37: B.set(x, 7, z, "polished_blackstone_brick_stairs[facing=south,half=bottom]")
        else: B.set(x, 7, z, "gold_block" if x in (24, 38) else "polished_blackstone_bricks")
for z in range(112, IZ1 + 1):
    for x in range(26, 37):
        if z == 112 and 27 <= x <= 35: B.set(x, 8, z, "polished_blackstone_brick_stairs[facing=south,half=bottom]")
        else: B.set(x, 8, z, "gold_block" if x in (26, 36) else "polished_blackstone_bricks")

# ================================================================== the throne
B.set(CX, 9, 115, "nazarick:throne[facing=north]")
for x in (30, 32):
    B.set(x, 9, 115, "gilded_blackstone")
    B.set(x, 9, 114, "polished_blackstone_brick_stairs[facing=south,half=bottom]")
SHAPE = [(x, y) for y in range(9, 19) for x in range(28, 35)] + \
        [(x, y) for y in range(19, 22) for x in range(29, 34)] + \
        [(x, y) for y in (22, 23) for x in range(30, 33)] + [(31, y) for y in (24, 25, 26)]
SS = set(SHAPE)
def edge(c): return any((c[0] + dx, c[1] + dy) not in SS for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
EDGE = {c for c in SHAPE if edge(c)}
for x, y in SHAPE:
    inner = any((x + dx, y + dy) in EDGE for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if (x, y) in EDGE and not (y == 9 and 29 <= x <= 33): spec = "gold_block"
    elif inner and y > 9 and x != CX: spec = "gilded_blackstone"
    elif x == CX and y % 2 == 0: spec = "shroomlight"
    else: spec = "crimson_planks"
    B.set(x, y, 116, spec); B.set(x, y, 117, "gilded_blackstone")
for x, y in ((28, 19), (34, 19), (29, 22), (33, 22), (30, 24), (32, 24), (31, 27)):
    B.set(x, y, 116, "end_rod[facing=up]")
throne = {(x, y, z) for x in range(26, 37) for y in range(7, 28) for z in range(113, 118)}
for ang, length in ((14, 30), (28, 27), (42, 23), (57, 19), (72, 15), (87, 10)):
    for s in (-1, 1):
        a = math.radians(ang) * s
        pts = [(CX + math.sin(a) * (6 + length * t), 12 + math.cos(a) * (6 + length * t), 118.4) for t in [i / 199 for i in range(200)]]
        sweep(pts, lambda t: 2.0 - 1.5 * t, protect=throne)

# ================================================================== chandeliers
def ring(r): return [(dx, dz) for dx in range(-7, 8) for dz in range(-7, 8) if abs(math.hypot(dx, dz) - r) < .5]
SPOKES = [(5, 0), (-5, 0), (0, 5), (0, -5), (4, 3), (3, 4), (-4, 3), (-3, 4), (4, -3), (3, -4), (-4, -3), (-3, -4)]
for zc in (25, 39, 53, 67, 81):
    for y in range(38, vault_h(CX) - 1): B.set(CX, y, zc, "chain[axis=y]")
    for dx, dz in ring(2): B.set(CX + dx, 38, zc + dz, "end_rod[facing=up]")
    B.set(CX, 37, zc, "gold_block")
    for dx, dz in ring(5):
        if (dx, dz) in SPOKES:
            B.set(CX + dx, 37, zc + dz, "gold_block"); B.set(CX + dx, 36, zc + dz, "lantern[hanging=true]")
            B.set(CX + dx, 38, zc + dz, "end_rod[facing=up]")
        else:
            B.set(CX + dx, 37, zc + dz, "end_rod[facing=down]")
    for d in (1, 2, 3, 4):
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            B.set(CX + dx * d, 37, zc + dz * d, "chain[axis=x]" if dx else "chain[axis=z]")
    for dx, dz in ring(3):
        B.set(CX + dx, 36, zc + dz, "end_rod[facing=down]")
    for dx, dz in ring(1.5): B.set(CX + dx, 35, zc + dz, "end_rod[facing=down]")
    for y in (36, 35, 34, 33, 32): B.set(CX, y, zc, "end_rod[facing=down]")

# ================================================================== anchors
B.anchor("throne_seat", CX, 9, 115)
B.entity(35, 9, 114, "nazarick:albedo", 180.0)
B.anchor("albedo_stand", 35, 9, 114)
B.anchor("sebas_entry", CX, 1, 5)
for x in range(19, 44, 4): B.anchor("guardian_kneel", x, 1, 93)
for z in range(12, 92, 4):
    B.anchor("line_left", 21, 1, z); B.anchor("line_right", W - 22, 1, z)
B.anchor("entrance", CX, 1, 0)
