"""Tiny structure-building toolkit for Minecraft 1.20.1 structure templates (.nbt).

Coordinates: x = east, y = up, z = south. Origin (0,0,0) is the template's min corner.
Block specs are strings like "oak_stairs[facing=south,half=bottom]" (namespace optional).
"""
import gzip, io, struct, json, math

DATA_VERSION_1_20_1 = 3465


# ---------------------------------------------------------------- NBT writer
class TagInt(int): pass
class TagByte(int): pass
class TagShort(int): pass
class TagFloat(float): pass
class TagDouble(float): pass
class TagList(list):
    def __init__(self, tagtype, items=()):
        super().__init__(items); self.tagtype = tagtype

_IDS = {"end": 0, "byte": 1, "short": 2, "int": 3, "float": 5, "double": 6, "string": 8, "list": 9, "compound": 10}

def _tag_id(v):
    if isinstance(v, TagByte): return 1
    if isinstance(v, TagShort): return 2
    if isinstance(v, TagFloat): return 5
    if isinstance(v, TagDouble): return 6
    if isinstance(v, (TagInt, int)) and not isinstance(v, bool): return 3
    if isinstance(v, str): return 8
    if isinstance(v, TagList): return 9
    if isinstance(v, dict): return 10
    raise TypeError(type(v))

def _w_str(buf, s):
    b = s.encode("utf-8"); buf.write(struct.pack(">H", len(b))); buf.write(b)

def _w_payload(buf, v):
    t = _tag_id(v)
    if t == 1: buf.write(struct.pack(">b", v))
    elif t == 2: buf.write(struct.pack(">h", v))
    elif t == 3: buf.write(struct.pack(">i", v))
    elif t == 5: buf.write(struct.pack(">f", v))
    elif t == 6: buf.write(struct.pack(">d", v))
    elif t == 8: _w_str(buf, v)
    elif t == 9:
        et = _IDS[v.tagtype] if len(v) else 0
        buf.write(struct.pack(">bi", et, len(v)))
        for it in v: _w_payload(buf, it)
    elif t == 10:
        for k, x in v.items():
            buf.write(struct.pack(">b", _tag_id(x))); _w_str(buf, k); _w_payload(buf, x)
        buf.write(b"\x00")

def write_nbt_gz(path, root):
    buf = io.BytesIO()
    buf.write(b"\x0a"); _w_str(buf, ""); _w_payload(buf, root)
    with gzip.open(path, "wb") as f: f.write(buf.getvalue())


# ---------------------------------------------------------------- block specs
def parse(spec):
    spec = spec.strip()
    props = {}
    if "[" in spec:
        name, rest = spec.split("[", 1)
        for kv in rest.rstrip("]").split(","):
            if kv.strip():
                k, v = kv.split("="); props[k.strip()] = v.strip()
    else:
        name = spec
    if ":" not in name: name = "minecraft:" + name
    return name, tuple(sorted(props.items()))

def fmt(name, props):
    n = name.replace("minecraft:", "")
    return n + ("[" + ",".join(f"{k}={v}" for k, v in props) + "]" if props else "")

_MIRROR_X = {"east": "west", "west": "east"}
_MIRROR_Z = {"north": "south", "south": "north"}

def mirror_props_x(props):
    out = {}
    d = dict(props)
    for k, v in d.items():
        if k in ("facing", "axis_dir") and v in _MIRROR_X: v = _MIRROR_X[v]
        elif k == "hinge": v = {"left": "right", "right": "left"}[v]
        elif k == "shape": v = v.replace("left", "@").replace("right", "left").replace("@", "right")
        out[k] = v
    # swap east/west connection booleans (vines, fences, walls)
    if "east" in d or "west" in d:
        e, w = d.get("east"), d.get("west")
        if w is not None: out["east"] = w
        else: out.pop("east", None)
        if e is not None: out["west"] = e
        else: out.pop("west", None)
    if d.get("rotation") is not None:  # standing banners/signs: 16-step rotation
        out["rotation"] = str((16 - int(d["rotation"])) % 16)
    return tuple(sorted(out.items()))


