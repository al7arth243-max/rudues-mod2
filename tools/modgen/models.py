"""The four entity models: Albedo & Sebas (player-skin NPCs), Elder Lich, Death Knight.
Coordinates follow vanilla ModelPart conventions: y down, front = -z, entity's right = -x.
Rotations in degrees: -x rot swings a limb forward/up; +x rot on the waist bends forward."""
from core import Cube, Part, Model, mirror_part, faces, shade

R2L = lambda n: n.replace("right", "left")

# ================================================================== player-skin NPC skeleton
def player_skeleton(slim, head_children=(), body_children=()):
    aw = 3 if slim else 4
    ay = -9.5 if slim else -10.0
    head = Part("head", (0, -12, 0), cubes=[
        Cube(-4, -8, -4, 8, 8, 8, "skin", 0.0, (0, 0)), Cube(-4, -8, -4, 8, 8, 8, "skin", 0.5, (32, 0))],
        children=list(head_children))
    body = Part("body", (0, -12, 0), cubes=[
        Cube(-4, 0, -2, 8, 12, 4, "skin", 0.0, (16, 16)), Cube(-4, 0, -2, 8, 12, 4, "skin", 0.25, (16, 32))],
        children=list(body_children))
    rarm = Part("right_arm", (-5, ay, 0), cubes=[
        Cube(-2 if slim else -3, -2, -2, aw, 12, 4, "skin", 0.0, (40, 16)),
        Cube(-2 if slim else -3, -2, -2, aw, 12, 4, "skin", 0.25, (40, 32))])
    larm = Part("left_arm", (5, ay, 0), cubes=[
        Cube(-1, -2, -2, aw, 12, 4, "skin", 0.0, (32, 48)), Cube(-1, -2, -2, aw, 12, 4, "skin", 0.25, (48, 48))])
    waist = Part("waist", (0, 12, 0), children=[body, head, rarm, larm])
    rleg = Part("right_leg", (-1.9, 12, 0), cubes=[
        Cube(-2, 0, -2, 4, 12, 4, "skin", 0.0, (0, 16)), Cube(-2, 0, -2, 4, 12, 4, "skin", 0.25, (0, 32))])
    lleg = Part("left_leg", (1.9, 12, 0), cubes=[
        Cube(-2, 0, -2, 4, 12, 4, "skin", 0.0, (16, 48)), Cube(-2, 0, -2, 4, 12, 4, "skin", 0.25, (0, 48))])
    return [waist, rleg, lleg]

# Albedo: slim skin + horns + black wings (extras packed into the right half of a 128x64 texture)
r_horn = Part("right_horn", (-3.3, -7.2, -0.5), (-10, 0, -40), [Cube(-1, -3, -1, 2, 3, 2, "horn")], [
    Part("right_horn2", (0, -3, 0), (40, 0, -45), [Cube(-1, -3, -1, 2, 3, 2, "horn", -0.15)], [
        Part("right_horn3", (0, -3, 0), (60, 0, -25), [Cube(-0.5, -3, -0.5, 1, 3, 1, "horn")])])])
r_wing = Part("right_wing", (-1.5, 8.5, 2.1), (8, 28, -12), [Cube(-8, -4, 0, 8, 12, 0, "wing_inner")], [
    Part("right_wing_tip", (-8, -4, 0), (0, 22, -8), [Cube(-9, -2, 0, 9, 15, 0, "wing_outer")])])
halberd = Part("halberd", (-0.5, 9.5, 0), (90, 0, 0), [
    Cube(-0.5, -20, -0.5, 1, 30, 1, "halberd_shaft"),
    Cube(-0.5, -23, -5, 1, 8, 4, "halberd_blade"),
    Cube(-0.5, -26, -1, 1, 4, 1, "halberd_blade"),
    Cube(-1, -16, -1, 2, 1, 2, "gold")])
def albedo_parts():
    parts = player_skeleton(True, [r_horn, mirror_part(r_horn, R2L)], [r_wing, mirror_part(r_wing, R2L)])
    for p in parts[0].children:
        if p.name == "right_arm": p.children.append(halberd)
    return parts
ALBEDO = Model("albedo", "AlbedoModel", 128, 64, albedo_parts(), pack_region=(64, 0))
MAID = Model("homunculus_maid", "HomunculusMaidModel", 64, 64, player_skeleton(False))
SEBAS = Model("sebas", "SebasModel", 64, 64, player_skeleton(False))

