"""King's Quarters (Royal Suite), v2: architecture only, no furniture.
Hall with arcades, galleries and dome; raised dais (future desk/dispatch); bedroom + dressing room shells.
Entrance at north (z=0). x: backing 0, wall 1, aisle 2-6, piers 7-8, nave 9-25 (mirror across x=17)."""
import math, random
from lib import Build, banner_nbt, pointed, ring_around

W, H, D = 35, 28, 54
CX = 17
B = Build(W, H, D, "kings_quarters")
rnd = random.Random(9)

P = [2, 8, 14, 20, 26, 32]                 # piers (2 deep); one more single pier at z=38
SPANS = [(p + 2, p + 5) for p in P]
HZ0, HZ1 = 2, 38                           # hall rows
BACKW = 39                                 # back wall of the hall
RWALL = 40                                 # room-side layer of the back wall
RZ0, RZ1 = 41, 51                          # private rooms

def m(x, y, z, spec, nbt=None): B.mset(x, y, z, spec, nbt)

# ================================================================== shell
B.fill(0, 0, 0, W - 1, 0, D - 1, "smooth_quartz")
for x in (0, 1, W - 2, W - 1): B.fill(x, 1, 0, x, H - 1, D - 1, "quartz_bricks")
for z in (0, 1, D - 2, D - 1): B.fill(0, 1, z, W - 1, H - 1, z, "quartz_bricks")
B.fill(2, 19, 2, W - 3, 20, HZ1, "smooth_quartz")            # hall ceiling (2 layers for coffers)
B.fill(2, 1, BACKW, W - 3, 20, RWALL, "quartz_bricks")      # back wall (2 thick)
B.fill(2, 11, RWALL, W - 3, 20, RZ1, "quartz_bricks")         # mass over the rooms
B.fill(2, 1, RZ0, W - 3, 3, RZ1, "smooth_quartz")           # raised room floor
B.fill(CX, 4, RZ0, CX, 10, RZ1, "quartz_bricks")            # partition
for z in range(HZ0, HZ1 + 1): B.fill(2, 17, z, 9, 18, z, "smooth_quartz"); B.fill(W - 10, 17, z, W - 3, 18, z, "smooth_quartz")

# ---------------------------------------------------------------- hall floors
for x in range(2, W - 2):
    for z in range(HZ0, HZ1 + 1):
        if 9 <= x <= 25:
            spec = "polished_diorite" if x in (9, 25) or z == HZ0 else "smooth_quartz"
        else:
            spec = "polished_diorite" if (x + z) % 4 == 0 or (x - z) % 4 == 0 else "smooth_quartz"
        B.set(x, 0, z, spec)
for x in range(10, 25):
    for z in range(3, 30): B.set(x, 1, z, "purple_carpet")

# ================================================================== piers & arcades
for p in P + [38]:
    zs = (p, p + 1) if p != 38 else (38,)
    for z in zs:
        m(7, 1, z, "quartz_bricks"); m(8, 1, z, "quartz_bricks")
        for y in range(2, 8):
            m(7, y, z, "quartz_bricks")
            m(8, y, z, "chiseled_quartz_block" if y in (2, 7) else ("blue_terracotta" if p not in (32, 38) else "quartz_bricks"))
        m(7, 8, z, "chiseled_quartz_block"); m(8, 8, z, "chiseled_quartz_block")
        m(7, 9, z, "smooth_quartz"); m(8, 9, z, "smooth_quartz")
        for y in range(10, 17): m(7, y, z, "quartz_pillar[axis=y]"); m(8, y, z, "quartz_pillar[axis=y]")
        if p not in (32, 38):
            m(9, 1, z, "smooth_quartz_stairs[facing=west,half=bottom]")   # base flare
            m(9, 8, z, "smooth_quartz_stairs[facing=west,half=top]")      # capital corbel
        m(6, 1, z, "smooth_quartz_stairs[facing=east,half=bottom]")
        m(6, 8, z, "smooth_quartz_stairs[facing=east,half=top]")
        m(9, 10, z, "smooth_quartz_stairs[facing=west,half=bottom]")      # upper column base
        m(9, 16, z, "smooth_quartz_stairs[facing=west,half=top]")         # upper capital
