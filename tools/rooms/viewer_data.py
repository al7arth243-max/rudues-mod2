"""Convert built rooms into compact box lists for the three.js preview."""
import json, os, sys, importlib
sys.path.insert(0, os.path.dirname(__file__))

COL = {
 "polished_deepslate": "#4a4a50", "deepslate_tiles": "#3a3a40", "chiseled_deepslate": "#45454c",
 "deepslate_tile_stairs": "#3a3a40", "polished_blackstone_bricks": "#302b33", "polished_blackstone_stairs": "#302b33",
 "chiseled_polished_blackstone": "#35303a", "polished_blackstone_wall": "#35303a", "gilded_blackstone": "#5a4426",
 "gold_block": "#f2c94c", "polished_andesite": "#8a8c8e", "andesite": "#76797b",
 "red_carpet": "#8e2133", "yellow_carpet": "#e6b53a", "pink_carpet": "#d77c9c", "green_carpet": "#3f5c2a",
 "blue_stained_glass": "#3a4fd6", "light_blue_stained_glass": "#6fb4ff", "purple_stained_glass": "#8a45d8",
 "white_stained_glass": "#ffffff", "crying_obsidian": "#2a0f4a", "amethyst_cluster": "#c9a3ff",
 "lantern": "#ffd27a", "sea_lantern": "#cde8ff", "shroomlight": "#ff9a3c", "crimson_planks": "#6b2f4a",
 "crimson_stairs": "#6b2f4a", "end_rod": "#fff6e0", "chain": "#3d4148",
 "smooth_quartz": "#ece6df", "quartz_bricks": "#e3ddd5", "chiseled_quartz_block": "#d9d2c8",
 "quartz_pillar": "#e8e2da", "smooth_quartz_stairs": "#ece6df", "blue_terracotta": "#4a3b5c",
 "diorite_wall": "#d6d6d6", "bookshelf": "#7a5233", "white_candle": "#f5f0e6", "vine": "#3f7a26",
 "azalea_leaves": "#5d8a32", "purple_carpet": "#7b3fae", "pearlescent_froglight": "#f7e6f0",
 "blue_wool": "#3a43a8", "dark_oak_slab": "#4a3220", "stripped_dark_oak_wood": "#5b4128",
 "spruce_slab": "#7a5a38", "potted_flowering_azalea_bush": "#d98ab8", "dark_oak_planks": "#4a3220",
 "stripped_dark_oak_log": "#5b4128", "decorated_pot": "#a5583a", "dark_oak_stairs": "#4a3220",
 "spruce_planks": "#7a5a38", "stripped_spruce_log": "#8f6c45", "spruce_door": "#6e5033",
 "light_blue_terracotta": "#6f6e8c", "lime_terracotta": "#6f8a3a", "green_terracotta": "#4d5a2a",
 "brown_terracotta": "#5c3d2a", "white_terracotta": "#d2b2a1", "purple_concrete": "#64209c",
 "red_concrete": "#a02020", "blue_concrete": "#2c2e8f", "black_carpet": "#1d1c21",
 "purple_bed": "#7b3fae", "dark_oak_fence": "#4a3220", "purple_wool": "#7b3fae",
 "soul_campfire": "#5ad2e0", "skeleton_wall_skull": "#d8d8d0", "light_gray_carpet": "#a0a09a",
 "white_wool": "#f0f0f0", "black_wall_banner": "#1d1c21", "purple_wall_banner": "#7b3fae",
}
COL.update({
 "nazarick:throne": "#6b2f4a", "stone": "#7d7d7d", "dirt": "#7a5638", "grass_block": "#5d8a3a", "coarse_dirt": "#6e4f36",
 "podzol": "#5c4123", "rooted_dirt": "#866043", "stone_bricks": "#7a7a7a", "cracked_stone_bricks": "#727272",
 "gravel": "#857f7e", "mossy_stone_bricks": "#66745a", "chiseled_stone_bricks": "#787878", "stone_brick_wall": "#7a7a7a",
 "soul_lantern": "#7fe8f0", "wither_skeleton_skull": "#2a2a2a", "deepslate_brick_slab": "#4a4a50", "deepslate_bricks": "#4a4a50",
 "cobblestone_wall": "#7f7f7f", "mossy_cobblestone_wall": "#6a7a5f", "andesite_wall": "#888a8c", "candle": "#d8cfa8",
 "stone_brick_slab": "#7a7a7a", "dark_oak_log": "#3c2e1c", "cobweb": "#e8e8e8", "polished_andesite": "#8a8c8e",
 "smooth_stone": "#9e9e9e", "smooth_quartz": "#ece6df", "polished_blackstone_wall": "#35303a", "end_rod": "#fff6e0",
 "chain": "#3d4148", "blackstone": "#2c2830", "crying_obsidian": "#2a0f4a", "polished_blackstone": "#36313b",
 "dark_oak_fence": "#4a3220",
 "polished_basalt": "#4f4d55", "polished_blackstone": "#36313b", "polished_blackstone_brick_stairs": "#302b33",
 "polished_blackstone_brick_slab": "#302b33", "polished_blackstone_brick_wall": "#302b33",
 "polished_deepslate_stairs": "#4a4a50", "amethyst_block": "#8e6bc8", "red_stained_glass": "#b02a2a",
 "polished_andesite_stairs": "#8a8c8e", "polished_diorite": "#c9c9cb", "white_stained_glass": "#ffffff",
 "light_blue_stained_glass": "#6fb4ff", "smooth_quartz_slab": "#ece6df", "polished_blackstone_slab": "#36313b",
 "soul_campfire": "#5ad2e0", "spruce_door": "#6e5033",
})
EMISSIVE = {"soul_lantern", "lantern", "sea_lantern", "shroomlight", "end_rod", "white_candle", "pearlescent_froglight",
            "soul_campfire", "amethyst_cluster"}