# ================================================================== Elder Lich (128x64)
def lich_parts():
    r_arm = Part("right_arm", (-5.5, -10.5, 0), (0, 0, 0), [
        Cube(-2.5, -1.5, -2.5, 4, 9, 5, "robe_dark"),
        Cube(-1.5, 7, -1, 2, 5, 2, "bone"),
        Cube(-2, 11, -1.5, 3, 2, 3, "bone")])
    head = Part("head", (0, -12, 0), cubes=[
        Cube(-3.5, -7.5, -3.5, 7, 7, 7, "skull"),
        Cube(-4.5, -9, -4.5, 9, 1, 9, "hood"),
        Cube(3.5, -8, -4.5, 1, 9, 9, "hood"),
        Cube(-4.5, -8, -4.5, 1, 9, 9, "hood"),
        Cube(-3.5, -8, 3.5, 7, 9, 1, "hood"),
        Cube(-2, -11, 0, 4, 2, 4, "hood"),
        Cube(-3.5, -8.6, -4.9, 7, 1, 1, "hood")], children=[
        Part("jaw", (0, -1.5, 0), cubes=[Cube(-2.5, 0, -3.7, 5, 2, 4, "jaw")]),
        Part("halo", (0, -10.5, 0), (12, 0, 8), [
            Cube(-6, -1, -6, 12, 2, 0, "ribbon"), Cube(-6, -1, 6, 12, 2, 0, "ribbon"),
            Cube(6, -1, -6, 0, 2, 12, "ribbon"), Cube(-6, -1, -6, 0, 2, 12, "ribbon")])])
    torso = Part("torso", (0, -12, 0), cubes=[
        Cube(-4, 0, -2.5, 8, 12, 5, "robe"),
        Cube(-5.5, -0.5, -3, 11, 4, 6, "robe_dark")], children=[
        Part("necklace", (0, 0, 0), cubes=[
            Cube(-1.5, 4.0, -5, 3, 3, 3, "orb_blue"),
            Cube(1.8, 3.0, -4.3, 2, 2, 2, "orb_blue"), Cube(-3.8, 3.0, -4.3, 2, 2, 2, "orb_blue"),
            Cube(3.4, 1.0, -4.0, 2, 2, 2, "orb_blue"), Cube(-5.4, 1.0, -4.0, 2, 2, 2, "orb_blue")]),
        Part("skirt", (0, 12, 0), cubes=[Cube(-5, 0, -3, 10, 12, 6, "robe_skirt")]),
        Part("cape", (0, 0, 3), cubes=[Cube(-5.5, -0.5, 0, 11, 25, 0, "lich_cape")])])
    waist = Part("waist", (0, -12, 0), children=[torso, head, r_arm, mirror_part(r_arm, R2L)])
    orbs = Part("orbs", (0, -16, 0), cubes=[
        Cube(6, -1, -1, 2, 2, 2, "orb_red"), Cube(-4.5, 0, 4.5, 2, 2, 2, "orb_red"), Cube(-4, -2, -6.5, 2, 2, 2, "orb_red")])
    return [Part("float", (0, 24, 0), children=[waist, orbs])]
LICH = Model("elder_lich", "ElderLichModel", 128, 64, lich_parts())

# ================================================================== Death Knight (128x128, 3 blocks tall)
def dk_parts():
    r_leg = Part("right_leg", (-3.5, 4, 0), cubes=[
        Cube(-2.5, 0, -2.5, 5, 17, 5, "metal"), Cube(-3, 5, -3.5, 6, 4, 1, "metal_light"),
        Cube(-3, 17, -4, 6, 3, 7, "metal_dark")])
    r_horn = Part("right_horn", (-4.5, -6, 0), (0, 0, -30), [Cube(-1, -5, -1, 2, 5, 2, "metal")], [
        Part("right_horn2", (0, -5, 0), (-20, 0, 35), [Cube(-0.5, -4, -0.5, 1, 4, 1, "metal_light")])])
    head = Part("head", (0, -16, 0), cubes=[
        Cube(-4.5, -9, -4.5, 9, 9, 9, "helmet"), Cube(-5, -7, -5, 10, 2, 1, "metal_dark"),
        Cube(-1, -12, -2, 2, 3, 5, "metal")], children=[
        r_horn, mirror_part(r_horn, R2L),
        Part("jaw", (0, -2, 0), cubes=[Cube(-3, 0, -4.8, 6, 2, 1, "dk_jaw")])])
    blade = [Cube(-0.5, 2 + 4 * i, -1.5 + (0.5 if i % 2 else -0.5), 1, 4, 3, "blade") for i in range(6)]
    sword = Part("sword", (0, 17.5, -0.5), (-50, 0, 0), [
        Cube(-1, -5, -1, 2, 1, 2, "gold"), Cube(-0.5, -4, -0.5, 1, 5, 1, "leather"),
        Cube(-1, 1, -3.5, 2, 1, 7, "gold")] + blade + [Cube(-0.5, 26, -1, 1, 3, 2, "blade")])
    def arm(side):
        s = -1 if side == "right" else 1
        cubes = [Cube(-4.5 if s < 0 else -3.5, -4, -4.5, 8, 6, 9, "metal"),
                 Cube(-2.5, 1, -2.5, 5, 9, 5, "metal_dark"),
                 Cube(-3, 9, -3, 6, 8, 6, "metal"),
                 Cube(-2.5, 16, -2.5, 5, 3, 5, "metal_dark")]
        spike = Part(f"{side}_spike", (2 * s, -4, 0), (0, 0, 25 * s), [Cube(-1, -5, -1, 2, 5, 2, "metal_light")])
        kids = [spike]
        if side == "right": kids.append(sword)
        else: kids.append(Part("shield", (2, 8, 0), cubes=[Cube(-7, -8, -6.5, 14, 30, 2, "shield")]))
        return Part(f"{side}_arm", (8.5 * s, -13, 0), cubes=cubes, children=kids)
    torso = Part("torso", (0, -3, 0), cubes=[
        Cube(-5, -13, -3, 10, 13, 6, "ribs"), Cube(-6, -16, -3.5, 12, 5, 7, "metal"),
        Cube(-4, -17, -3, 8, 2, 6, "metal_dark")], children=[
        Part("cape", (0, -15, 3.6), cubes=[Cube(-9, 0, 0, 18, 34, 0, "dk_cape")]),
        head, arm("right"), arm("left")])
    waist = Part("waist", (0, 4, 0), cubes=[Cube(-5.5, -3, -3.5, 11, 5, 7, "metal_dark")], children=[
        Part("loin_front", (0, 1, -3.6), cubes=[Cube(-4, 0, 0, 8, 12, 0, "rags")]),
        Part("loin_back", (0, 1, 3.6), cubes=[Cube(-5, 0, 0, 10, 14, 0, "rags")]),
        torso])
    return [r_leg, mirror_part(r_leg, R2L), waist]