for z0, z1 in SPANS:
    for z in range(z0, z1 + 1):
        for x in (7, 8):
            m(x, 8, z, "chiseled_quartz_block" if z in (z0 + 1, z0 + 2) and x == 8 else "quartz_bricks")
            m(x, 9, z, "smooth_quartz")
            m(x, 16, z, "chiseled_quartz_block" if z in (z0 + 1, z0 + 2) and x == 8 else "quartz_bricks")
        m(8, 10, z, "diorite_wall"); m(8, 11, z, "smooth_quartz_slab[type=bottom]")   # balustrade + rail
    for x in (7, 8):
        m(x, 7, z0, "smooth_quartz_stairs[facing=north,half=top]"); m(x, 7, z1, "smooth_quartz_stairs[facing=south,half=top]")
        m(x, 15, z0, "smooth_quartz_stairs[facing=north,half=top]"); m(x, 15, z1, "smooth_quartz_stairs[facing=south,half=top]")
for z in range(HZ0, HZ1 + 1):
    m(2, 9, z, "smooth_quartz")
    for x in range(3, 7): m(x, 9, z, "polished_diorite" if (x + z) % 4 == 0 else "smooth_quartz")
    m(9, 9, z, "smooth_quartz_slab[type=top]")                  # balcony nosing
    m(9, 17, z, "smooth_quartz_stairs[facing=west,half=top]")   # cornice tier 1
    m(10, 18, z, "smooth_quartz_stairs[facing=west,half=top]")  # cornice tier 2

# ivy over the balconies
for z0, z1 in SPANS[:5]:
    for z in range(z0, z1 + 1):
        for side in (0, 1):
            xv, face = (9, "west") if side == 0 else (W - 10, "east")
            for i in range(rnd.choice([0, 1, 2, 3, 4, 5])):
                B.set(xv, 8 - i, z, f"vine[{face}=true]")
            if rnd.random() < .6: B.set(8 if side == 0 else W - 9, 12, z, "azalea_leaves[persistent=true]")
            if rnd.random() < .3: B.set(xv, 10, z, "azalea_leaves[persistent=true]")

# candle sconces on the piers
for p in P[:-1]:
    for z in (p, p + 1):
        m(9, 4, z, "gold_block"); m(9, 5, z, "white_candle[candles=3,lit=true]")

# ================================================================== aisles & galleries (outer walls)
for z in range(HZ0, HZ1 + 1):
    m(1, 1, z, "polished_diorite"); m(1, 2, z, "polished_diorite")
for p in P:
    for z in (p, p + 1):
        for y in range(1, 9): m(2, y, z, "quartz_pillar[axis=y]")
        for y in range(10, 17): m(2, y, z, "quartz_pillar[axis=y]")
        for x in range(3, 7): m(x, 8, z, "quartz_bricks")     # aisle cross-beams
    m(4, 7, p, "lantern[hanging=true]")
    z0, z1 = p + 2, p + 5
    # lower window
    for z in range(z0, z1 + 1):
        m(1, 2, z, "chiseled_quartz_block"); m(1, 8, z, "chiseled_quartz_block")
    for z in (z0 + 1, z0 + 2):
        for y in range(3, 8):
            m(1, y, z, "chiseled_quartz_block" if y == 5 else "white_stained_glass"); m(0, y, z, "sea_lantern")
    # gallery window (pointed, 4 wide, quartz transom)
    for z, y in pointed(z0, z1, 11, 14):
        m(1, y, z, "chiseled_quartz_block" if y == 13 else "white_stained_glass"); m(0, y, z, "sea_lantern")
    for z, y in ring_around(pointed(z0, z1, 11, 14), diag=False):
        if y >= 11: m(1, y, z, "chiseled_quartz_block" if y >= 15 else "quartz_bricks")
    m(4, 16, p + 3, "lantern[hanging=true]")
    m(4, 17, p + 3, "smooth_quartz")
