package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.FireGeyserEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A fire geyser: the glowing warning circle filling up, then the column of fire. */
public class FireGeyserRenderer extends EntityRenderer<FireGeyserEntity> {
    private static final ResourceLocation RING = new ResourceLocation(RuduesMod.MODID, "textures/effect/hammer_ring.png");
    private static final ResourceLocation FILL = new ResourceLocation(RuduesMod.MODID, "textures/effect/hammer_fill.png");

    public FireGeyserRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(FireGeyserEntity e, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = e.tickCount + partialTick;
        PoseStack.Pose pose = poseStack.last();
        float r = FireGeyserEntity.RADIUS;
        if (age < FireGeyserEntity.WARN_TICKS + 3) {
            float p = e.warnProgress(partialTick);
            float pulse = 0.5F + 0.5F * Mth.sin(age * (0.5F + 1.2F * p));
            float fade = Math.min(1.0F, age / 4.0F) * (age > FireGeyserEntity.WARN_TICKS ? 1.0F - (age - FireGeyserEntity.WARN_TICKS) / 3.0F : 1.0F);
            InfernalShadowRenderer.disc(buffers.getBuffer(RenderType.entityTranslucentEmissive(FILL)), pose, 0, 0, r * p, 0.03F,
                    1.0F, 0.15F, 0.07F, (0.3F + 0.15F * pulse) * fade);
            InfernalShadowRenderer.disc(buffers.getBuffer(RenderType.entityTranslucentEmissive(RING)), pose, 0, 0, r, 0.04F,
                    1.0F, 0.2F, 0.1F, (0.7F + 0.3F * pulse) * fade);
        }
        float erupt = e.eruption(partialTick);
        if (erupt > 0) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(InfernalShadowRenderer.FLAMES));
            float h = FireGeyserEntity.HEIGHT * erupt;
            for (int shell = 0; shell < 2; shell++) {
                float rad = r * (shell == 0 ? 0.95F : 0.6F) * (0.85F + 0.15F * Mth.sin(age * 0.7F + shell));
                float rise = age * (0.09F + 0.04F * shell), spin = age * 0.02F * (shell == 0 ? 1 : -1);
                float[] c = shell == 0 ? new float[]{0.7F, 0.38F, 1.0F, 0.75F} : new float[]{1.0F, 0.85F, 1.0F, 0.8F};
                int segs = 16, rows = 6;
                for (int row = 0; row < rows; row++) {
                    float y0 = h * row / rows, y1 = h * (row + 1) / rows;
                    float a0 = c[3] * erupt * fade(row, rows), a1 = c[3] * erupt * fade(row + 1, rows);
                    // the column flares out a little at the top
                    float w0 = rad * (1.0F + 0.35F * row / rows), w1 = rad * (1.0F + 0.35F * (row + 1) / rows);
                    for (int k = 0; k < segs; k++) {
                        float t0 = (float) (Math.PI * 2 * k / segs), t1 = (float) (Math.PI * 2 * (k + 1) / segs);
                        float u0 = 2.0F * k / segs + spin, u1 = 2.0F * (k + 1) / segs + spin;
                        float v0 = y0 / 3.0F - rise, v1 = y1 / 3.0F - rise;
                        vertex(vc, pose, t0, w0, y0, u0, v0, c, a0);
                        vertex(vc, pose, t1, w0, y0, u1, v0, c, a0);
                        vertex(vc, pose, t1, w1, y1, u1, v1, c, a1);
                        vertex(vc, pose, t0, w1, y1, u0, v1, c, a1);
                    }
                }
            }
        }
        super.render(e, yaw, partialTick, poseStack, buffers, light);
    }

    private static float fade(int row, int rows) {
        float u = (float) row / rows;
        return Mth.clamp((1.0F - u) / 0.35F, 0.0F, 1.0F);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float angle, float rad, float y, float u, float v, float[] c, float a) {
        vc.vertex(pose.pose(), Mth.cos(angle) * rad, y, Mth.sin(angle) * rad).color(c[0], c[1], c[2], a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), Mth.cos(angle), 0.0F, Mth.sin(angle)).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(FireGeyserEntity entity) {
        return InfernalShadowRenderer.FLAMES;
    }
}