TRANSLUCENT = {"red_stained_glass", "blue_stained_glass", "light_blue_stained_glass", "purple_stained_glass", "white_stained_glass"}
BANNER_COL = {"white": "#e9ecec", "orange": "#f07613", "magenta": "#bd44b3", "light_blue": "#3aafd9",
  "yellow": "#f8c527", "lime": "#70b919", "pink": "#ed8dac", "gray": "#3e4447", "light_gray": "#8e8e86",
  "cyan": "#158991", "purple": "#792aac", "blue": "#35399d", "brown": "#724728", "green": "#546d1b",
  "red": "#a12722", "black": "#141519"}

def is_full_opaque(n, p):
    if n in TRANSLUCENT or n in EMISSIVE and n not in ("sea_lantern", "shroomlight", "pearlescent_froglight"): return False
    bad = ("stairs", "slab", "carpet", "wall", "fence", "door", "lantern", "chain", "end_rod", "candle",
           "banner", "vine", "bed", "pot", "potted", "cluster", "campfire", "skull", "leaves")
    return not any(b in n for b in bad)

def boxes_for(n, p, x, y, z):
    """Return list of (x0,y0,z0,sx,sy,sz)."""
    f = p.get("facing")
    if n.endswith("carpet"): return [(x, y, z, 1, .07, 1)]
    if "slab" in n: return [(x, y + (.5 if p.get("type") == "top" else 0), z, 1, .5, 1)]
    if "stairs" in n:
        out = [(x, y + (.5 if p.get("half") == "top" else 0), z, 1, .5, 1)]
        yy = y if p.get("half") == "top" else y + .5
        if f == "north": out.append((x, yy, z, 1, .5, .5))
        elif f == "south": out.append((x, yy, z + .5, 1, .5, .5))
        elif f == "west": out.append((x, yy, z, .5, .5, 1))
        else: out.append((x + .5, yy, z, .5, .5, 1))
        return out
    if n in ("chain", "end_rod"):
        ax = p.get("axis", "y")
        if n == "end_rod": return [(x + .4, y, z + .4, .2, 1, .2)]
        if ax == "x": return [(x, y + .45, z + .45, 1, .1, .1)]
        if ax == "z": return [(x + .45, y + .45, z, .1, .1, 1)]
        return [(x + .45, y, z + .45, .1, 1, .1)]
    if "fence" in n or n.endswith("_wall"): return [(x + .3, y, z + .3, .4, 1, .4)]
    if n in ("lantern", "soul_lantern"): return [(x + .3, y + (.1 if p.get("hanging") == "true" else 0), z + .3, .4, .55, .4)]
    if "skull" in n: return [(x + .25, y, z + .25, .5, .5, .5)]
    if "candle" in n: return [(x + .35, y, z + .35, .3, .4, .3)]
    if "pot" in n: return [(x + .2, y, z + .2, .6, .8, .6)]
    if n == "amethyst_cluster": return [(x + .25, y, z + .25, .5, .6, .5)]
    if "bed" in n: return [(x, y, z, 1, .56, 1)]
    if n == "soul_campfire": return [(x, y, z, 1, .45, 1)]
    if "skull" in n: return [(x + .25, y + .25, z + .25, .5, .5, .5)]
    def plate(t, h=1.0, y0=0.0):
        return {"north": (x, y + y0, z + 1 - t, 1, h, t), "south": (x, y + y0, z, 1, h, t),
                "east": (x, y + y0, z, t, h, 1), "west": (x + 1 - t, y + y0, z, t, h, 1)}[f]
    if n.endswith("wall_banner"): return [plate(.08, 1.9, -0.95)]
    if "door" in n:
        # closed door: panel on the side opposite to facing
        opp = {"north": "south", "south": "north", "east": "west", "west": "east"}[f]
        f = opp; return [plate(.19)]
    if n == "vine":
        for side in ("west", "east", "north", "south"):
            if p.get(side) == "true":
                f = {"west": "east", "east": "west", "north": "south", "south": "north"}[side]
                return [plate(.06)]
    return [(x, y, z, 1, 1, 1)]