# gallery stairs at the front of each aisle
for k in range(1, 9):
    z = 2 + k
    for x in (2, 3):
        B.mfill(x, 1, z, x, k - 1, z, "quartz_bricks")
        m(x, k, z, "smooth_quartz_stairs[facing=south,half=bottom]")
        for y in range(k + 1, 9): m(x, y, z, "air")
B.mfill(2, 9, 3, 3, 9, 10, "air")
for z in range(3, 11): m(4, 10, z, "diorite_wall")
m(2, 10, 2, "diorite_wall"); m(3, 10, 2, "diorite_wall")

# ================================================================== coffered ceiling + dome
for x in range(11, 24):
    for z in range(3, 36):
        if (x - 11) % 4 == 0 or (z - 3) % 4 == 0:
            B.set(x, 19, z, "quartz_bricks")
        else:
            B.set(x, 19, z, "air")
            B.set(x, 20, z, "gold_block" if (x - 11) % 4 == 2 and (z - 3) % 4 == 2 else "light_blue_terracotta")
DZ = 15
for x in range(W):
    for z in range(HZ1 + 1):
        d = math.hypot(x - CX, z - DZ)
        if d <= 7.5:
            B.set(x, 19, z, "chiseled_quartz_block" if d > 6.5 else "air"); B.set(x, 20, z, "air")
        if d <= 8.6:
            for y in range(20, H):
                r3 = math.hypot(d, y - 19)
                if r3 <= 7.5: continue
                surface = r3 <= 8.6
                rib = int(round((math.atan2(z - DZ, x - CX) + math.pi) / (2 * math.pi) * 16)) % 2 == 0
                if surface and y == 22: spec = "gold_block"
                elif surface and rib: spec = "quartz_bricks"
                elif surface and 23 <= y <= 25: spec = "light_blue_terracotta"
                else: spec = "smooth_quartz"
                B.set(x, y, z, spec)
for x in range(CX - 1, CX + 2):
    for z in range(DZ - 1, DZ + 2):
        if math.hypot(x - CX, z - DZ) <= 1.5: B.set(x, H - 1, z, "pearlescent_froglight")
# chandelier
def ring(r): return [(dx, dz) for dx in range(-5, 6) for dz in range(-5, 6) if abs(math.hypot(dx, dz) - r) < .5]
SPOKES = [(4, 0), (-4, 0), (0, 4), (0, -4), (3, 3), (3, -3), (-3, 3), (-3, -3)]
for y in range(18, H - 1): B.set(CX, y, DZ, "chain[axis=y]")
for dx, dz in ring(2): B.set(CX + dx, 18, DZ + dz, "end_rod[facing=up]")
for dx, dz in ring(4): B.set(CX + dx, 17, DZ + dz, "end_rod[facing=down]")
for dx, dz in SPOKES:
    B.set(CX + dx, 17, DZ + dz, "gold_block"); B.set(CX + dx, 16, DZ + dz, "lantern[hanging=true]")
for d in (1, 2, 3):
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        B.set(CX + dx * d, 17, DZ + dz * d, "chain[axis=x]" if dx else "chain[axis=z]")
B.set(CX, 17, DZ, "gold_block")
for r, y in ((3, 16), (2, 15), (1, 14)):
    for dx, dz in ring(r):
        if (dx, dz) not in SPOKES: B.set(CX + dx, y, DZ + dz, "end_rod[facing=down]")
for y in (16, 15, 14, 13): B.set(CX, y, DZ, "end_rod[facing=down]")

# ================================================================== front wall: entrance, pediment, rose, blind arches
OPEN = pointed(16, 18, 1, 5)
for x, y in OPEN: B.set(x, y, 0, "air"); B.set(x, y, 1, "air")
for x in range(2, W - 2):
    if (x, 1) not in OPEN: B.set(x, 1, 1, "polished_diorite"); B.set(x, 2, 1, "polished_diorite")
