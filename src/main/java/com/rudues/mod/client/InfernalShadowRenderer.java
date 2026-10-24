package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.HammerMoveData;
import com.rudues.mod.entity.InfernalShadowEntity;
import com.rudues.mod.entity.VortexShape;
import com.rudues.mod.entity.CrescentMoveData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Infernal Shadow: the copied player's own skin (darkened) on a player model with bendable elbows and knees,
 * armour drawn segment by segment so it bends with the limbs, the held sword as the real item at the sword_item bone,
 * and the Hammer and Anvil telegraph circle on the floor.
 */
public class InfernalShadowRenderer extends GeoEntityRenderer<InfernalShadowEntity> {
    private static final ResourceLocation FALLBACK = new ResourceLocation(RuduesMod.MODID, "textures/entity/infernal_shadow.png");
    private static final ResourceLocation RING = new ResourceLocation(RuduesMod.MODID, "textures/effect/hammer_ring.png");
    private static final ResourceLocation FILL = new ResourceLocation(RuduesMod.MODID, "textures/effect/hammer_fill.png");
    static final ResourceLocation FLAMES = new ResourceLocation(RuduesMod.MODID, "textures/effect/inferno_flames.png");
    /** phase 2: the obsidian growths, and their molten veins (drawn again additively, full bright) */
    private static final ResourceLocation CORRUPTION = new ResourceLocation(RuduesMod.MODID, "textures/entity/infernal_shadow_corruption.png");
    private static final ResourceLocation CORRUPTION_GLOW = new ResourceLocation(RuduesMod.MODID, "textures/entity/infernal_shadow_corruption_glow.png");
    /** the netherite the growths replace in phase 2 (the chestplate keeps its body and right arm, the leggings their waist) */
    private static final Set<String> STRIPPED = Set.of("armor_left_arm", "armor_left_forearm", "armor_right_leg", "armor_left_leg",
            "armor_right_shin", "armor_left_shin", "armor_right_boot", "armor_left_boot");
    /** the armour bones of each slot (their cubes carry UVs into the vanilla 64x32 armour textures) */
    private static final Map<EquipmentSlot, Set<String>> ARMOR_BONES = Map.of(
            EquipmentSlot.HEAD, Set.of("armor_head"),
            EquipmentSlot.CHEST, Set.of("armor_body", "armor_right_arm", "armor_left_arm", "armor_right_forearm", "armor_left_forearm"),
            EquipmentSlot.LEGS, Set.of("armor_waist", "armor_right_leg", "armor_left_leg", "armor_right_shin", "armor_left_shin"),
            EquipmentSlot.FEET, Set.of("armor_right_boot", "armor_left_boot"));
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    /**
     * Where the hand holds a vanilla sword sprite, in item units from its centre (the sprite runs from the pommel at the
     * lower left to the tip at the upper right).
     */
    private static final float GRIP = 0.30F;

    /** which armour slot is being drawn by the current re-render (null = the skin pass) */
    @Nullable
    private EquipmentSlot armorPass;
    /** the current re-render draws the corruption growths instead */
    private boolean corruptionPass;

    public InfernalShadowRenderer(EntityRendererProvider.Context context) {
        super(context, new ShadowModel());
        this.shadowRadius = 0.5F;
        addRenderLayer(new SwordLayer(this));
        addRenderLayer(new ArmorPassLayer(this));
        addRenderLayer(new CorruptionLayer(this));
    }

    @Nullable
    static PlayerInfo copiedPlayer(InfernalShadowEntity entity) {
        UUID id = entity.getCopyOf();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        return id == null || conn == null ? null : conn.getPlayerInfo(id);
    }

    @Override
    public void render(InfernalShadowEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // grabbed by a player's Pummel: drawn tipped over in their hands
        boolean held = !entity.isPuppet() && PummelClient.apply(entity, partialTick, poseStack);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (held) {
            poseStack.popPose();
        }
        if (entity.getMove() == InfernalShadowEntity.MOVE_HAMMER) {
            renderCircle(entity, partialTick, poseStack, bufferSource);
        } else if (entity.getMove() == InfernalShadowEntity.MOVE_VORTEX) {
            renderVortex(entity, partialTick, poseStack, bufferSource);
        } else if (entity.getMove() == InfernalShadowEntity.MOVE_CRESCENT) {
            renderCrescent(entity, partialTick, poseStack, bufferSource);
        }
    }

