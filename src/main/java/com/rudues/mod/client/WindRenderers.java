package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.AerogaOrbEntity;
import com.rudues.mod.entity.TornadoEntity;
import com.rudues.mod.entity.WindBladeEntity;
import com.rudues.mod.entity.WindBoltEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Feathered Knight's wind, drawn additively and full bright from two textures: a four-frame spiralling gust
 * (camera-facing swirls) and a strip of streaks (wrapped round twisters, laid along blades and trails). Black in the
 * textures is clear; the vertex colour scales the light.
 */
public final class WindRenderers {
    private WindRenderers() {
    }

    static final ResourceLocation SWIRL = new ResourceLocation(RuduesMod.MODID, "textures/entity/wind_swirl.png");
    static final ResourceLocation STREAK = new ResourceLocation(RuduesMod.MODID, "textures/entity/wind_streak.png");
    private static final int FRAMES = 4;

    // ------------------------------------------------------------ drawing
    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float w, float k) {
        vc.vertex(m, x, y, z).color(k, k, k, 1.0F).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** a quad and its back face */
    private static void quad2(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] a, float[] b, float[] c, float[] d) {
        // each corner: x, y, z, u, v, k
        v(vc, m, n, a[0], a[1], a[2], a[3], a[4], a[5]);
        v(vc, m, n, b[0], b[1], b[2], b[3], b[4], b[5]);
        v(vc, m, n, c[0], c[1], c[2], c[3], c[4], c[5]);
        v(vc, m, n, d[0], d[1], d[2], d[3], d[4], d[5]);
        v(vc, m, n, d[0], d[1], d[2], d[3], d[4], d[5]);
        v(vc, m, n, c[0], c[1], c[2], c[3], c[4], c[5]);
        v(vc, m, n, b[0], b[1], b[2], b[3], b[4], b[5]);
        v(vc, m, n, a[0], a[1], a[2], a[3], a[4], a[5]);
    }

    /** a camera-facing swirl (call with the pose already turned to the camera) */
    static void swirl(PoseStack ps, VertexConsumer vc, float size, float spin, int frame, float k) {
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float h = size / 2.0F, v0 = (float) (frame % FRAMES) / FRAMES, v1 = v0 + 1.0F / FRAMES;
        v(vc, m, n, -h, -h, 0, 0, v1, k);
        v(vc, m, n, h, -h, 0, 1, v1, k);
        v(vc, m, n, h, h, 0, 1, v0, k);
        v(vc, m, n, -h, h, 0, 0, v0, k);
        ps.popPose();
    }

    /** a ring of streaks round the y axis, from radius r0 at y0 to r1 at y1, scrolled round by u0 */
    static void band(PoseStack ps, VertexConsumer vc, float r0, float r1, float y0, float y1, int seg, float u0, float reps, float k,
                     float ox0, float oz0, float ox1, float oz1) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (i * Math.PI * 2 / seg), a1 = (float) ((i + 1) * Math.PI * 2 / seg);
            float ua = u0 + reps * i / seg, ub = u0 + reps * (i + 1) / seg;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            quad2(vc, m, n,
                    new float[]{ox0 + c0 * r0, y0, oz0 + s0 * r0, ua, 1, k},
                    new float[]{ox0 + c1 * r0, y0, oz0 + s1 * r0, ub, 1, k},
                    new float[]{ox1 + c1 * r1, y1, oz1 + s1 * r1, ub, 0, k},
                    new float[]{ox1 + c0 * r1, y1, oz1 + s0 * r1, ua, 0, k});
        }
    }

    private static int frame(float age) {
        return (int) (age / 2.0F) % FRAMES;
    }

    abstract static class Base<T extends Entity> extends EntityRenderer<T> {
        Base(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.0F;
        }

        @Override
        protected int getBlockLightLevel(T entity, BlockPos pos) {
            return 15;
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return SWIRL;
        }

        void faceCamera(PoseStack ps) {
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
    }

    // ------------------------------------------------------------ Wind Burst
    public static final class Bolt extends Base<WindBoltEntity> {
        public Bolt(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(WindBoltEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float age = e.tickCount + pt;
            float in = Mth.clamp(age / 3.0F, 0.0F, 1.0F);
            ps.pushPose();
            ps.translate(0.0F, 0.25F, 0.0F);
            // the trail: two crossed streak ribbons laid back along the flight
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(pt, e.yRotO, e.getYRot())));
            ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
            VertexConsumer st = buffers.getBuffer(RenderType.eyes(STREAK));
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float len = 1.9F * in, w = 0.28F, u = -age * 0.09F;
            quad2(st, m, n, new float[]{-w, 0, 0.2F, u, 0.15F, 0.9F}, new float[]{w, 0, 0.2F, u, 0.85F, 0.9F},
                    new float[]{w * 0.3F, 0, -len, u + 0.6F, 0.85F, 0.0F}, new float[]{-w * 0.3F, 0, -len, u + 0.6F, 0.15F, 0.0F});
            quad2(st, m, n, new float[]{0, -w, 0.2F, u + 0.3F, 0.15F, 0.9F}, new float[]{0, w, 0.2F, u + 0.3F, 0.85F, 0.9F},
                    new float[]{0, w * 0.3F, -len, u + 0.9F, 0.85F, 0.0F}, new float[]{0, -w * 0.3F, -len, u + 0.9F, 0.15F, 0.0F});
            ps.popPose();
            // the gust itself
            faceCamera(ps);
            VertexConsumer sw = buffers.getBuffer(RenderType.eyes(SWIRL));
            swirl(ps, sw, 1.05F * in, -age * 32.0F, frame(age), 1.0F);
            swirl(ps, sw, 0.6F * in, age * 20.0F, frame(age) + 2, 0.7F);
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }
    }

    // ------------------------------------------------------------ Aeroga
    public static final class Orb extends Base<AerogaOrbEntity> {
        public Orb(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(AerogaOrbEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float age = e.tickCount + pt;
            float s = e.size(pt);
            float pulse = 1.0F + 0.05F * Mth.sin(age * 0.8F);
            ps.pushPose();
            ps.translate(0.0F, 0.5F, 0.0F);
            // two rings of streaks whirling round it on crossed tilts, so it reads as a ball, not a disc
            VertexConsumer st = buffers.getBuffer(RenderType.eyes(STREAK));
            for (int i = 0; i < 2; i++) {
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(age * (i == 0 ? 14.0F : -11.0F)));
                ps.mulPose(Axis.XP.rotationDegrees(i == 0 ? 24.0F : -58.0F));
                float r = 0.5F * s * pulse;
                band(ps, st, r, r * 0.92F, -0.16F * s, 0.16F * s, 14, -age * 0.05F * (i + 1), 2.0F, 0.75F, 0, 0, 0, 0);
                ps.popPose();
            }
            faceCamera(ps);
            VertexConsumer sw = buffers.getBuffer(RenderType.eyes(SWIRL));
            swirl(ps, sw, s * 1.25F * pulse, -age * 18.0F, frame(age), 0.55F);
            swirl(ps, sw, s * pulse, age * 26.0F, frame(age) + 1, 1.0F);
            swirl(ps, sw, s * 0.55F, -age * 40.0F, frame(age) + 3, 1.0F);
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }
    }

    // ------------------------------------------------------------ the tornado
    public static final class Tornado extends Base<TornadoEntity> {
        private static final int LAYERS = 9;

        public Tornado(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(TornadoEntity e, Frustum frustum, double x, double y, double z) {
            return true;
        }

        @Override
        public void render(TornadoEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float age = e.tickCount + pt;
            float s = e.strength(pt);
            if (s <= 0.0F) {
                return;
            }
            float height = TornadoEntity.HEIGHT * (0.35F + 0.65F * s);
            VertexConsumer st = buffers.getBuffer(RenderType.eyes(STREAK));
            ps.pushPose();
            for (int i = 0; i < LAYERS; i++) {
                float h0 = height * i / LAYERS, h1 = height * (i + 1.4F) / LAYERS;
                // the funnel widens upward and its spine sways
                float r0 = (0.35F + 0.27F * h0) * (0.6F + 0.4F * s), r1 = (0.35F + 0.27F * h1) * (0.6F + 0.4F * s);
                float sw0 = 0.07F * h0, sw1 = 0.07F * h1;
                float ox0 = sw0 * Mth.sin(age * 0.17F + h0 * 0.7F), oz0 = sw0 * Mth.cos(age * 0.13F + h0 * 0.6F);
                float ox1 = sw1 * Mth.sin(age * 0.17F + h1 * 0.7F), oz1 = sw1 * Mth.cos(age * 0.13F + h1 * 0.6F);
                float spin = age * (0.12F + 0.02F * i);
                float k = s * (0.5F + 0.2F * Mth.sin(age * 0.3F + i)) * (i == 0 ? 0.6F : 1.0F);
                ps.pushPose();
                ps.mulPose(Axis.YP.rotationDegrees(-age * (24.0F + 5.0F * i)));
                band(ps, st, r0, r1, h0, h1, 16, spin + i * 0.37F, 2.0F, k, ox0, oz0, ox1, oz1);
                // a fainter outer skin, counter-scrolled
                band(ps, st, r0 * 1.25F, r1 * 1.25F, h0, h1, 16, -spin * 0.7F + i * 0.21F, 1.0F, k * 0.35F, ox0, oz0, ox1, oz1);
                ps.popPose();
            }
            // dust whirling at its foot
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-age * 30.0F));
            band(ps, st, 0.6F, 1.6F * (0.6F + 0.4F * s), 0.02F, 0.45F, 16, age * 0.08F, 2.0F, 0.4F * s, 0, 0, 0, 0);
            ps.popPose();
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }
    }

    // ------------------------------------------------------------ Wind Blade
    public static final class Blade extends Base<WindBladeEntity> {
        private static final int SEG = 20;
        private static final float SPREAD = 75.0F * Mth.DEG_TO_RAD;
        private static final float RADIUS = WindBladeEntity.HALF_WIDTH / Mth.sin(SPREAD);

        public Blade(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(WindBladeEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
            float age = e.tickCount + pt;
            float f = e.formed(pt), fade = e.fade(pt);
            float grow = 1.0F - (1.0F - f) * (1.0F - f);
            float k = (e.fired() ? 1.0F : 0.45F + 0.4F * f + 0.15F * Mth.sin(age * 1.3F)) * fade;
            if (k <= 0.01F) {
                return;
            }
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
            ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot())));
            ps.mulPose(Axis.ZP.rotationDegrees(-12.0F));       // swept from low left to high right
            ps.scale(0.35F + 0.65F * grow, 1.0F, 0.35F + 0.65F * grow);
            VertexConsumer st = buffers.getBuffer(RenderType.eyes(STREAK));
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float scroll = -age * (e.fired() ? 0.12F : 0.05F);
            for (int i = 0; i < SEG; i++) {
                float t0 = -1.0F + 2.0F * i / SEG, t1 = -1.0F + 2.0F * (i + 1) / SEG;
                float a0 = t0 * SPREAD, a1 = t1 * SPREAD;
                // thick in the middle, tapering to points at the tips; the tips fainter
                float w0 = 0.62F * (float) Math.pow(Math.max(0.0F, 1.0F - t0 * t0), 0.6), w1 = 0.62F * (float) Math.pow(Math.max(0.0F, 1.0F - t1 * t1), 0.6);
                float k0 = k * (1.0F - 0.55F * Math.abs(t0)), k1 = k * (1.0F - 0.55F * Math.abs(t1));
                float x0 = RADIUS * Mth.sin(a0), z0 = RADIUS * (Mth.cos(a0) - 1.0F);
                float x1 = RADIUS * Mth.sin(a1), z1 = RADIUS * (Mth.cos(a1) - 1.0F);
                float cx0 = Mth.sin(a0), cz0 = Mth.cos(a0), cx1 = Mth.sin(a1), cz1 = Mth.cos(a1);
                float u0 = scroll + 0.5F + t0 * 0.9F, u1 = scroll + 0.5F + t1 * 0.9F;
                // the flat crescent (seen from above and below): its edge leads, its body trails back
                quad2(st, m, n,
                        new float[]{x0 + cx0 * w0 * 0.3F, 0, z0 + cz0 * w0 * 0.3F, u0, 0.0F, k0},
                        new float[]{x1 + cx1 * w1 * 0.3F, 0, z1 + cz1 * w1 * 0.3F, u1, 0.0F, k1},
                        new float[]{x1 - cx1 * w1 * 1.4F, 0, z1 - cz1 * w1 * 1.4F, u1, 1.0F, 0.0F},
                        new float[]{x0 - cx0 * w0 * 1.4F, 0, z0 - cz0 * w0 * 1.4F, u0, 1.0F, 0.0F});
                // the upright edge (seen head on): a curved ribbon of light along the crescent's front
                float h0 = 0.42F * w0 / 0.62F, h1 = 0.42F * w1 / 0.62F;
                quad2(st, m, n,
                        new float[]{x0, -h0, z0, u0 + 0.3F, 1.0F, k0},
                        new float[]{x1, -h1, z1, u1 + 0.3F, 1.0F, k1},
                        new float[]{x1, h1, z1, u1 + 0.3F, 0.0F, k1},
                        new float[]{x0, h0, z0, u0 + 0.3F, 0.0F, k0});
            }
            ps.popPose();
            super.render(e, yaw, pt, ps, buffers, light);
        }
    }
}
