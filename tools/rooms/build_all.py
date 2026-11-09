"""Generate all structure templates (.nbt) into the mod, plus anchors and a report."""
import json, os, sys, importlib
sys.path.insert(0, os.path.dirname(__file__))
from lib import check_support

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "out")
MOD_STRUCT = os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "nazarick", "structures")

def all_builds():
    builds = {}
    for mod in ("throne_room", "kings_quarters"):
        builds[mod] = importlib.import_module(mod).B
    builds.update(importlib.import_module("great_tomb").BUILDS)
    return builds

def main():
    os.makedirs(MOD_STRUCT, exist_ok=True); os.makedirs(OUT, exist_ok=True)
    report = {}
    for name, B in all_builds().items():
        probs = check_support(B)
        for p in probs[:10]: print("  SUPPORT:", name, p)
        npal, nblk = B.to_nbt(os.path.join(MOD_STRUCT, f"{name}.nbt"))
        json.dump({"size": [B.sx, B.sy, B.sz], "anchors": B.anchors,
                   "entities": [[x, y, z, e] for (x, y, z, e, _, _) in B.entities]},
                  open(os.path.join(OUT, f"{name}.anchors.json"), "w"), indent=1)
        report[name] = {"size": [B.sx, B.sy, B.sz], "blocks": nblk, "palette": npal, "support_problems": len(probs),
                        "entities": len(B.entities)}
        print(f"{name}: {B.sx}x{B.sy}x{B.sz}, {nblk} blocks, palette {npal}, entities {len(B.entities)}, support issues {len(probs)}")
    json.dump(report, open(os.path.join(OUT, "report.json"), "w"), indent=1)

if __name__ == "__main__":
    main()