# ---------------------------------------------------------------- builder
class Build:
    def __init__(self, sx, sy, sz, name):
        self.sx, self.sy, self.sz, self.name = sx, sy, sz, name
        self.b = {}       # (x,y,z) -> (name, props)
        self.nbt = {}     # (x,y,z) -> compound
        self.anchors = {} # name -> list of [x,y,z]
        self.entities = []  # (x, y, z, entity_id, yaw, extra_nbt)

    def inb(self, x, y, z):
        return 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz

    def set(self, x, y, z, spec, nbt=None):
        if not self.inb(x, y, z): return
        st = parse(spec) if isinstance(spec, str) else spec
        if st[0] == "minecraft:air":
            self.b.pop((x, y, z), None); self.nbt.pop((x, y, z), None); return
        self.b[(x, y, z)] = st
        if nbt is not None: self.nbt[(x, y, z)] = nbt
        else: self.nbt.pop((x, y, z), None)

    def get(self, x, y, z):
        st = self.b.get((x, y, z))
        return st[0].replace("minecraft:", "") if st else "air"

    def fill(self, x1, y1, z1, x2, y2, z2, spec):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, spec)

    def clear(self, x1, y1, z1, x2, y2, z2):
        self.fill(x1, y1, z1, x2, y2, z2, "air")

    # mirrored placement across the x axis center (x -> sx-1-x)
    def mset(self, x, y, z, spec, nbt=None):
        st = parse(spec)
        self.set(x, y, z, st, nbt)
        mx = self.sx - 1 - x
        if mx != x:
            self.set(mx, y, z, (st[0], mirror_props_x(st[1])), nbt)

    def mfill(self, x1, y1, z1, x2, y2, z2, spec):
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.mset(x, y, z, spec)

    def entity(self, x, y, z, eid, yaw=180.0, extra=None):
        """Entity stored in the template (spawned when the structure is placed). Facing: 180 = north."""
        self.entities.append((x, y, z, eid, yaw, extra or {}))

    def anchor(self, key, x, y, z):
        self.anchors.setdefault(key, []).append([x, y, z])

    # ---------------------------------------------------------- export
    def to_nbt(self, path, include_air=True):
        palette, index = [], {}
        def pid(st):
            if st not in index:
                index[st] = len(palette)
                c = {"Name": st[0]}
                if st[1]: c["Properties"] = {k: v for k, v in st[1]}
                palette.append(c)
            return index[st]
        air = ("minecraft:air", ())
        blocks = TagList("compound")
        for y in range(self.sy):
            for z in range(self.sz):
                for x in range(self.sx):
                    st = self.b.get((x, y, z))
                    if st is None:
                        if not include_air: continue
                        st = air
                    e = {"state": TagInt(pid(st)),
                         "pos": TagList("int", [TagInt(x), TagInt(y), TagInt(z)])}
                    if (x, y, z) in self.nbt: e["nbt"] = self.nbt[(x, y, z)]
                    blocks.append(e)
        palette_tag = TagList("compound", palette)
        ents = TagList("compound")
        for (x, y, z, eid, yaw, extra) in self.entities:
            nbt = {"id": eid, "PersistenceRequired": TagByte(1),
                   "Rotation": TagList("float", [TagFloat(yaw), TagFloat(0.0)])}
            nbt.update(extra)
            ents.append({"pos": TagList("double", [TagDouble(x + 0.5), TagDouble(y), TagDouble(z + 0.5)]),
                         "blockPos": TagList("int", [TagInt(x), TagInt(y), TagInt(z)]),
                         "nbt": nbt})
        root = {
            "size": TagList("int", [TagInt(self.sx), TagInt(self.sy), TagInt(self.sz)]),
            "entities": ents,
            "blocks": blocks,
            "palette": palette_tag,
            "DataVersion": TagInt(DATA_VERSION_1_20_1),
        }
        write_nbt_gz(path, root)
        return len(palette), len(self.b)

    def counts(self):
        c = {}
        for st in self.b.values():
            n = st[0].replace("minecraft:", ""); c[n] = c.get(n, 0) + 1
        return dict(sorted(c.items(), key=lambda kv: -kv[1]))


# ---------------------------------------------------------------- support checks
# Blocks that pop off (become air) when the structure is placed if unsupported.
FULL_EXCEPT = ("stairs", "slab", "carpet", "wall", "fence", "pane", "door", "trapdoor",
               "lantern", "chain", "end_rod", "candle", "banner", "vine", "bed", "pot",
               "flower_pot", "potted", "cluster", "bud", "campfire", "lectern", "button",
               "leaves", "glass", "air", "rod")

def is_sturdy_top(name):
    """Rough check: block offers a full top face."""
    if name == "air": return False
    if any(t in name for t in ("carpet", "wall", "fence", "pane", "door", "lantern", "chain",
                               "end_rod", "candle", "banner", "vine", "pot", "potted",
                               "cluster", "bud", "campfire", "button", "rod", "bed")):
        return False
    if "slab" in name: return True  # caller handles bottom slabs separately
    return True

