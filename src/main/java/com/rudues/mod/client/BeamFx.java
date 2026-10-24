package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rudues.mod.RuduesMod;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The beams, built as a lit object rather than flat planes (drawn along +Z of the pose, from 0 to length):
 *  - a soft glow that always faces the camera (a ribbon turned toward the eye, so it reads as a thick column from any
 *    side), in two widths;
 *  - an energy tube - a 12-sided cylinder of streaking fire, scrolling down its length - and a thin white-hot core;
 *  - two strands spiralling round it, twisting as it fires;
 *  - a flare where it leaves the mouth and one where it hits, with shock rings rolling off the end.
 * All additive and full bright. The player's beam is white-hot violet; the Infernal Hero's a deep dark violet.
 */
public final class BeamFx {
    private BeamFx() {
    }

    private static final ResourceLocation CORE = new ResourceLocation(RuduesMod.MODID, "textures/entity/beam_core.png");
    private static final ResourceLocation GLOW = new ResourceLocation(RuduesMod.MODID, "textures/entity/beam_glow.png");
    private static final ResourceLocation RIBBON = new ResourceLocation(RuduesMod.MODID, "textures/entity/beam_ribbon.png");
    private static final ResourceLocation FLARE = new ResourceLocation(RuduesMod.MODID, "textures/entity/beam_flare.png");
    private static final ResourceLocation RING = new ResourceLocation(RuduesMod.MODID, "textures/entity/beam_ring.png");
    private static final int SIDES = 12;

    /** colours: {glow, tube, core, strands} as r, g, b (0..1) */
    private static final float[][] PLAYER = {{0.62F, 0.30F, 1.0F}, {0.86F, 0.55F, 1.0F}, {1.0F, 0.95F, 1.0F}, {1.0F, 0.75F, 1.0F}};
    private static final float[][] DARK = {{0.30F, 0.08F, 0.58F}, {0.46F, 0.18F, 0.78F}, {0.80F, 0.62F, 0.96F}, {0.62F, 0.34F, 0.92F}};

    /** width = overall scale (1 = the player's beam); dark = the Infernal Hero's */
    public static void draw(PoseStack ps, MultiBufferSource buffers, float length, float time, float width, boolean dark) {
        if (length < 0.05F) {
            return;
        }
        float[][] c = dark ? DARK : PLAYER;
        float pulse = 1.0F + 0.10F * Mth.sin(time * 1.9F) + 0.04F * Mth.sin(time * 5.3F);
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        // where the eye is, in the beam's own frame
        Vector3f eye = new Matrix4f(m).invert().transformPosition(new Vector3f());

        // the glow: two camera-facing ribbons
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        ribbon(glow, m, n, eye, length, 1.95F * width * pulse, -time * 0.05F, length * 0.08F, c[0], 0.45F);
        ribbon(glow, m, n, eye, length, 1.05F * width * pulse, -time * 0.09F, length * 0.12F, c[1], 0.5F);
        ribbon(glow, m, n, eye, length, 0.42F * width * pulse, -time * 0.2F, length * 0.2F, c[2], 0.55F);
        // the energy tube and the core (each buffer is finished before the next is asked for: an immediate buffer
        // source shares one builder between render types)
        VertexConsumer core = buffers.getBuffer(RenderType.eyes(CORE));
        tube(core, m, n, length, 0.46F * width * pulse, time * 0.9F, -time * 0.22F, length * 0.18F, c[1], 0.4F);
        tube(core, m, n, length, 0.22F * width * pulse, -time * 1.6F, -time * 0.4F, length * 0.3F, c[2], 0.6F);
        // the spiralling strands
        VertexConsumer strands = buffers.getBuffer(RenderType.eyes(RIBBON));
        for (int k = 0; k < 2; k++) {
            helix(strands, m, n, eye, length, 0.62F * width, time * 0.35F + k * Mth.PI, 0.13F * width, -time * 0.25F, c[3], 0.85F);
        }
        // the flares at the mouth and at the end, and the shock rings
        VertexConsumer flare = buffers.getBuffer(RenderType.eyes(FLARE));
        billboard(flare, m, n, eye, new Vector3f(0, 0, 0.1F), 1.5F * width * pulse, time * 0.03F, c[2], 0.95F);
        billboard(flare, m, n, eye, new Vector3f(0, 0, length), 2.3F * width * (1.0F + 0.25F * Mth.sin(time * 2.7F)), -time * 0.05F, c[1], 1.0F);
        VertexConsumer ring = buffers.getBuffer(RenderType.eyes(RING));
        for (int k = 0; k < 3; k++) {
            float ph = ((time * 0.09F) + k / 3.0F) % 1.0F;
            disc(ring, m, n, length - 0.2F, (0.6F + 2.4F * ph) * width, c[1], 1.0F - ph);
            float pm = ((time * 0.07F) + k / 3.0F) % 1.0F;
            disc(ring, m, n, 0.25F + pm * 0.6F, (0.4F + 1.2F * pm) * width, c[0], (1.0F - pm) * 0.7F);
        }
    }