DK = Model("death_knight", "DeathKnightModel", 128, 128, dk_parts())

# ================================================================== painters
def p_generic(base, var=8, edge=0.8):
    def f(P, c, R):
        for k, r in R.items():
            P.noise_rect(r, base, var)
            x, y, w, h = r
            if w > 2 and h > 2:
                for i in range(w): P.px(x + i, y + h - 1, shade(base, edge))
    return f

def p_robe(base):
    def f(P, c, R):
        for k, (x, y, w, h) in R.items():
            P.noise_rect((x, y, w, h), base, 5)
            if k in ("front", "back", "east", "west"):
                for i in range(0, w, 3):
                    for j in range(h): P.px(x + i, y + j, shade(base, 0.72))
                for i in range(w): P.px(x + i, y + h - 1, (88, 52, 110))          # purple hem
    return f

def p_skull(P, c, R):
    skin, dark, shadow = (205, 190, 196), (40, 26, 38), (150, 128, 140)
    for k, r in R.items(): P.noise_rect(r, (30, 22, 34), 4)    # hood interior on sides/back/top
    x, y, w, h = R["front"]
    P.noise_rect((x, y, w, h), skin, 7)
    for i in range(w): P.px(x + i, y, (45, 32, 48)); P.px(x + i, y + 1, shadow)
    for (i, j) in ((1, 2), (2, 2), (4, 2), (5, 2), (1, 3), (5, 3)): P.px(x + i, y + j, dark)
    P.px(x + 1, y + 2, (230, 40, 50)); P.px(x + 1, y + 2, (255, 60, 70), glow=True)   # the red eye
    P.px(x + 2, y + 2, (120, 20, 30))
    P.px(x + 3, y + 3, shadow); P.px(x + 3, y + 4, dark)
    for i in range(1, 6): P.px(x + i, y + 5, (225, 220, 210) if i % 2 else (150, 40, 50))
    for i in range(1, 6): P.px(x + i, y + 6, (200, 40, 55))
    for i in (0, 6):
        for j in range(2, 7): P.px(x + i, y + j, shadow)

def p_jaw(P, c, R):
    for k, r in R.items(): P.noise_rect(r, (190, 175, 182), 6)
    x, y, w, h = R["front"]
    for i in range(w): P.px(x + i, y, (235, 230, 220) if i % 2 == 0 else (160, 40, 52))
    for i in range(1, w - 1): P.px(x + i, y + 1, (175, 160, 168))

def p_bone(P, c, R):
    for k, (x, y, w, h) in R.items():
        P.noise_rect((x, y, w, h), (196, 186, 178), 8)
        for j in range(0, h, 3): P.px(x + P.r.randrange(max(1, w)), y + j, (150, 138, 132))

def p_orb(base, hi, glow=False):
    def f(P, c, R):
        for k, (x, y, w, h) in R.items():
            P.noise_rect((x, y, w, h), base, 6)
            if glow:
                for i in range(w):
                    for j in range(h): P.px(x + i, y + j, shade(base, 1.1), glow=True)
            P.px(x, y, hi)
            if glow: P.px(x, y, hi, glow=True)
            if w > 1 and h > 1: P.px(x + w - 1, y + h - 1, shade(base, 0.6))
    return f