for y in range(1, 7): m(15, y, 2, "quartz_pillar[axis=y]")
for x in range(14, 21): B.set(x, 7, 2, "chiseled_quartz_block")
m(15, 8, 2, "smooth_quartz_stairs[facing=east,half=bottom]"); B.fill(16, 8, 2, 18, 8, 2, "quartz_bricks")
m(16, 9, 2, "smooth_quartz_stairs[facing=east,half=bottom]"); B.set(17, 9, 2, "chiseled_quartz_block")
for x in range(CX - 4, CX + 5):
    for y in range(11, 20):
        d = math.hypot(x - CX, y - 15)
        if d <= 3.2:
            ang = math.degrees(math.atan2(y - 15, x - CX)) % 360
            spoke = min(ang % 60, 60 - ang % 60) < 8 and d > 1
            B.set(x, y, 1, "gold_block" if d < 1 or spoke else ("purple_stained_glass" if d > 2.2 else "white_stained_glass"))
            B.set(x, y, 0, "sea_lantern")
        elif d <= 4.1:
            B.set(x, y, 2, "chiseled_quartz_block" if int(math.degrees(math.atan2(y - 15, x - CX)) // 30) % 2 else "quartz_bricks")
AOG = [("bo", "yellow"), ("mc", "purple"), ("sku", "white"), ("tts", "yellow")]
B.set(CX, 11, 2, "black_wall_banner[facing=south]", banner_nbt(AOG))
for x in (12, 22):
    B.set(x, 11, 2, "purple_wall_banner[facing=south]", banner_nbt([("bs", "black"), ("cbo", "yellow")]))
for x0 in (10,):        # blind arches either side of the entrance
    cells = pointed(x0, x0 + 3, 3, 13)
    for x, y in cells:
        if (x, y) == (12, 11): continue
        m(x, y, 1, "blue_terracotta")
    for x, y in ring_around(cells, diag=False):
        if y >= 3: m(x, y, 2, "quartz_pillar[axis=y]" if y <= 13 else "chiseled_quartz_block")
B.set(12, 11, 1, "quartz_bricks"); B.set(22, 11, 1, "quartz_bricks")

# ================================================================== dais (empty: reserved for the ruler's desk)
for i, z in enumerate((30, 31, 32)):
    for x in range(9, 26):
        B.fill(x, 0, z, x, i, z, "smooth_quartz")
        if x in (9, 25):
            B.set(x, i + 1, z, "quartz_bricks"); B.set(x, i + 2, z, "diorite_wall")
        else:
            B.set(x, i + 1, z, "smooth_quartz_stairs[facing=south,half=bottom]")
for x in (9, 25):
    B.fill(x, 1, 29, x, 3, 29, "quartz_bricks"); B.set(x, 4, 29, "chiseled_quartz_block"); B.set(x, 5, 29, "lantern[hanging=false]")
for x in range(9, 26):
    for z in range(33, HZ1 + 1):
        B.fill(x, 1, z, x, 2, z, "smooth_quartz")
        border = x in (9, 25) or z in (33, HZ1)
        B.set(x, 3, z, "chiseled_quartz_block" if border else ("polished_diorite" if (x - CX) % 3 == 0 or (z - 33) % 3 == 0 else "smooth_quartz"))
for z in range(34, 38):                     # last arch on each side becomes a balcony over the aisle
    m(7, 1, z, "quartz_bricks"); m(8, 1, z, "quartz_bricks")
    for y in (2, 3): m(7, y, z, "quartz_bricks"); m(8, y, z, "quartz_bricks")
    m(8, 4, z, "diorite_wall"); m(8, 5, z, "smooth_quartz_slab[type=bottom]")

# ================================================================== back wall of the hall (z=39)
for x in range(9, 26): B.set(x, 11, HZ1, "smooth_quartz_stairs[facing=south,half=top]")   # cornice
# great doors (opening runs through both wall layers)
for y in (4, 5):
    m(11, y, RWALL, 'air'); m(12, y, RWALL, 'air')
for x in (10, 13):
    for y in range(4, 10): m(x, y, RWALL, 'spruce_planks')
for x in (10, 11, 12, 13): m(x, 10, RWALL, 'chiseled_quartz_block')
for x in range(10, 14):
    for y in range(4, 10):
        spec = "spruce_planks"
        if x in (10, 13) and 6 <= y <= 8: spec = "stripped_spruce_log[axis=y]"
        if x in (11, 12) and y == 7: spec = "stripped_spruce_log[axis=y]"
        if x in (11, 12) and y == 9: spec = "gold_block"
        m(x, y, BACKW, spec)
for y, half in ((4, "lower"), (5, "upper")):
    m(11, y, BACKW, f"spruce_door[facing=north,half={half},hinge=left,open=false,powered=false]")
    m(12, y, BACKW, f"spruce_door[facing=north,half={half},hinge=right,open=false,powered=false]")
for y in range(4, 11): m(9, y, HZ1, "quartz_pillar[axis=y]"); m(14, y, HZ1, "quartz_pillar[axis=y]")
for x in range(9, 15): m(x, 10, BACKW, "chiseled_quartz_block"); m(x, 10, HZ1, "chiseled_quartz_block")
# guild emblem relief
for x in range(15, 20):
    for y in range(4, 11):
        if x in (15, 19) or y in (4, 10): B.set(x, y, BACKW, "chiseled_quartz_block")
        else: B.set(x, y, BACKW, "blue_terracotta")
for y in range(5, 9): B.set(CX, y, BACKW, "gold_block")
B.set(16, 7, BACKW, "gold_block"); B.set(18, 7, BACKW, "gold_block"); B.set(CX, 9, BACKW, "amethyst_block")
B.set(16, 9, BACKW, "gold_block"); B.set(18, 9, BACKW, "gold_block")
# three backlit gothic windows
def gwin(x0, x1, mull):
    cells = pointed(x0, x1, 13, 16)
    for x, y in cells:
        tr = y == 15 or (mull and x == (x0 + x1) // 2 and y <= 17)
        B.set(x, y, BACKW, "air"); B.set(x, y, BACKW + 1, "gold_block" if tr else "blue_stained_glass")
        B.set(x, y, BACKW + 2, "sea_lantern")
    for x, y in ring_around(cells, diag=False):
        if y >= 13: B.set(x, y, HZ1, "quartz_bricks" if y < 17 else "chiseled_quartz_block")
gwin(15, 19, True); gwin(10, 13, False); gwin(21, 24, False)
for x in (11, 12, 22, 23): B.set(x, 18, HZ1, "gold_block")
for z in (BACKW,):
    for x in list(range(2, 7)) + list(range(W - 7, W - 2)):
        B.set(x, 1, z, "polished_diorite"); B.set(x, 2, z, "polished_diorite")

# ================================================================== private rooms (empty shells), mirrored
for x in range(2, CX):
    for z in range(RZ0, RZ1 + 1):
        border = x in (2, CX - 1) or z in (RZ0, RZ1)
        spec = "stripped_dark_oak_wood[axis=y]" if border else ("dark_oak_planks" if (x // 2 + z) % 2 else "spruce_planks")
        m(x, 3, z, spec)
        # coffered ceiling
        if (x - 2) % 4 == 0 or (z - RZ0) % 4 == 0:
            m(x, 11, z, "dark_oak_planks")
        else:
            m(x, 11, z, "air")
            m(x, 12, z, "gold_block" if (x - 2) % 4 == 2 and (z - RZ0) % 4 == 2 else "smooth_quartz")
def wall_face(x, z):
    """Decorate the wall block at (x, *, z) facing into a room."""
    u = x + z
    for y in range(4, 11):
        if y <= 5: spec = "dark_oak_planks"
        elif y == 6: spec = "stripped_dark_oak_wood[axis=y]"
        elif u % 4 == 0: spec = "quartz_pillar[axis=y]"
        elif y == 10: spec = "smooth_quartz"
        else: spec = "light_blue_terracotta"
        m(x, y, z, spec)
for z in range(RZ0, RZ1 + 1): wall_face(1, z); wall_face(CX, z)
for x in range(2, CX):
    for z in (RWALL, D - 2):
        if not (z == RWALL and 10 <= x <= 13): wall_face(x, z)
for z in range(RZ0, RZ1 + 1):          # chair rail + crown molding along the long walls
    m(2, 6, z, "dark_oak_slab[type=top]"); m(2, 10, z, "smooth_quartz_stairs[facing=west,half=top]")
    B.set(CX - 1, 6, z, "dark_oak_slab[type=top]"); B.set(CX + 1, 6, z, "dark_oak_slab[type=top]")
    B.set(CX - 1, 10, z, "smooth_quartz_stairs[facing=east,half=top]"); B.set(CX + 1, 10, z, "smooth_quartz_stairs[facing=west,half=top]")
for x in range(2, CX):
    if not 10 <= x <= 13: m(x, 6, RZ0, "dark_oak_slab[type=top]")
    m(x, 6, RZ1, "dark_oak_slab[type=top]")
    m(x, 10, RZ0, "smooth_quartz_stairs[facing=north,half=top]"); m(x, 10, RZ1, "smooth_quartz_stairs[facing=south,half=top]")
for x0 in (5, 12):                       # tall windows in the outer wall
    cells = pointed(x0, x0 + 1, 5, 9)
    for x, y in cells:
        m(x, y, D - 2, "white_stained_glass" if y != 7 else "chiseled_quartz_block"); m(x, y, D - 1, "sea_lantern")
    for x, y in ring_around(cells, diag=False):
        if y >= 5: m(x, y, RZ1, "quartz_bricks" if y < 10 else "chiseled_quartz_block")
m(9, 10, 44, "lantern[hanging=true]"); m(9, 11, 44, "dark_oak_planks")
m(9, 10, 48, "lantern[hanging=true]"); m(9, 11, 48, "dark_oak_planks")
# bedroom: soul-fire hearth (west wall)
for z in range(45, 48):
    for y in (4, 5): B.set(1, y, z, "air"); B.set(0, y, z, "polished_blackstone_bricks")
    B.set(1, 3, z, "polished_blackstone"); B.set(2, 3, z, "polished_blackstone"); B.set(3, 3, z, "polished_blackstone")
B.set(1, 4, 46, "soul_campfire[lit=true,facing=east,signal_fire=false,waterlogged=false]")
for z in range(44, 49):
    for y in range(4, 11):
        if z in (44, 48) or y >= 6: B.set(2, y, z, "polished_blackstone_bricks")
    B.set(3, 6, z, "polished_blackstone_brick_slab[type=top]")
for z in range(45, 48):
    for y in (4, 5): B.set(2, y, z, "air")
B.set(2, 8, 46, "gilded_blackstone"); B.set(2, 7, 46, "gold_block"); B.set(2, 9, 46, "gold_block")
# dressing room: arched niches (east wall)
for z0 in (42, 47):
    cells = pointed(z0, z0 + 2, 4, 8)
    for z, y in cells:
        B.set(W - 2, y, z, "air"); B.set(W - 1, y, z, "blue_terracotta")
    for z, y in ring_around(cells, diag=False):
        if y >= 4: B.set(W - 2, y, z, "chiseled_quartz_block" if y >= 9 else "quartz_pillar[axis=y]")
    B.set(W - 1, 6, z0 + 1, "gold_block")

# ================================================================== anchors (where furniture/NPCs will go later)
B.anchor("entrance", CX, 1, 0)
B.entity(22, 4, 36, "nazarick:sebas", 180.0)
B.anchor("desk_spot", CX, 4, 35)
B.anchor("ruler_chair_spot", CX, 4, 37)
B.anchor("sebas_stand", 22, 4, 36)
B.anchor("lounge_center", CX, 1, 15)
B.anchor("bed_spot", 9, 4, 50)
B.anchor("duty_maid", 25, 4, 45)
for z in (7, 23): B.anchor("maid_post", 10, 1, z); B.anchor("maid_post", 24, 1, z)