    /** a strip from the start to the end, its flat turned toward the eye */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f eye, float length, float half, float v0, float vLen,
                               float[] col, float a) {
        int seg = Math.max(1, (int) (length / 4.0F));
        for (int i = 0; i < seg; i++) {
            float z0 = length * i / seg, z1 = length * (i + 1) / seg;
            Vector3f s0 = side(eye, 0, 0, z0, half), s1 = side(eye, 0, 0, z1, half);
            float va = v0 + vLen * i / seg, vb = v0 + vLen * (i + 1) / seg;
            quad(vc, m, n, -s0.x, -s0.y, z0, 0, va, s0.x, s0.y, z0, 1, va, s1.x, s1.y, z1, 1, vb, -s1.x, -s1.y, z1, 0, vb, col, a);
        }
    }

    /** the across-vector at a point on the axis, turned to face the eye (the axis is +Z) */
    private static Vector3f side(Vector3f eye, float x, float y, float z, float half) {
        Vector3f to = new Vector3f(eye.x - x, eye.y - y, eye.z - z);
        Vector3f s = new Vector3f(0, 0, 1).cross(to);
        if (s.lengthSquared() < 1.0E-6F) {
            s.set(1, 0, 0);
        }
        return s.normalize().mul(half);
    }

    /** a cylinder around the axis; spin turns its texture round, v0 scrolls it along */
    private static void tube(VertexConsumer vc, Matrix4f m, Matrix3f n, float length, float r, float spin, float v0, float vLen,
                             float[] col, float a) {
        for (int i = 0; i < SIDES; i++) {
            float a0 = Mth.TWO_PI * i / SIDES, a1 = Mth.TWO_PI * (i + 1) / SIDES;
            float x0 = Mth.cos(a0) * r, y0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, y1 = Mth.sin(a1) * r;
            float u0 = (float) i / SIDES + spin, u1 = (float) (i + 1) / SIDES + spin;
            quad(vc, m, n, x0, y0, 0, u0, v0, x1, y1, 0, u1, v0, x1, y1, length, u1, v0 + vLen, x0, y0, length, u0, v0 + vLen, col, a);
        }
    }

    /** a thin strand spiralling round the axis */
    private static void helix(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f eye, float length, float radius, float phase, float half,
                              float v0, float[] col, float a) {
        int seg = Math.max(8, (int) (length * 3.0F));
        float turns = length / 3.5F;
        for (int i = 0; i < seg; i++) {
            float t0 = (float) i / seg, t1 = (float) (i + 1) / seg;
            float z0 = length * t0, z1 = length * t1;
            float an0 = phase + Mth.TWO_PI * turns * t0, an1 = phase + Mth.TWO_PI * turns * t1;
            float x0 = Mth.cos(an0) * radius, y0 = Mth.sin(an0) * radius, x1 = Mth.cos(an1) * radius, y1 = Mth.sin(an1) * radius;
            Vector3f d = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
            Vector3f s0 = new Vector3f(d).cross(new Vector3f(eye.x - x0, eye.y - y0, eye.z - z0));
            if (s0.lengthSquared() < 1.0E-8F) {
                continue;
            }
            s0.normalize().mul(half);
            float fade = Math.min(1.0F, Math.min(t0 * 6.0F, (1.0F - t0) * 6.0F));
            float va = v0 + t0 * length * 0.4F, vb = v0 + t1 * length * 0.4F;
            quad(vc, m, n, x0 - s0.x, y0 - s0.y, z0 - s0.z, 0, va, x0 + s0.x, y0 + s0.y, z0 + s0.z, 1, va,
                    x1 + s0.x, y1 + s0.y, z1 + s0.z, 1, vb, x1 - s0.x, y1 - s0.y, z1 - s0.z, 0, vb, col, a * fade);
        }
    }

    /** a square turned to face the eye, spun by rot */
    private static void billboard(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f eye, Vector3f at, float half, float rot, float[] col, float a) {
        Vector3f to = new Vector3f(eye).sub(at).normalize();
        Vector3f r = new Vector3f(to).cross(Math.abs(to.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
        Vector3f u = new Vector3f(r).cross(to).normalize();
        float cs = Mth.cos(rot), sn = Mth.sin(rot);
        Vector3f rr = new Vector3f(r).mul(cs).add(new Vector3f(u).mul(sn)).mul(half);
        Vector3f uu = new Vector3f(u).mul(cs).sub(new Vector3f(r).mul(sn)).mul(half);
        quad(vc, m, n, at.x - rr.x - uu.x, at.y - rr.y - uu.y, at.z - rr.z - uu.z, 0, 1,
                at.x + rr.x - uu.x, at.y + rr.y - uu.y, at.z + rr.z - uu.z, 1, 1,
                at.x + rr.x + uu.x, at.y + rr.y + uu.y, at.z + rr.z + uu.z, 1, 0,
                at.x - rr.x + uu.x, at.y - rr.y + uu.y, at.z - rr.z + uu.z, 0, 0, col, a);
    }

    /** a flat ring across the beam at z */
    private static void disc(VertexConsumer vc, Matrix4f m, Matrix3f n, float z, float half, float[] col, float a) {
        quad(vc, m, n, -half, -half, z, 0, 1, half, -half, z, 1, 1, half, half, z, 1, 0, -half, half, z, 0, 0, col, a);
    }

    /** both windings, so nothing is lost to face culling whichever side it is seen from */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4, float[] col, float a) {
        if (a <= 0.01F) {
            return;
        }
        vertex(vc, m, n, x1, y1, z1, u1, v1, col, a);
        vertex(vc, m, n, x2, y2, z2, u2, v2, col, a);
        vertex(vc, m, n, x3, y3, z3, u3, v3, col, a);
        vertex(vc, m, n, x4, y4, z4, u4, v4, col, a);
        vertex(vc, m, n, x4, y4, z4, u4, v4, col, a);
        vertex(vc, m, n, x3, y3, z3, u3, v3, col, a);
        vertex(vc, m, n, x2, y2, z2, u2, v2, col, a);
        vertex(vc, m, n, x1, y1, z1, u1, v1, col, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float[] col, float a) {
        // (eyes is additive: the alpha scales how much light it adds)
        vc.vertex(m, x, y, z).color(col[0] * a, col[1] * a, col[2] * a, 1.0F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
