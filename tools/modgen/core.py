"""Model spec -> Java (LayerDefinition + AnimationDefinition), packed + painted textures, viewer JSON."""
import math, random
from PIL import Image

# ---------------------------------------------------------------- spec types
class Cube:
    def __init__(self, x, y, z, w, h, d, mat, inflate=0.0, uv=None, mirror=False):
        self.x, self.y, self.z, self.w, self.h, self.d = x, y, z, w, h, d
        self.mat, self.inflate, self.uv, self.mirror = mat, inflate, uv, mirror
    def footprint(self):
        return 2 * (self.w + self.d), self.h + self.d

class Part:
    def __init__(self, name, off=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
        self.name, self.off, self.rot = name, off, rot     # off relative to parent pivot, rot in degrees
        self.cubes, self.children = list(cubes), list(children)
    def walk(self):
        yield self
        for c in self.children: yield from c.walk()

def mirror_part(p, rename):
    """Mirror a part tree across x (right <-> left)."""
    q = Part(rename(p.name), (-p.off[0], p.off[1], p.off[2]), (p.rot[0], -p.rot[1], -p.rot[2]))
    q.cubes = [Cube(-c.x - c.w, c.y, c.z, c.w, c.h, c.d, c.mat, c.inflate, None, False) for c in p.cubes]
    q.children = [mirror_part(ch, rename) for ch in p.children]
    return q

class Model:
    def __init__(self, name, java_class, tex_w, tex_h, roots, pack_region=(0, 0)):
        self.name, self.java_class, self.tw, self.th = name, java_class, tex_w, tex_h
        self.roots, self.pack_region = roots, pack_region

# ---------------------------------------------------------------- UV packing
def pack(model):
    cubes = [c for r in model.roots for p in r.walk() for c in p.cubes if c.uv is None]
    cubes.sort(key=lambda c: (-(c.h + c.d), -2 * (c.w + c.d)))
    x0, y0 = model.pack_region
    x, y, row_h = x0, y0, 0
    for c in cubes:
        fw, fh = c.footprint()
        fw, fh = math.ceil(fw), math.ceil(fh)
        if x + fw > model.tw:
            x, y, row_h = x0, y + row_h + 1, 0
        if y + fh > model.th:
            raise RuntimeError(f"{model.name}: texture full")
        c.uv = (x, y); x += fw + 1; row_h = max(row_h, fh)

def faces(c):
    u, v = c.uv; w, h, d = int(c.w), int(c.h), int(c.d)
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "west": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "east": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

# ---------------------------------------------------------------- painting helpers
class Painter:
    def __init__(self, w, h, seed):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.r = random.Random(seed)
    def px(self, x, y, col, glow=False):
        if 0 <= x < self.img.width and 0 <= y < self.img.height:
            (self.glow if glow else self.img).putpixel((int(x), int(y)), tuple(col) + ((255,) if len(col) == 3 else ()))
    def noise_rect(self, rect, base, var=10, alpha=255):
        x0, y0, w, h = rect
        for i in range(w):
            for j in range(h):
                k = self.r.randint(-var, var)
                self.px(x0 + i, y0 + j, tuple(max(0, min(255, c + k)) for c in base) + (alpha,))
    def clear(self, x, y):
        self.img.putpixel((x, y), (0, 0, 0, 0))

def shade(c, f): return tuple(max(0, min(255, int(v * f))) for v in c)

# ---------------------------------------------------------------- Java emission
def jf(v):
    s = f"{float(v):.6g}"
    if "e" in s: s = f"{float(v):.6f}"
    return s + "F"

def emit_model_java(model, package, modid, anims_class, setup_body, imports_extra=""):
    lines = []
    def emit_part(p, parent_var):
        var = "p_" + p.name
        cl = "CubeListBuilder.create()"
        for c in p.cubes:
            cl += f".texOffs({c.uv[0]}, {c.uv[1]})"
            if c.mirror: cl += ".mirror()"
            cl += f".addBox({jf(c.x)}, {jf(c.y)}, {jf(c.z)}, {jf(c.w)}, {jf(c.h)}, {jf(c.d)}, new CubeDeformation({jf(c.inflate)}))"
            if c.mirror: cl += ".mirror(false)"
        rx, ry, rz = (math.radians(a) for a in p.rot)
        pose = (f"PartPose.offsetAndRotation({jf(p.off[0])}, {jf(p.off[1])}, {jf(p.off[2])}, {jf(rx)}, {jf(ry)}, {jf(rz)})"
                if any(p.rot) else f"PartPose.offset({jf(p.off[0])}, {jf(p.off[1])}, {jf(p.off[2])})")
        lines.append(f"        PartDefinition {var} = {parent_var}.addOrReplaceChild(\"{p.name}\", {cl}, {pose});")
        for ch in p.children: emit_part(ch, var)
    for r in model.roots: emit_part(r, "root")
    body = "\n".join(lines)
    return f"""package {package};

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
{imports_extra}
/** GENERATED by modgen from the model spec. Edit the spec, not this file. */
@SuppressWarnings("unused")
public class {model.java_class} extends HierarchicalModel<{setup_body['entity']}> {{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("{modid}", "{model.name}"), "main");
    private final ModelPart root;
{setup_body['fields']}
    public {model.java_class}(ModelPart root) {{
        this.root = root;
{setup_body['ctor']}
    }}

    public static LayerDefinition createBodyLayer() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {model.tw}, {model.th});
    }}

    @Override
    public ModelPart root() {{
        return this.root;
    }}

    @Override
    public void setupAnim({setup_body['entity']} entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {{
        this.root().getAllParts().forEach(ModelPart::resetPose);
{setup_body['setup']}
    }}
}}
"""

def emit_anims_java(package, cls, anims):
    out = []
    for name, a in anims.items():
        s = f"    public static final AnimationDefinition {name} = AnimationDefinition.Builder.withLength({jf(a['length'])})"
        if a.get("loop"): s += ".looping()"
        for part, chans in a["ch"].items():
            for kind in ("rot", "pos"):
                if kind not in chans: continue
                interp = "LINEAR" if chans.get("lin") else "CATMULLROM"
                target = "ROTATION" if kind == "rot" else "POSITION"
                fn = "degreeVec" if kind == "rot" else "posVec"
                kfs = ",\n                ".join(
                    f"new Keyframe({jf(t)}, KeyframeAnimations.{fn}({jf(x)}, {jf(y)}, {jf(z)}), AnimationChannel.Interpolations.{interp})"
                    for t, x, y, z in chans[kind])
                s += f"\n        .addAnimation(\"{part}\", new AnimationChannel(AnimationChannel.Targets.{target},\n                {kfs}))"
        s += "\n        .build();"
        out.append(s)
    body = "\n\n".join(out)
    return f"""package {package};

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/** GENERATED by modgen. Keyframe animations (degrees; positions in pixels, Y up). */
public final class {cls} {{
    private {cls}() {{}}

{body}
}}
"""

# ---------------------------------------------------------------- viewer JSON
def viewer_json(model, anims):
    def pj(p):
        return {"name": p.name, "off": p.off, "rot": p.rot,
                "cubes": [[c.x, c.y, c.z, c.w, c.h, c.d, c.uv[0], c.uv[1], c.inflate, c.mirror] for c in p.cubes],
                "children": [pj(c) for c in p.children]}
    return {"tw": model.tw, "th": model.th, "roots": [pj(r) for r in model.roots], "anims": anims}