    /** bone visibility for the current pass: the skin (with the right arm variant) or one armour slot */
    @Override
    public void preRender(PoseStack poseStack, InfernalShadowEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        PlayerInfo info = copiedPlayer(animatable);
        boolean slim = info != null && "slim".equals(info.getModelName());
        EquipmentSlot pass = isReRender ? this.armorPass : null;
        boolean corrupt = isReRender && this.corruptionPass;
        boolean stripped = animatable.armourStripped();
        for (GeoBone bone : model.topLevelBones()) {
            applyVisibility(bone, pass, corrupt, stripped, slim);
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    private static void applyVisibility(GeoBone bone, @Nullable EquipmentSlot pass, boolean corrupt, boolean stripped, boolean slim) {
        String name = bone.getName();
        if (name.startsWith("corrupt_")) {
            bone.setHidden(!corrupt);
        } else if (name.startsWith("armor_")) {
            bone.setHidden(corrupt || pass == null || !ARMOR_BONES.get(pass).contains(name) || (stripped && STRIPPED.contains(name)));
        } else if (pass != null || corrupt) {
            bone.setHidden(true);              // no skin in an armour pass, but keep walking down the limbs
            bone.setChildrenHidden(false);
        } else if (name.endsWith("_wide")) {
            bone.setHidden(slim);
        } else if (name.endsWith("_slim")) {
            bone.setHidden(!slim);
        } else {
            bone.setHidden(false);
        }
        for (GeoBone child : bone.getChildBones()) {
            applyVisibility(child, pass, corrupt, stripped, slim);
        }
    }

    /** a shadow of the player: their skin, drained of light */
    @Override
    public Color getRenderColor(InfernalShadowEntity animatable, float partialTick, int packedLight) {
        if (copiedPlayer(animatable) == null || animatable.isPuppet()) {
            return Color.WHITE;      // a player's stand-in wears their skin as it is
        }
        return animatable.isPhaseTwo() ? Color.ofRGBA(0.50F, 0.40F, 0.62F, 1.0F) : Color.ofRGBA(0.62F, 0.55F, 0.70F, 1.0F);
    }

    // ------------------------------------------------------------ Hammer and Anvil: the red circle
    private void renderCircle(InfernalShadowEntity e, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        float t = e.moveTime(partialTick);
        float tp = HammerMoveData.TELEPORT_TICK, impact = HammerMoveData.IMPACT_TICK;
        if (t < tp || t > impact + 4) {
            return;
        }
        float progress = Mth.clamp((t - tp) / (impact - tp), 0.0F, 1.0F);
        boolean locked = t >= HammerMoveData.LOCK_TICK;
        float fade = Mth.clamp((t - tp) / 3.0F, 0.0F, 1.0F) * (t > impact ? 1.0F - (t - impact) / 4.0F : 1.0F);
        float pulse = 0.5F + 0.5F * Mth.sin(t * (locked ? 1.9F : 0.8F));
        float yaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        // circle centre relative to the feet: where the fist lands
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        float cx = fx * HammerMoveData.FIST_F + rx * HammerMoveData.FIST_R;
        float cz = fz * HammerMoveData.FIST_F + rz * HammerMoveData.FIST_R;
        float r = HammerMoveData.RADIUS;
        float g = locked ? 0.22F : 0.08F;
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer fill = buffers.getBuffer(RenderType.entityTranslucentEmissive(FILL));
        disc(fill, pose, cx, cz, r * progress, 0.03F, 1.0F, g, 0.06F, (0.28F + (locked ? 0.14F * pulse : 0.0F)) * fade);
        VertexConsumer ring = buffers.getBuffer(RenderType.entityTranslucentEmissive(RING));
        disc(ring, pose, cx, cz, r, 0.04F, 1.0F, g, 0.06F, (0.7F + 0.3F * pulse) * fade);
        if (progress > 0.05F && progress < 1.0F) {      // the filling edge
            disc(ring, pose, cx, cz, r * progress, 0.045F, 1.0F, 0.35F, 0.2F, 0.6F * fade);
        }
    }

    // ------------------------------------------------------------ Infernal Crescent: the warning on the ground
    /** a crescent of ground in front of it that fills while it charges, then flashes as the slash lands */
    private void renderCrescent(InfernalShadowEntity e, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        float t = e.moveTime(partialTick);
        float charge = CrescentMoveData.CHARGE_TICK, release = CrescentMoveData.RELEASE_TICK, hit = CrescentMoveData.HIT_TICK;
        if (t < charge || t > hit + 6) {
            return;
        }
        float progress = Mth.clamp((t - charge) / (release - charge), 0.0F, 1.0F);
        boolean locked = t >= release - 3;
        float fade = Mth.clamp((t - charge) / 3.0F, 0.0F, 1.0F) * (t > hit ? 1.0F - (t - hit) / 6.0F : 1.0F);
        float pulse = 0.5F + 0.5F * Mth.sin(t * (locked ? 1.9F : 0.9F));
        float flash = t >= hit ? 1.0F - (t - hit) / 6.0F : 0.0F;
        float yaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        float facing = (float) Math.atan2(Mth.cos(yaw), -Mth.sin(yaw));       // angle of the forward vector in x/z
        float half = CrescentMoveData.HALF_ANGLE * Mth.DEG_TO_RAD, reach = CrescentMoveData.REACH, inner = 0.8F;
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FILL));
        float g = locked ? 0.3F : 0.15F;
        float fillA = ((0.22F + (locked ? 0.15F * pulse : 0.0F)) + 0.5F * flash) * fade;
        float edgeA = (0.65F + 0.3F * pulse + 0.3F * flash) * fade;
        float rFill = inner + (reach - inner) * progress;
        int segs = 24;
        for (int k = 0; k < segs; k++) {
            float a0 = facing - half + 2 * half * k / segs, a1 = facing - half + 2 * half * (k + 1) / segs;
            sector(vc, pose, a0, a1, inner, rFill, 0.03F, 1.0F, g, 0.05F, fillA);
            sector(vc, pose, a0, a1, reach - 0.14F, reach, 0.04F, 1.0F, g + 0.1F, 0.1F, edgeA);       // outer edge
            if (progress < 1.0F) {
                sector(vc, pose, a0, a1, rFill - 0.1F, rFill, 0.045F, 1.0F, 0.45F, 0.2F, 0.6F * fade);  // the filling front
            }
        }
        for (float a : new float[]{facing - half, facing + half}) {     // the two straight edges
            float da = 0.12F / reach;
            sector(vc, pose, a - da, a + da, inner, reach, 0.04F, 1.0F, g + 0.1F, 0.1F, edgeA);
        }
    }

