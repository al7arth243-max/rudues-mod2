package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.HeroMoveData;
import com.rudues.mod.entity.ClashScript;
import com.rudues.mod.entity.InfernalHeroEntity;
import com.rudues.mod.network.ClashPacket;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The Infernal Hero: the dark Titan at the player's Hero of Hell size, its veins burning a deep violet (a second,
 * full-bright additive pass), the tail sweep's spin, its Hell Beam, and - during the end fight - drawn exactly where
 * the clash script puts it so its fist meets the player's.
 */
public class InfernalHeroRenderer extends GeoEntityRenderer<InfernalHeroEntity> {
    private static final ResourceLocation GLOW = new ResourceLocation(RuduesMod.MODID, "textures/entity/infernal_hero_glow.png");

    public InfernalHeroRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(new ResourceLocation(RuduesMod.MODID, "infernal_hero")));
        withScale(InfernalHeroEntity.SCALE);
        this.shadowRadius = 2.2F;
        addRenderLayer(new GlowLayer(this));
    }

    @Override
    public boolean shouldRender(InfernalHeroEntity entity, Frustum frustum, double x, double y, double z) {
        return ClashClient.stateFor(entity.getId()) != null || super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public void render(InfernalHeroEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        ClashPacket clash = ClashClient.stateFor(entity.getId());
        if (clash != null && ClashClient.scripted(clash)) {
            Vec3 want = ClashClient.heroPos(clash, partialTick);
            Vec3 delta = want.subtract(entity.getPosition(partialTick));
            poseStack.translate(delta.x, delta.y, delta.z);
        }
        float spin = entity.spinYaw(partialTick);
        if (clash != null && ClashClient.scripted(clash)) {
            spin += ClashScript.poundSpin(clash.stage, clash.phase, clash.result, ClashClient.time(clash, partialTick));   // the giant swing
        }
        if (spin != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-spin));
        }
        // dark obsidian: drawn fully lit like the player's own Titan, so it never goes black
        super.render(entity, entityYaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
        if (entity.getMove() == InfernalHeroEntity.MOVE_BEAM && entity.beamLength() > 0.1F) {
            renderBeam(entity, partialTick, poseStack, buffers);
        }
    }

    /** the Hell Beam, from the jaws along the synced aim */
    private void renderBeam(InfernalHeroEntity e, float partialTick, PoseStack poseStack, MultiBufferSource buffers) {
        int i = Mth.clamp((int) e.moveTime(0), 0, HeroMoveData.HELL_BEAM_MOUTH_F.length - 1);
        float yaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        float r = yaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(r), fz = Mth.cos(r), rx = -Mth.cos(r), rz = -Mth.sin(r);
        double mx = fx * HeroMoveData.HELL_BEAM_MOUTH_F[i] + rx * HeroMoveData.HELL_BEAM_MOUTH_R[i];
        double mz = fz * HeroMoveData.HELL_BEAM_MOUTH_F[i] + rz * HeroMoveData.HELL_BEAM_MOUTH_R[i];
        poseStack.pushPose();
        poseStack.translate(mx, HeroMoveData.HELL_BEAM_MOUTH_U[i], mz);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(e.beamPitch()));
        drawDarkBeam(poseStack, buffers, e.beamLength(), e.tickCount + partialTick, 1.25F);
        poseStack.popPose();
    }

    /** the Hero's beam: the player's beam, a deeper darker violet (oriented along +z of the pose) */
    static void drawDarkBeam(PoseStack ps, MultiBufferSource buffers, float length, float time, float width) {
        float pulse = 1.0F + 0.12F * Mth.sin(time * 1.7F);
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(BeamRenderer.TEXTURE));
        BeamRenderer.drawPlanes(ps, vc, length, 1.7F * width * pulse, -time * 0.28F, 92, 30, 170, 200);
        BeamRenderer.drawPlanes(ps, vc, length, 0.95F * width * pulse, -time * 0.5F, 128, 60, 210, 255);
        BeamRenderer.drawPlanes(ps, vc, length, 0.4F * width * pulse, -time * 0.85F, 196, 150, 235, 255);
    }

    /** the veins: drawn again additively, full bright */
    private static final class GlowLayer extends GeoRenderLayer<InfernalHeroEntity> {
        GlowLayer(InfernalHeroRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, InfernalHeroEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            float k = 0.78F + 0.22F * Mth.sin((animatable.tickCount + partialTick) * 0.09F);
            RenderType glow = RenderType.eyes(GLOW);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, k, k, k, 1.0F);
            bufferSource.getBuffer(renderType);
        }
    }
}
