package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.ShadowFireballEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** The Hellfire Volley's fireball: a churning ball of purple fire, always facing the camera, full bright. */
public class ShadowFireballRenderer extends EntityRenderer<ShadowFireballEntity> {
    /** 4 frames of 32x32, stacked */
    private static final ResourceLocation TEXTURE = new ResourceLocation(RuduesMod.MODID, "textures/entity/shadow_fireball.png");
    private static final int FRAMES = 4;

    public ShadowFireballRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    protected int getBlockLightLevel(ShadowFireballEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public void render(ShadowFireballEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        ps.pushPose();
        ps.translate(0.0F, 0.15F, 0.0F);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        int frame = (int) (age / 2.0F) % FRAMES;
        // the ball, then a slower counter-spinning halo behind it
        quad(vc, ps, 1.1F * (1.0F + 0.06F * (float) Math.sin(age * 0.9F)), age * 9.0F, frame, 1.0F);
        quad(vc, ps, 1.55F, -age * 5.0F, (frame + 2) % FRAMES, 0.45F);
        ps.popPose();
        super.render(e, yaw, partialTick, ps, buffers, light);
    }

    private static void quad(VertexConsumer vc, PoseStack ps, float size, float spin, int frame, float alpha) {
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float h = size / 2.0F, v0 = (float) frame / FRAMES, v1 = (float) (frame + 1) / FRAMES;
        vertex(vc, m, n, -h, -h, 0, v1, alpha);
        vertex(vc, m, n, h, -h, 1, v1, alpha);
        vertex(vc, m, n, h, h, 1, v0, alpha);
        vertex(vc, m, n, -h, h, 0, v0, alpha);
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, float alpha) {
        vc.vertex(m, x, y, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowFireballEntity entity) {
        return TEXTURE;
    }
}