def p_ribbon(P, c, R):
    for k, (x, y, w, h) in R.items():
        for i in range(w):
            for j in range(h):
                stripe = (i // 3) % 4 == 0
                P.px(x + i, y + j, (40, 34, 20) if stripe else (226 + P.r.randint(-10, 10), 178, 58))

def p_tattered(base, var=6, holes=0.03, depth=5):
    def f(P, c, R):
        for k, (x, y, w, h) in R.items():
            P.noise_rect((x, y, w, h), base, var)
            if k in ("front", "back"):
                cut = [P.r.randint(0, depth) for _ in range(w)]
                for i in range(w):
                    for j in range(cut[i]): P.clear(x + i, y + h - 1 - j)
                    if i % 2 == 0:
                        for j in range(h):
                            if P.r.random() < 0.25: P.px(x + i, y + j, shade(base, 0.7))
                for i in range(w):
                    for j in range(h - depth - 1):
                        if P.r.random() < holes: P.clear(x + i, y + j)
    return f

def p_metal(base):
    def f(P, c, R):
        for k, (x, y, w, h) in R.items():
            P.noise_rect((x, y, w, h), base, 7)
            for i in range(w): P.px(x + i, y, shade(base, 1.35))
            for j in range(h): P.px(x, y + j, shade(base, 1.2))
            for i in range(w): P.px(x + i, y + h - 1, shade(base, 0.6))
            if w >= 4 and h >= 4:
                for (i, j) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)): P.px(x + i, y + j, (150, 150, 158))
            for _ in range(max(1, w * h // 25)):
                i, j = P.r.randrange(w), P.r.randrange(h); P.px(x + i, y + j, shade(base, 1.5))
    return f

def p_helmet(P, c, R):
    p_metal((76, 76, 84))(P, c, R)
    x, y, w, h = R["front"]   # 9 x 9 skull face inside the helm
    bone, dark = (200, 192, 178), (14, 10, 12)
    for i in range(1, 8):
        for j in range(2, 9): P.px(x + i, y + j, bone if P.r.random() > .15 else (180, 170, 156))
    for (i, j) in ((2, 3), (3, 3), (5, 3), (6, 3), (2, 4), (3, 4), (5, 4), (6, 4)): P.px(x + i, y + j, dark)
    for (i, j) in ((2, 4), (6, 4)): P.px(x + i, y + j, (255, 40, 40)); P.px(x + i, y + j, (255, 60, 50), glow=True)
    P.px(x + 4, y + 5, dark); P.px(x + 4, y + 6, dark)
    for i in range(2, 7): P.px(x + i, y + 7, (240, 236, 226) if i % 2 == 0 else (60, 20, 24))
    for i in range(2, 7): P.px(x + i, y + 8, (70, 22, 26))
    for i in range(w): P.px(x + i, y, (60, 60, 68)); P.px(x + i, y + 1, (96, 96, 104))
    for j in range(2, h): P.px(x, y + j, (60, 60, 68)); P.px(x + w - 1, y + j, (60, 60, 68))

def p_dk_jaw(P, c, R):
    for k, r in R.items(): P.noise_rect(r, (190, 182, 168), 6)
    x, y, w, h = R["front"]
    for i in range(w): P.px(x + i, y, (240, 236, 226) if i % 2 == 0 else (60, 20, 24))

def p_ribs(P, c, R):
    flesh, bone, gap = (110, 52, 58), (130, 66, 70), (18, 12, 14)
    for k, (x, y, w, h) in R.items():
        P.noise_rect((x, y, w, h), gap, 4)
        if k in ("front", "east", "west", "back"):
            for j in range(1, h, 2):
                for i in range(w):
                    curve = 1 if (i == 0 or i == w - 1) else 0
                    P.px(x + i, y + j - curve, shade(bone if P.r.random() > .2 else flesh, 1.0))
            if k in ("front", "back"):
                for j in range(h): P.px(x + w // 2, y + j, (150, 82, 84)); P.px(x + w // 2 - 1, y + j, (120, 60, 64))

def p_blade(P, c, R):
    for k, (x, y, w, h) in R.items():
        P.noise_rect((x, y, w, h), (196, 198, 206), 6)
        if k in ("west", "east"):
            for j in range(h): P.px(x + w // 2, y + j, (120, 122, 132))
        for j in range(h): P.px(x, y + j, (235, 238, 245))

def p_shield(P, c, R):
    import math
    for k, r in R.items(): p_metal((40, 40, 46))(P, c, {k: r})
    x, y, w, h = R["front"]
    P.noise_rect((x, y, w, h), (18, 18, 22), 4)
    for i in range(w):
        P.px(x + i, y, (90, 90, 98)); P.px(x + i, y + h - 1, (90, 90, 98))
    for j in range(h):
        P.px(x, y + j, (90, 90, 98)); P.px(x + w - 1, y + j, (90, 90, 98))
    cx, cy = x + w - 1, y + 8
    for i in range(w):
        for j in range(h):
            d = math.hypot(x + i - cx, y + j - cy)
            if abs(d - 9) < .6 or abs(d - 13) < .5: P.px(x + i, y + j, (120, 120, 128))
    for j in range(h // 2, h - 2, 1):
        i = 3 + (j % 4 if (j // 4) % 2 == 0 else 3 - j % 4)
        P.px(x + i, y + j, (110, 110, 118))

def p_horn(P, c, R):
    for k, (x, y, w, h) in R.items():
        P.noise_rect((x, y, w, h), (238, 232, 220), 5)
        for j in range(0, h, 2):
            for i in range(w): P.px(x + i, y + j, (206, 196, 180))

def p_feathers(long_tips):
    def f(P, c, R):
        base, edge, quill = (20, 16, 26), (48, 40, 62), (70, 60, 88)
        for k, (x, y, w, h) in R.items():
            P.noise_rect((x, y, w, h), base, 4)
            if k not in ("front", "back"): continue
            for i in range(w):
                fi = i if k == "front" else w - 1 - i          # same silhouette on both faces
                # feather length grows toward the outer edge (fi=0 is the outer side for the right wing)
                tip = (h - 1) - (fi % 2) - (0 if not long_tips else int(3 * (fi / max(1, w - 1))))
                for j in range(h):
                    if j > tip: P.clear(x + i, y + j)
                    elif fi % 2 == 0 and j > 2: P.px(x + i, y + j, quill if j % 4 == 0 else edge)
                for j in range(min(3, h)):                   # rounded covert row at the top
                    if (fi + j) % 3 == 0: P.px(x + i, y + j, edge)
                if fi in (w - 1,) and k == "front": P.clear(x + i, y)
    return f

def p_wing(P, c, R):
    for k, (x, y, w, h) in R.items():
        P.noise_rect((x, y, w, h), (22, 18, 28), 5)
        if k in ("front", "back"):
            for i in range(w):
                for j in range(h):
                    if (j + i // 2) % 3 == 0: P.px(x + i, y + j, (44, 36, 56))
            for i in range(w):                         # scalloped feather tips
                cut = (i % 3 == 1) * 2 + (i % 3 == 2) * 1
                for j in range(cut): P.clear(x + i, y + h - 1 - j)

PAINTERS = {
    "robe": p_robe((36, 26, 44)), "robe_dark": p_robe((26, 19, 32)), "robe_skirt": p_robe((32, 23, 40)),
    "hood": p_generic((30, 23, 36), 5, 0.75), "skull": p_skull, "jaw": p_jaw, "bone": p_bone,
    "orb_blue": p_orb((66, 76, 132), (190, 200, 255)), "orb_red": p_orb((235, 70, 100), (255, 190, 200), glow=True),
    "ribbon": p_ribbon, "lich_cape": p_tattered((24, 18, 30), 5, 0.02, 4),
    "metal": p_metal((76, 76, 84)), "metal_dark": p_metal((46, 46, 52)), "metal_light": p_metal((112, 112, 122)),
    "helmet": p_helmet, "dk_jaw": p_dk_jaw, "ribs": p_ribs, "dk_cape": p_tattered((16, 16, 20), 5, 0.05, 7),
    "rags": p_tattered((30, 30, 34), 6, 0.04, 4), "blade": p_blade, "gold": p_generic((214, 176, 64), 10, 0.7),
    "leather": p_generic((60, 42, 30), 8), "shield": p_shield, "halberd_shaft": p_generic((34, 28, 44), 6), "halberd_blade": p_metal((28, 26, 34)), "horn": p_horn, "wing": p_wing, "wing_inner": p_feathers(False), "wing_outer": p_feathers(True),
}

# ================================================================== animations
def L(*k): return list(k)
ALBEDO_STANCE_R, ALBEDO_STANCE_L = (-25, 0, -20), (-25, 0, 20)
ALBEDO_ANIMS = {
    "IDLE": {"length": 4.0, "loop": True, "ch": {
        "waist": {"rot": L((0, 0, 0, 0), (2, 1.5, 0, 0), (4, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (1.5, 0, 6, 0), (3, 0, -6, 0), (4, 0, 0, 0))},
        "right_arm": {"rot": L((0, *ALBEDO_STANCE_R), (2, -28, 0, -21), (4, *ALBEDO_STANCE_R))},
        "left_arm": {"rot": L((0, *ALBEDO_STANCE_L), (2, -28, 0, 21), (4, *ALBEDO_STANCE_L))},
        "right_wing": {"rot": L((0, 0, 0, 0), (2, 0, -6, 4), (4, 0, 0, 0))},
        "left_wing": {"rot": L((0, 0, 0, 0), (2, 0, 6, -4), (4, 0, 0, 0))},
        "right_wing_tip": {"rot": L((0, 0, 0, 0), (2, 0, -5, 0), (4, 0, 0, 0))},
        "left_wing_tip": {"rot": L((0, 0, 0, 0), (2, 0, 5, 0), (4, 0, 0, 0))}}},
    "WALK": {"length": 1.0, "loop": True, "ch": {
        "right_leg": {"rot": L((0, 30, 0, 0), (0.5, -30, 0, 0), (1, 30, 0, 0))},
        "left_leg": {"rot": L((0, -30, 0, 0), (0.5, 30, 0, 0), (1, -30, 0, 0))},
        "right_arm": {"rot": L((0, -10, 0, 0), (0.5, 10, 0, 0), (1, -10, 0, 0))},
        "left_arm": {"rot": L((0, 10, 0, 0), (0.5, -10, 0, 0), (1, 10, 0, 0))},
        "waist": {"pos": L((0, 0, 0, 0), (0.25, 0, 0.5, 0), (0.5, 0, 0, 0), (0.75, 0, 0.5, 0), (1, 0, 0, 0))}}},
    "GREET": {"length": 2.0, "ch": {
        "waist": {"rot": L((0, 0, 0, 0), (0.4, 28, 0, 0), (1.4, 28, 0, 0), (2, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (0.4, 12, 0, 0), (1.4, 12, 0, 0), (2, 0, 0, 0))},
        "right_arm": {"rot": L((0, *ALBEDO_STANCE_R), (0.4, -70, 0, -38), (1.4, -70, 0, -38), (2, *ALBEDO_STANCE_R))},
        "left_arm": {"rot": L((0, *ALBEDO_STANCE_L), (0.4, 8, 0, 12), (1.4, 8, 0, 12), (2, *ALBEDO_STANCE_L))},
        "right_wing": {"rot": L((0, 0, 0, 0), (0.5, 0, -32, 28), (1.4, 0, -32, 28), (2, 0, 0, 0))},
        "left_wing": {"rot": L((0, 0, 0, 0), (0.5, 0, 32, -28), (1.4, 0, 32, -28), (2, 0, 0, 0))},
        "right_wing_tip": {"rot": L((0, 0, 0, 0), (0.5, 0, -20, 10), (1.4, 0, -20, 10), (2, 0, 0, 0))},
        "left_wing_tip": {"rot": L((0, 0, 0, 0), (0.5, 0, 20, -10), (1.4, 0, 20, -10), (2, 0, 0, 0))}}},
}
ALBEDO_ANIMS["COMBAT"] = {"length": 2.0, "loop": True, "ch": {
    "right_arm": {"rot": L((0, -35, 0, 8), (1, -38, 0, 8), (2, -35, 0, 8))},
    "left_arm": {"rot": L((0, -45, 20, -8), (1, -48, 20, -8), (2, -45, 20, -8))},
    "waist": {"rot": L((0, 6, 0, 0), (1, 7, 0, 0), (2, 6, 0, 0))},
    "right_wing": {"rot": L((0, 0, -25, 22), (1, 0, -28, 25), (2, 0, -25, 22))},
    "left_wing": {"rot": L((0, 0, 25, -22), (1, 0, 28, -25), (2, 0, 25, -22))}}}
ALBEDO_ANIMS["ATTACK"] = {"length": 0.8, "ch": {
    "right_arm": {"rot": L((0, 0, 0, 0), (0.25, -115, 0, 10), (0.45, 35, 0, 0), (0.8, 0, 0, 0))},
    "waist": {"rot": L((0, 0, 0, 0), (0.25, -6, -20, 0), (0.45, 10, 15, 0), (0.8, 0, 0, 0))}}}
MAID_R, MAID_L = (-20, 0, -16), (-20, 0, 16)
MAID_ANIMS = {
    "IDLE": {"length": 4.0, "loop": True, "ch": {
        "waist": {"rot": L((0, 0, 0, 0), (2, 1.2, 0, 0), (4, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (1.5, 0, 5, 3), (3, 0, -5, -3), (4, 0, 0, 0))},
        "right_arm": {"rot": L((0, *MAID_R), (2, -22, 0, -17), (4, *MAID_R))},
        "left_arm": {"rot": L((0, *MAID_L), (2, -22, 0, 17), (4, *MAID_L))}}},
    "WALK": {"length": 1.0, "loop": True, "ch": {
        "right_leg": {"rot": L((0, 26, 0, 0), (0.5, -26, 0, 0), (1, 26, 0, 0))},
        "left_leg": {"rot": L((0, -26, 0, 0), (0.5, 26, 0, 0), (1, -26, 0, 0))},
        "waist": {"pos": L((0, 0, 0, 0), (0.25, 0, 0.4, 0), (0.5, 0, 0, 0), (0.75, 0, 0.4, 0), (1, 0, 0, 0))}}},
    "GREET": {"length": 2.0, "ch": {
        "waist": {"rot": L((0, 0, 0, 0), (0.4, 40, 0, 0), (1.4, 40, 0, 0), (2, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (0.4, 15, 0, 0), (1.4, 15, 0, 0), (2, 0, 0, 0))},
        "right_arm": {"rot": L((0, *MAID_R), (0.4, -25, 0, -24), (1.4, -25, 0, -24), (2, *MAID_R))},
        "left_arm": {"rot": L((0, *MAID_L), (0.4, -25, 0, 24), (1.4, -25, 0, 24), (2, *MAID_L))}}},
}
SEBAS_R, SEBAS_L = (22, 0, -12), (22, 0, 12)
SEBAS_ANIMS = {
    "IDLE": {"length": 4.0, "loop": True, "ch": {
        "waist": {"rot": L((0, 0, 0, 0), (2, 1.2, 0, 0), (4, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (1, 3, 4, 0), (3, 0, -4, 0), (4, 0, 0, 0))},
        "right_arm": {"rot": L((0, *SEBAS_R), (2, 23, 0, -13), (4, *SEBAS_R))},
        "left_arm": {"rot": L((0, *SEBAS_L), (2, 23, 0, 13), (4, *SEBAS_L))}}},
    "WALK": {"length": 1.0, "loop": True, "ch": {
        "right_leg": {"rot": L((0, 28, 0, 0), (0.5, -28, 0, 0), (1, 28, 0, 0))},
        "left_leg": {"rot": L((0, -28, 0, 0), (0.5, 28, 0, 0), (1, -28, 0, 0))},
        "right_arm": {"rot": L((0, -3, 0, 0), (0.5, 3, 0, 0), (1, -3, 0, 0))},
        "left_arm": {"rot": L((0, 3, 0, 0), (0.5, -3, 0, 0), (1, 3, 0, 0))},
        "waist": {"pos": L((0, 0, 0, 0), (0.25, 0, 0.4, 0), (0.5, 0, 0, 0), (0.75, 0, 0.4, 0), (1, 0, 0, 0))}}},
    "GREET": {"length": 2.0, "ch": {
        "waist": {"rot": L((0, 0, 0, 0), (0.5, 35, 0, 0), (1.5, 35, 0, 0), (2, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (0.5, 10, 0, 0), (1.5, 10, 0, 0), (2, 0, 0, 0))},
        "right_arm": {"rot": L((0, *SEBAS_R), (0.5, -80, 25, -38), (1.5, -80, 25, -38), (2, *SEBAS_R))},
        "left_arm": {"rot": L((0, *SEBAS_L), (0.5, 30, 0, 14), (1.5, 30, 0, 14), (2, *SEBAS_L))}}},
}
LICH_ANIMS = {
    "IDLE": {"length": 4.0, "loop": True, "ch": {
        "float": {"pos": L((0, 0, 0, 0), (2, 0, 1.5, 0), (4, 0, 0, 0))},
        "waist": {"rot": L((0, 0, 0, 0), (2, 2, 0, 0), (4, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (1, 0, 4, 3), (3, 0, -4, -3), (4, 0, 0, 0))},
        "jaw": {"rot": L((0, 0, 0, 0), (2, 6, 0, 0), (4, 0, 0, 0))},
        "cape": {"rot": L((0, 4, 0, 0), (2, 8, 0, 2), (4, 4, 0, 0))},
        "right_arm": {"rot": L((0, -10, 0, 8), (2, -14, 0, 10), (4, -10, 0, 8))},
        "left_arm": {"rot": L((0, -10, 0, -8), (2, -14, 0, -10), (4, -10, 0, -8))},
        "halo": {"lin": True, "rot": L((0, 0, 0, 0), (1, 0, 90, 0), (2, 0, 180, 0), (3, 0, 270, 0), (4, 0, 360, 0))},
        "orbs": {"lin": True, "rot": L((0, 0, 0, 0), (1, 0, -90, 0), (2, 0, -180, 0), (3, 0, -270, 0), (4, 0, -360, 0))}}},
    "WALK": {"length": 2.0, "loop": True, "ch": {
        "waist": {"rot": L((0, 8, 0, 0), (1, 10, 0, 0), (2, 8, 0, 0))},
        "skirt": {"rot": L((0, 4, 0, 0), (1, -4, 0, 0), (2, 4, 0, 0))},
        "cape": {"rot": L((0, 14, 0, 0), (1, 18, 0, 0), (2, 14, 0, 0))},
        "right_arm": {"rot": L((0, -6, 0, 0), (1, 6, 0, 0), (2, -6, 0, 0))},
        "left_arm": {"rot": L((0, 6, 0, 0), (1, -6, 0, 0), (2, 6, 0, 0))}}},
    "CAST": {"length": 1.2, "ch": {
        "right_arm": {"rot": L((0, 0, 0, 0), (0.3, -110, -10, -10), (0.8, -110, -10, -10), (1.2, 0, 0, 0))},
        "left_arm": {"rot": L((0, 0, 0, 0), (0.3, -110, 10, 10), (0.8, -110, 10, 10), (1.2, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (0.3, -15, 0, 0), (0.8, -15, 0, 0), (1.2, 0, 0, 0))},
        "jaw": {"rot": L((0, 0, 0, 0), (0.3, 30, 0, 0), (0.8, 30, 0, 0), (1.2, 0, 0, 0))},
        "waist": {"rot": L((0, 0, 0, 0), (0.3, -8, 0, 0), (0.8, -8, 0, 0), (1.2, 0, 0, 0))}}},
}
DK_ANIMS = {
    "IDLE": {"length": 3.0, "loop": True, "ch": {
        "waist": {"rot": L((0, 0, 0, 0), (1.5, 2, 0, 0), (3, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (1.5, -3, 0, 0), (3, 0, 0, 0))},
        "cape": {"rot": L((0, 3, 0, 0), (1.5, 6, 0, 1), (3, 3, 0, 0))},
        "right_arm": {"rot": L((0, -5, 0, 5), (1.5, -8, 0, 6), (3, -5, 0, 5))},
        "left_arm": {"rot": L((0, -25, -10, -5), (1.5, -27, -10, -5), (3, -25, -10, -5))}}},
    "WALK": {"length": 1.6, "loop": True, "ch": {
        "right_leg": {"rot": L((0, 22, 0, 0), (0.8, -22, 0, 0), (1.6, 22, 0, 0))},
        "left_leg": {"rot": L((0, -22, 0, 0), (0.8, 22, 0, 0), (1.6, -22, 0, 0))},
        "waist": {"pos": L((0, 0, 0, 0), (0.4, 0, -0.8, 0), (0.8, 0, 0, 0), (1.2, 0, -0.8, 0), (1.6, 0, 0, 0)),
                  "rot": L((0, 0, 0, 3), (0.8, 0, 0, -3), (1.6, 0, 0, 3))},
        "right_arm": {"rot": L((0, -15, 0, 0), (0.8, 15, 0, 0), (1.6, -15, 0, 0))},
        "cape": {"rot": L((0, 8, 0, 0), (0.8, 14, 0, 0), (1.6, 8, 0, 0))},
        "loin_front": {"rot": L((0, -10, 0, 0), (0.8, 10, 0, 0), (1.6, -10, 0, 0))}}},
    "ATTACK": {"length": 1.0, "ch": {
        "right_arm": {"rot": L((0, 0, 0, 0), (0.35, -165, 0, 10), (0.5, -170, 0, 10), (0.65, 15, 0, 0), (1, 0, 0, 0))},
        "waist": {"rot": L((0, 0, 0, 0), (0.35, -5, -20, 0), (0.65, 14, 15, 0), (1, 0, 0, 0))},
        "head": {"rot": L((0, 0, 0, 0), (0.35, -10, 0, 0), (0.65, 10, 0, 0), (1, 0, 0, 0))}}},
    "BLOCK": {"length": 0.8, "ch": {
        "left_arm": {"rot": L((0, 0, 0, 0), (0.15, -40, 35, 0), (0.6, -40, 35, 0), (0.8, 0, 0, 0))},
        "waist": {"rot": L((0, 0, 0, 0), (0.15, 0, 15, 0), (0.6, 0, 15, 0), (0.8, 0, 0, 0))}}},
    "ROAR": {"length": 2.0, "ch": {
        "head": {"rot": L((0, 0, 0, 0), (0.4, -35, 0, 0), (1.6, -35, 0, 0), (2, 0, 0, 0))},
        "jaw": {"rot": L((0, 0, 0, 0), (0.4, 25, 0, 0), (1.6, 25, 0, 0), (2, 0, 0, 0))},
        "right_arm": {"rot": L((0, 0, 0, 0), (0.4, -30, 0, 45), (1.6, -30, 0, 45), (2, 0, 0, 0))},
        "left_arm": {"rot": L((0, 0, 0, 0), (0.4, -30, 0, -45), (1.6, -30, 0, -45), (2, 0, 0, 0))},
        "waist": {"rot": L((0, 0, 0, 0), (0.4, -12, 0, 0), (1.6, -12, 0, 0), (2, 0, 0, 0))}}},
}