    private static void sector(VertexConsumer vc, PoseStack.Pose pose, float a0, float a1, float r0, float r1, float y,
                               float red, float green, float blue, float alpha) {
        if (r1 <= r0 || alpha <= 0.0F) {
            return;
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vertex(vc, m, n, Mth.cos(a0) * r0, y, Mth.sin(a0) * r0, 0.5F, 0.5F, red, green, blue, alpha);
        vertex(vc, m, n, Mth.cos(a0) * r1, y, Mth.sin(a0) * r1, 0.5F, 0.5F, red, green, blue, alpha);
        vertex(vc, m, n, Mth.cos(a1) * r1, y, Mth.sin(a1) * r1, 0.5F, 0.5F, red, green, blue, alpha);
        vertex(vc, m, n, Mth.cos(a1) * r0, y, Mth.sin(a1) * r0, 0.5F, 0.5F, red, green, blue, alpha);
    }

    // ------------------------------------------------------------ Inferno Vortex: the fire tornado
    /** three nested flame shells spinning at different speeds, flames climbing their walls */
    private void renderVortex(InfernalShadowEntity e, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        float t = e.moveTime(partialTick);
        float s = VortexShape.strength(t);
        if (s <= 0.0F) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FLAMES));
        PoseStack.Pose pose = poseStack.last();
        float h = VortexShape.HEIGHT * s;
        int segs = 28, rows = 16;
        // purple fire: a deep outer shell, the hero's purple, a hot lavender core
        float[][] tint = {{0.62F, 0.26F, 1.0F, 0.55F}, {0.82F, 0.5F, 1.0F, 0.7F}, {1.0F, 0.86F, 1.0F, 0.6F}};
        for (int shell = 0; shell < 3; shell++) {
            float scale = 1.0F - 0.17F * shell;
            float spin = t * (0.035F + 0.022F * shell);
            float rise = t * (0.035F + 0.012F * shell);
            float[] c = tint[shell];
            for (int r = 0; r < rows; r++) {
                float y0 = h * r / rows, y1 = h * (r + 1) / rows;
                float a0 = c[3] * edgeFade(r, rows), a1 = c[3] * edgeFade(r + 1, rows);
                for (int k = 0; k < segs; k++) {
                    float t0 = (float) (Math.PI * 2 * k / segs), t1 = (float) (Math.PI * 2 * (k + 1) / segs);
                    float u0 = 3.0F * k / segs + spin, u1 = 3.0F * (k + 1) / segs + spin;
                    float v0 = 2.0F * y0 / VortexShape.HEIGHT - rise, v1 = 2.0F * y1 / VortexShape.HEIGHT - rise;
                    ring(vc, pose, y0, t0, s, scale, t, u0, v0, c, a0);
                    ring(vc, pose, y0, t1, s, scale, t, u1, v0, c, a0);
                    ring(vc, pose, y1, t1, s, scale, t, u1, v1, c, a1);
                    ring(vc, pose, y1, t0, s, scale, t, u0, v1, c, a1);
                }
            }
        }
    }

    /** 0 at the very bottom and top, 1 between */
    private static float edgeFade(int row, int rows) {
        float u = (float) row / rows;
        return Mth.clamp(u / 0.08F, 0.0F, 1.0F) * Mth.clamp((1.0F - u) / 0.22F, 0.0F, 1.0F);
    }

    private static void ring(VertexConsumer vc, PoseStack.Pose pose, float y, float angle, float s, float scale, float t,
                             float u, float v, float[] c, float alpha) {
        float r = VortexShape.radiusAt(y, s) * scale;
        float x = VortexShape.swayX(y, t) + Mth.cos(angle) * r;
        float z = VortexShape.swayZ(y, t) + Mth.sin(angle) * r;
        vc.vertex(pose.pose(), x, y, z).color(c[0], c[1], c[2], alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), Mth.cos(angle), 0.0F, Mth.sin(angle)).endVertex();
    }

    static void disc(VertexConsumer vc, PoseStack.Pose pose, float cx, float cz, float r, float y,
                             float red, float green, float blue, float alpha) {
        if (r <= 0.01F || alpha <= 0.0F) {
            return;
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vertex(vc, m, n, cx - r, y, cz - r, 0, 0, red, green, blue, alpha);
        vertex(vc, m, n, cx - r, y, cz + r, 0, 1, red, green, blue, alpha);
        vertex(vc, m, n, cx + r, y, cz + r, 1, 1, red, green, blue, alpha);
        vertex(vc, m, n, cx + r, y, cz - r, 1, 0, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               float red, float green, float blue, float alpha) {
        vc.vertex(m, x, y, z).color(red, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    // ------------------------------------------------------------ model: textures from the copied player
    private static final class ShadowModel extends DefaultedEntityGeoModel<InfernalShadowEntity> {
        ShadowModel() {
            // GeckoLib's own head turning would overwrite the animated head, so it is added on top below instead
            super(new ResourceLocation(RuduesMod.MODID, "infernal_shadow"), false);
        }

        /** between moves the head follows what the Shadow is looking at, on top of the animation */
        @Override
        public void setCustomAnimations(InfernalShadowEntity animatable, long instanceId, AnimationState<InfernalShadowEntity> state) {
            if (animatable.isInMove()) {
                return;
            }
            CoreGeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && data != null) {
                head.setRotX(head.getRotX() + Mth.clamp(data.headPitch(), -50.0F, 50.0F) * Mth.DEG_TO_RAD * 0.85F);
                head.setRotY(head.getRotY() + Mth.clamp(data.netHeadYaw(), -65.0F, 65.0F) * Mth.DEG_TO_RAD * 0.85F);
            }
        }

        @Override
        public ResourceLocation getTextureResource(InfernalShadowEntity animatable) {
            PlayerInfo info = copiedPlayer(animatable);
            return info != null ? info.getSkinLocation() : FALLBACK;
        }

        @Override
        public RenderType getRenderType(InfernalShadowEntity animatable, ResourceLocation texture) {
            // player skins use partial transparency in their outer layer
            return RenderType.entityTranslucent(texture);
        }
    }

    // ------------------------------------------------------------ armour: one re-render per worn piece, with its texture
    private final class ArmorPassLayer extends GeoRenderLayer<InfernalShadowEntity> {
        ArmorPassLayer(InfernalShadowRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, InfernalShadowEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack stack = animatable.getItemBySlot(slot);
                if (!(stack.getItem() instanceof ArmorItem)) {
                    continue;
                }
                RenderType type = RenderType.armorCutoutNoCull(armorTexture(animatable, stack, slot));
                armorPass = slot;
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
                        partialTick, packedLight, packedOverlay, 1, 1, 1, 1);
                if (stack.hasFoil()) {
                    RenderType glint = RenderType.armorEntityGlint();
                    getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glint, bufferSource.getBuffer(glint),
                            partialTick, packedLight, packedOverlay, 1, 1, 1, 1);
                }
            }
            armorPass = null;
            bufferSource.getBuffer(renderType);
        }
    }

    // ------------------------------------------------------------ phase 2: the growths on the left arm and the legs
    private final class CorruptionLayer extends GeoRenderLayer<InfernalShadowEntity> {
        CorruptionLayer(InfernalShadowRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, InfernalShadowEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!animatable.corruptionShown(partialTick)) {
                return;
            }
            corruptionPass = true;
            RenderType type = RenderType.entityCutoutNoCull(CORRUPTION);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
                    partialTick, packedLight, packedOverlay, 1, 1, 1, 1);
            // the veins: additive and full bright, so they burn in the dark
            float g = animatable.corruptionGlow(partialTick);
            RenderType glow = RenderType.eyes(CORRUPTION_GLOW);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, Math.min(1.0F, g), Math.min(1.0F, g), Math.min(1.0F, g), 1);
            corruptionPass = false;
            bufferSource.getBuffer(renderType);
        }
    }

    /** the vanilla armour texture of a piece (layer 2 for leggings), through Forge's hook so modded armour works too */
    private static ResourceLocation armorTexture(InfernalShadowEntity entity, ItemStack stack, EquipmentSlot slot) {
        ArmorItem item = (ArmorItem) stack.getItem();
        String material = item.getMaterial().getName();
        String domain = "minecraft";
        int colon = material.indexOf(':');
        if (colon != -1) {
            domain = material.substring(0, colon);
            material = material.substring(colon + 1);
        }
        String path = String.format(java.util.Locale.ROOT, "%s:textures/models/armor/%s_layer_%d.png", domain, material,
                slot == EquipmentSlot.LEGS ? 2 : 1);
        return new ResourceLocation(ForgeHooksClient.getArmorTexture(entity, stack, path, slot, null));
    }

    // ------------------------------------------------------------ the sword: the main-hand item, laid along the blade bone
    private static final class SwordLayer extends BlockAndItemGeoLayer<InfernalShadowEntity> {
        SwordLayer(InfernalShadowRenderer renderer) {
            super(renderer);
        }

        @Nullable
        @Override
        protected ItemStack getStackForBone(GeoBone bone, InfernalShadowEntity animatable) {
            if (!"sword_item".equals(bone.getName())) {
                return null;
            }
            ItemStack stack = animatable.getMainHandItem();
            return stack.isEmpty() ? null : stack;
        }

        @Override
        protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, InfernalShadowEntity animatable,
                                          MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
            poseStack.pushPose();
            // the sprite's pommel->tip diagonal onto the bone's -Z (blade forward), the flat facing sideways
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
            poseStack.translate(GRIP, GRIP, 0.0F);
            Minecraft.getInstance().getItemRenderer().renderStatic(animatable, stack, ItemDisplayContext.NONE, false, poseStack,
                    bufferSource, animatable.level(), packedLight, packedOverlay, animatable.getId());
            poseStack.popPose();
        }
    }
}