def check_support(B):
    problems = []
    for (x, y, z), (name, props) in B.b.items():
        n = name.replace("minecraft:", ""); p = dict(props)
        below = B.get(x, y - 1, z); above = B.get(x, y + 1, z)
        if n.endswith("carpet") or n.endswith("candle") or (n == "lantern" or n == "soul_lantern") and p.get("hanging") == "false" \
                or n.endswith("_door") and p.get("half") == "lower" or n in ("campfire", "soul_campfire"):
            if below == "air" or not is_sturdy_top(below) and not below.endswith("_door"):
                problems.append((n, x, y, z, "needs block below, has " + below))
        if n in ("lantern", "soul_lantern") and p.get("hanging") == "true":
            if above == "air" or ("slab" in above and B.b[(x, y + 1, z)][1] and dict(B.b[(x, y + 1, z)][1]).get("type") == "top"):
                problems.append((n, x, y, z, "hanging lantern needs full block/chain above, has " + above))
        if n.endswith("wall_banner"):
            d = {"north": (0, 0, 1), "south": (0, 0, -1), "east": (-1, 0, 0), "west": (1, 0, 0)}[p["facing"]]
            if B.get(x + d[0], y, z + d[2]) == "air":
                problems.append((n, x, y, z, "wall banner has no wall behind"))
        if n == "vine":
            ok = False
            for side, d in (("north", (0, 0, -1)), ("south", (0, 0, 1)), ("east", (1, 0, 0)), ("west", (-1, 0, 0)), ("up", (0, 1, 0))):
                if p.get(side) == "true":
                    if B.get(x + d[0], y + d[1], z + d[2]) not in ("air", "vine"): ok = True
                    elif d[1] == 0:
                        # chain of vines from above with same face
                        yy = y + 1
                        while B.get(x, yy, z) == "vine" and dict(B.b[(x, yy, z)][1]).get(side) == "true":
                            if B.get(x + d[0], yy, z + d[2]) not in ("air", "vine"): ok = True; break
                            yy += 1
            if not ok: problems.append((n, x, y, z, "vine unsupported"))
        if n in ("amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud"):
            f = p.get("facing", "up")
            d = {"up": (0, -1, 0), "down": (0, 1, 0), "north": (0, 0, 1), "south": (0, 0, -1), "east": (-1, 0, 0), "west": (1, 0, 0)}[f]
            if B.get(x + d[0], y + d[1], z + d[2]) == "air":
                problems.append((n, x, y, z, "cluster unsupported"))
    return problems


# ---------------------------------------------------------------- shapes
def pointed(u0, u1, y0, y_full):
    """Cells (u, y) of a pointed (gothic) opening: full width u0..u1 for rows y0..y_full,
    then narrowing one block per side per row up to the point."""
    cells = [(u, y) for u in range(u0, u1 + 1) for y in range(y0, y_full + 1)]
    a, b, y = u0 + 1, u1 - 1, y_full + 1
    while a <= b:
        cells += [(u, y) for u in range(a, b + 1)]
        a, b, y = a + 1, b - 1, y + 1
    return cells

def ring_around(cells, diag=True):
    """Cells adjacent (4- or 8-neighbourhood) to a set of (u, y) cells, not in it."""
    s = set(cells); out = set()
    nb = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (1, -1), (-1, 1), (-1, -1)] if diag else [])
    for u, y in s:
        for du, dy in nb:
            if (u + du, y + dy) not in s: out.add((u + du, y + dy))
    return out


# ---------------------------------------------------------------- banners (1.20.1 format)
PATTERNS = ["bri", "cre", "sku", "flo", "moj", "glb", "pig", "mc", "bo", "cbo", "tts", "bts",
            "ss", "cs", "ms", "rd", "ld", "dls", "drs", "lud", "rud", "tl", "tr", "bl", "br",
            "ts", "bs", "ls", "rs", "mr", "gra", "gru", "hh", "hhb", "vh", "vhr"]
COLOR_IDS = {c: i for i, c in enumerate(["white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"])}

def banner_nbt(layers):
    """layers: list of (pattern_code, color_name)"""
    return {"id": "minecraft:banner",
            "Patterns": TagList("compound", [{"Pattern": p, "Color": TagInt(COLOR_IDS[c])} for p, c in layers])}
