"""Generate Java models/animations + textures into the mod project, and viewer data."""
import json, os, sys, base64, io, zlib
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image
from core import pack, faces, Painter, emit_model_java, emit_anims_java, viewer_json
import models as M

MOD = os.path.join(os.path.dirname(__file__), "..", "..")
JAVA = f"{MOD}/src/main/java/com/nazarick"
TEX = f"{MOD}/src/main/resources/assets/nazarick/textures/entity"
UP = os.path.join(os.path.dirname(__file__), "skins")
os.makedirs(f"{JAVA}/client/model", exist_ok=True); os.makedirs(f"{JAVA}/client/animation", exist_ok=True)
os.makedirs(TEX, exist_ok=True)

LOOK = """        this.head.yRot += netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot += headPitch * ((float) Math.PI / 180F);"""
def npc_setup(ent, anim):
    return {"entity": ent,
            "fields": "    private final ModelPart head;\n",
            "ctor": '        this.head = root.getChild("waist").getChild("head");',
            "setup": LOOK + f"""
        this.animateWalk({anim}.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        if (entity.greetAnimationState.isStarted()) {{
            this.animate(entity.greetAnimationState, {anim}.GREET, ageInTicks);
        }} else {{
            this.animate(entity.idleAnimationState, {anim}.IDLE, ageInTicks);
        }}"""}

SETUPS = {
 "albedo": (M.ALBEDO, M.ALBEDO_ANIMS, "AlbedoAnimations", {"entity": "AlbedoEntity",
     "fields": "    private final ModelPart head;\n    private final ModelPart halberd;\n",
     "ctor": '        this.head = root.getChild("waist").getChild("head");\n        this.halberd = root.getChild("waist").getChild("right_arm").getChild("halberd");',
     "setup": LOOK + """
        boolean fighting = entity.isAggressive();
        this.halberd.visible = fighting;
        this.animateWalk(AlbedoAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        if (fighting) {
            this.animate(entity.idleAnimationState, AlbedoAnimations.COMBAT, ageInTicks);
        } else if (entity.greetAnimationState.isStarted()) {
            this.animate(entity.greetAnimationState, AlbedoAnimations.GREET, ageInTicks);
        } else {
            this.animate(entity.idleAnimationState, AlbedoAnimations.IDLE, ageInTicks);
        }
        this.animate(entity.attackAnimationState, AlbedoAnimations.ATTACK, ageInTicks);"""}, "AlbedoEntity"),
 "sebas": (M.SEBAS, M.SEBAS_ANIMS, "SebasAnimations", npc_setup("SebasEntity", "SebasAnimations"), "SebasEntity"),
 "elder_lich": (M.LICH, M.LICH_ANIMS, "ElderLichAnimations", {"entity": "ElderLichEntity",
     "fields": "    private final ModelPart head;\n",
     "ctor": '        this.head = root.getChild("float").getChild("waist").getChild("head");',
     "setup": LOOK + """
        this.animateWalk(ElderLichAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        this.animate(entity.idleAnimationState, ElderLichAnimations.IDLE, ageInTicks);
        this.animate(entity.castAnimationState, ElderLichAnimations.CAST, ageInTicks);"""}, "ElderLichEntity"),
 "death_knight": (M.DK, M.DK_ANIMS, "DeathKnightAnimations", {"entity": "DeathKnightEntity",
     "fields": "    private final ModelPart head;\n",
     "ctor": '        this.head = root.getChild("waist").getChild("torso").getChild("head");',
     "setup": LOOK + """
        this.animateWalk(DeathKnightAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
        this.animate(entity.idleAnimationState, DeathKnightAnimations.IDLE, ageInTicks);
        this.animate(entity.attackAnimationState, DeathKnightAnimations.ATTACK, ageInTicks);
        this.animate(entity.blockAnimationState, DeathKnightAnimations.BLOCK, ageInTicks);
        this.animate(entity.roarAnimationState, DeathKnightAnimations.ROAR, ageInTicks);"""}, "DeathKnightEntity"),
}

def data_uri(img):
    b = io.BytesIO(); img.save(b, "PNG"); return "data:image/png;base64," + base64.b64encode(b.getvalue()).decode()

viewer = {}
for key, (model, anims, acls, setup, ent) in SETUPS.items():
    pack(model)
    P = Painter(model.tw, model.th, seed=zlib.crc32(key.encode()))
    for r in model.roots:
        for p in r.walk():
            for c in p.cubes:
                if c.mat == "skin": continue
                M.PAINTERS[c.mat](P, c, faces(c))
    tex = P.img
    if key == "albedo":
        skin = Image.open(f"{UP}/albedo_skin.png").convert("RGBA")
        tex.paste(skin, (0, 0))
    if key == "sebas":
        tex = Image.open(f"{UP}/sebas_skin.png").convert("RGBA")
    tex.save(f"{TEX}/{key}.png")
    glow = None
    if P.glow.getbbox():
        glow = P.glow; glow.save(f"{TEX}/{key}_glow.png")
    imports = f"import com.nazarick.client.animation.{acls};\nimport com.nazarick.entity.{ent};\n"
    open(f"{JAVA}/client/model/{model.java_class}.java", "w").write(
        emit_model_java(model, "com.nazarick.client.model", "nazarick", acls, setup, imports))
    open(f"{JAVA}/client/animation/{acls}.java", "w").write(emit_anims_java("com.nazarick.client.animation", acls, anims))
    vj = viewer_json(model, anims); vj["tex"] = data_uri(tex); vj["glow"] = data_uri(glow) if glow else None
    viewer[key] = vj
    print(key, model.tw, model.th, "glow" if glow else "")
import items as _items
_items.make(os.path.join(MOD, "src/main/resources/assets/nazarick/textures/item"))
json.dump(viewer, open(os.path.join(os.path.dirname(__file__), "viewer_models.json"), "w"))
