package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.WindBallEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Garuda's great wind ball: whirling green wind, taking the Hero of Hell's dark purple fire as he pushes into it. */
public class WindBallRenderer extends WindRenderers.Base<WindBallEntity> {
    private static final ResourceLocation FIRE = new ResourceLocation(RuduesMod.MODID, "textures/entity/hero_fireball.png");

    public WindBallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(WindBallEntity e, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(WindBallEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = e.tickCount + pt;
        float r = e.radius(), fire = e.fire(), wind = 1.0F - fire;
        ps.pushPose();
        if (wind > 0.02F) {
            VertexConsumer st = buffers.getBuffer(RenderType.eyes(WindRenderers.STREAK));
            for (int i = 0; i < 3; i++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(age * (10.0F + 6.0F * i) * (i == 1 ? -1 : 1)));
                ps.mulPose(Axis.XP.rotationDegrees(i == 0 ? 20.0F : i == 1 ? -55.0F : 80.0F));
                WindRenderers.band(ps, st, r, r * 0.9F, -0.3F * r, 0.3F * r, 24, -age * 0.04F * (i + 1), 3.0F, 0.7F * wind, 0, 0, 0, 0);
                ps.popPose();
            }
        }
        faceCamera(ps);
        if (wind > 0.02F) {
            VertexConsumer sw = buffers.getBuffer(RenderType.eyes(WindRenderers.SWIRL));
            WindRenderers.swirl(ps, sw, r * 2.6F, -age * 12.0F, (int) (age / 2) % 4, 0.6F * wind);
            WindRenderers.swirl(ps, sw, r * 2.1F, age * 18.0F, (int) (age / 2 + 1) % 4, 1.0F * wind);
            WindRenderers.swirl(ps, sw, r * 1.1F, -age * 30.0F, (int) (age / 2 + 3) % 4, 1.0F * wind);
        }
        if (fire > 0.02F) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FIRE));
            int frame = (int) (age / 2.0F) % 4;
            fireQuad(vc, ps, r * 2.5F * (1.0F + 0.05F * Mth.sin(age * 0.9F)), age * 7.0F, frame, fire);
            fireQuad(vc, ps, r * 3.2F, -age * 4.0F, (frame + 2) % 4, 0.5F * fire);
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buffers, light);
    }

    private static void fireQuad(VertexConsumer vc, PoseStack ps, float size, float spin, int frame, float alpha) {
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float h = size / 2.0F, v0 = frame / 4.0F, v1 = (frame + 1) / 4.0F;
        v(vc, m, n, -h, -h, 0, v1, alpha);
        v(vc, m, n, h, -h, 1, v1, alpha);
        v(vc, m, n, h, h, 1, v0, alpha);
        v(vc, m, n, -h, h, 0, v0, alpha);
        ps.popPose();
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float w, float a) {
        vc.vertex(m, x, y, 0.0F).color(1.0F, 1.0F, 1.0F, a).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