def export(B):
    full = {pos for pos, (nm, pr) in B.b.items() if is_full_opaque(nm.replace("minecraft:", ""), dict(pr))}
    out = {"o": [], "e": [], "t": []}
    colors, cidx = [], {}
    def ci(c):
        if c not in cidx: cidx[c] = len(colors); colors.append(c)
        return cidx[c]
    for (x, y, z), (nm, pr) in B.b.items():
        n = nm.replace("minecraft:", ""); p = dict(pr)
        if n == "cobweb": continue
        if (x, y, z) in full and all(((x + dx, y + dy, z + dz) in full) for dx, dy, dz in
                                     ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1))):
            continue
        col = COL.get(n)
        if n.endswith("wall_banner") and (x, y, z) in B.nbt:
            col = BANNER_COL[n.replace("_wall_banner", "")]
        if col is None: print("no color:", n); col = "#ff00ff"
        bucket = "t" if n in TRANSLUCENT else ("e" if n in EMISSIVE else "o")
        for bx in boxes_for(n, p, x, y, z):
            out[bucket].append([round(v, 2) for v in bx] + [ci(col)])
    return {"size": [B.sx, B.sy, B.sz], "colors": colors, "boxes": out, "anchors": B.anchors}

def composite(builds):
    """Assemble the whole Great Tomb using the same offsets as TombLayout.java."""
    from lib import Build
    LAYOUT = [("tomb_surface", -30, -6, -30), ("tomb_shaft", -4, -70, -4), ("tomb_antechamber", -10, -70, 5),
              ("tomb_corridor", 11, -70, 6), ("throne_room", -31, -70, 27), ("kings_quarters", 36, -70, 12)]
    mins = [min(dx for _, dx, _, _ in LAYOUT), min(dy for _, _, dy, _ in LAYOUT), min(dz for _, _, _, dz in LAYOUT)]
    maxs = [max(dx + builds[n].sx for n, dx, _, _ in LAYOUT), max(dy + builds[n].sy for n, _, dy, _ in LAYOUT),
            max(dz + builds[n].sz for n, _, _, dz in LAYOUT)]
    C = Build(maxs[0] - mins[0], maxs[1] - mins[1], maxs[2] - mins[2], "great_tomb")
    for n, dx, dy, dz in LAYOUT:
        b = builds[n]
        ox, oy, oz = dx - mins[0], dy - mins[1], dz - mins[2]
        # a piece overwrites earlier pieces inside its whole box (air included), like the real placement
        for x in range(b.sx):
            for y in range(b.sy):
                for z in range(b.sz):
                    C.b.pop((x + ox, y + oy, z + oz), None)
        for (x, y, z), st in b.b.items():
            C.b[(x + ox, y + oy, z + oz)] = st
            if (x, y, z) in b.nbt: C.nbt[(x + ox, y + oy, z + oz)] = b.nbt[(x, y, z)]
        for k, v in b.anchors.items():
            C.anchors.setdefault(k, []).extend([[p[0] + ox, p[1] + oy, p[2] + oz] for p in v])
    return C

if __name__ == "__main__":
    import build_all
    builds = build_all.all_builds()
    data = {}
    for key in ("great_tomb", "throne_room", "kings_quarters", "tomb_surface", "tomb_antechamber"):
        B = composite(builds) if key == "great_tomb" else builds[key]
        data[key] = export(B)
        print(key, B.sx, B.sy, B.sz, {k: len(v) for k, v in data[key]["boxes"].items()})
    with open(os.path.join(os.path.dirname(__file__), "..", "out", "viewer_data.json"), "w") as f:
        json.dump(data, f, separators=(",", ":"))
