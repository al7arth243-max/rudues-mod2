package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rudues.mod.RuduesMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A rendered explosion (Hellfire's detonation), drawn as real shapes rather than only particles:
 *  - a blinding star-flash at the heart, and a white-out of the screen for anyone close;
 *  - the fireball: two turbulent, scrolling shells of violet fire that balloon out, then rise and flatten into a
 *    mushroom cap on a column of fire, cooling and darkening into a rolling smoke shell as they go;
 *  - shock rings racing out across the ground, and a faint pressure dome sweeping through the air;
 *  - the camera shakes with the blast, harder the nearer it is.
 * Everything runs off the start tick, so every client draws the same explosion.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class ExplosionFx {
    private ExplosionFx() {
    }

    private static final ResourceLocation FIRE = new ResourceLocation(RuduesMod.MODID, "textures/effect/boom_fire.png");
    private static final ResourceLocation SMOKE = new ResourceLocation(RuduesMod.MODID, "textures/effect/boom_smoke.png");
    private static final ResourceLocation RING = new ResourceLocation(RuduesMod.MODID, "textures/effect/boom_ring.png");
    private static final ResourceLocation FLASH = new ResourceLocation(RuduesMod.MODID, "textures/effect/boom_flash.png");
    /** seconds an explosion lasts */
    private static final float LIFE = 5.0F;
    private static final int LON = 28, LAT = 16;

    /** centre, the ground it sits on, its size (1 = Hellfire: a fireball ~14 blocks across the radius) and start tick */
    private record Boom(Vec3 pos, double ground, float size, long start) {
    }

    private static final List<Boom> BOOMS = new ArrayList<>();

    public static void spawn(double x, double y, double z, double ground, float size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            BOOMS.add(new Boom(new Vec3(x, y, z), ground, size, mc.level.getGameTime()));
        }
    }

    private static float age(Boom b, float pt) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? LIFE : (mc.level.getGameTime() - b.start() + pt) / 20.0F;
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BOOMS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BOOMS.clear();
            return;
        }
        BOOMS.removeIf(b -> age(b, 0.0F) > LIFE || age(b, 0.0F) < -1.0F);
    }

    // ------------------------------------------------------------------ the shapes
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BOOMS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = event.getPoseStack();
        for (Boom b : BOOMS) {
            float t = age(b, pt);
            if (t < 0.0F || t > LIFE) {
                continue;
            }
            ps.pushPose();
            ps.translate(b.pos().x - cam.x, b.pos().y - cam.y, b.pos().z - cam.z);
            draw(ps, buffers, b, t);
            ps.popPose();
        }
        buffers.endBatch();
    }

    private static void draw(PoseStack ps, MultiBufferSource.BufferSource buffers, Boom b, float t) {
        float s = b.size();
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        Vector3f eye = new Matrix4f(m).invert().transformPosition(new Vector3f());
        float groundY = (float) (b.ground() - b.pos().y);

        // how hot it still is: a flash-in over 0.08 s, then cooling
        float heat = t < 0.08F ? t / 0.08F : (float) Math.exp(-(t - 0.08F) * 0.6F);
        // the fireball balloons out, then the whole mass climbs, spreads and squashes into a mushroom cap
        float radius = s * 12.0F * (1.0F - (float) Math.exp(-t * 5.0F)) * (1.0F + 0.15F * smooth(0.8F, 4.0F, t));
        float rise = s * 18.0F * smooth(0.45F, 4.6F, t);
        float squash = 1.0F - 0.35F * smooth(0.6F, 3.5F, t);
        float spread = smooth(0.6F, 3.5F, t);

        // ---- additive fire first (no depth writes)
        VertexConsumer fire = buffers.getBuffer(RenderType.eyes(FIRE));
        // the column the cap rises on
        if (rise > 0.3F) {
            float colR = s * (2.2F + 1.6F * smooth(0.5F, 2.5F, t));
            column(fire, m, n, eye, groundY, rise - radius * squash * 0.3F, colR, -t * 0.35F, new float[]{0.62F, 0.22F, 0.95F}, 0.85F * heat);
        }
        // the billows: a cauliflower of lumpy violet fireballs
        cloud(fire, m, n, eye, t, radius, rise, squash, spread, 1.0F, 1.0F, 0.22F, new float[]{0.62F, 0.22F, 1.0F}, 0.95F * heat, false);
        // the white-hot heart, burning out sooner
        float coreHeat = t < 0.08F ? t / 0.08F : (float) Math.exp(-(t - 0.08F) * 1.6F);
        lumpy(fire, m, n, eye, t, 0.0F, rise, 0.0F, radius * 0.6F, radius * 0.6F * squash, 11, -t * 0.4F,
                new float[]{1.0F, 0.85F, 1.0F}, coreHeat, false);
        // the pressure dome: only its rim shows, sweeping out through the air
        float dome = s * 38.0F * (1.0F - (float) Math.exp(-t * 3.6F));
        float domeA = (float) Math.exp(-t * 3.2F);
        if (domeA > 0.02F) {
            sphere(fire, m, n, eye, 0.0F, groundY, 0.0F, dome, dome * 0.75F, 0.0F, t * 0.5F, new float[]{0.7F, 0.45F, 1.0F}, 0.55F * domeA, true);
        }
        // ---- the shock rings on the ground
        VertexConsumer ring = buffers.getBuffer(RenderType.eyes(RING));
        for (int k = 0; k < 3; k++) {
            float tk = t - k * 0.13F;
            if (tk <= 0.0F) {
                continue;
            }
            float outer = s * (42.0F - 9.0F * k) * (1.0F - (float) Math.exp(-tk * (3.4F - 0.5F * k)));
            float band = s * (2.5F + 5.0F * tk);
            float a = (1.0F - smooth(0.15F, 1.6F - 0.2F * k, tk)) * (k == 0 ? 1.0F : 0.6F);
            annulus(ring, m, n, groundY + 0.12F + 0.05F * k, Math.max(0.0F, outer - band), outer, tk * 0.3F, new float[]{0.85F, 0.5F, 1.0F}, a);
        }
        // ---- the star-flash at the heart
        float flashA = (float) Math.exp(-t * 5.5F) * (t < 0.04F ? t / 0.04F : 1.0F);
        if (flashA > 0.02F) {
            VertexConsumer fl = buffers.getBuffer(RenderType.eyes(FLASH));
            billboard(fl, m, n, eye, new Vector3f(0.0F, 0.0F, 0.0F), s * (10.0F + 46.0F * t), t * 0.6F, new float[]{1.0F, 0.88F, 1.0F}, flashA);
            billboard(fl, m, n, eye, new Vector3f(0.0F, 0.0F, 0.0F), s * (5.0F + 14.0F * t), -t * 0.9F, new float[]{1.0F, 1.0F, 1.0F}, flashA);
        }
        // ---- smoke last: it rolls in round the cooling fireball and darkens it into a mushroom of soot
        float smokeA = smooth(0.7F, 2.0F, t) * (1.0F - smooth(3.6F, LIFE, t)) * 0.8F;
        if (smokeA > 0.02F) {
            VertexConsumer sm = buffers.getBuffer(RenderType.entityTranslucent(SMOKE));
            cloud(sm, m, n, eye, t, radius, rise, squash, spread, 1.1F, 1.0F + 0.2F * smooth(0.8F, 4.5F, t), 0.08F, null, smokeA, true);
        }
    }

    /** where the billows sit (unit directions spread over the sphere, mostly the top half) and how big each is */
    private static final float[][] PUFFS = new float[10][];

    static {
        for (int i = 0; i < PUFFS.length; i++) {
            float y = 1.0F - (i + 0.5F) / PUFFS.length * 1.4F;
            float rxz = (float) Math.sqrt(Math.max(0.0F, 1.0F - y * y));
            float th = i * 2.39996F;
            double h = Math.sin(i * 12.9898) * 43758.5453;
            float f = 0.75F + 0.35F * (float) (h - Math.floor(h));
            PUFFS[i] = new float[]{rxz * Mth.cos(th), y, rxz * Mth.sin(th), f};
        }
    }

    private static void cloud(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f eye, float t, float radius, float rise, float squash,
                              float spread, float scale, float grow, float scrollRate, float[] col, float a, boolean smoke) {
        for (int i = 0; i < PUFFS.length; i++) {
            float[] pf = PUFFS[i];
            float out = radius * 0.58F * (1.0F + 0.6F * spread) * grow;
            float r = radius * 0.5F * pf[3] * scale * grow;
            lumpy(vc, m, n, eye, t, pf[0] * out, rise + pf[1] * radius * 0.5F * squash * grow, pf[2] * out, r, r * squash, i,
                    -t * scrollRate, col, a, smoke);
        }
    }

    /**
     * One billow: a sphere with a slowly churning lumpy surface. Fire is brightest face-on and soft at the silhouette;
     * smoke is soot on top and lit faintly violet underneath by the fire below.
     */
    private static void lumpy(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f eye, float t, float cx, float cy, float cz, float r, float ry,
                              int k, float scroll, float[] col, float a, boolean smoke) {
        if (a <= 0.01F || r < 0.05F) {
            return;
        }
        int lon = 16, lat = 10;
        float[] px = new float[4], py = new float[4], pz = new float[4], w = new float[4], uu = new float[4], vv = new float[4];
        for (int j = 0; j < lat; j++) {
            for (int i = 0; i < lon; i++) {
                float under = 0.0F;
                for (int c = 0; c < 4; c++) {
                    float u = (float) (i + (c == 1 || c == 2 ? 1 : 0)) / lon;
                    float v = (float) (j + (c >= 2 ? 1 : 0)) / lat;
                    float lo = u * Mth.TWO_PI, la = (v - 0.5F) * Mth.PI;
                    float nx = Mth.cos(la) * Mth.cos(lo), ny = Mth.sin(la), nz = Mth.cos(la) * Mth.sin(lo);
                    float bump = 1.0F + 0.17F * Mth.sin(3.1F * nx + 2.3F * ny + t * 1.3F + k) * Mth.sin(2.7F * nz - 1.9F * ny + t * 0.9F + 2 * k);
                    px[c] = cx + nx * r * bump;
                    py[c] = cy + ny * ry * bump;
                    pz[c] = cz + nz * r * bump;
                    float ex = eye.x - px[c], ey = eye.y - py[c], ez = eye.z - pz[c];
                    float el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez) + 1.0E-4F;
                    float d = Math.abs((nx * ex + ny * ey + nz * ez) / el);
                    w[c] = 0.2F + 0.8F * d * d;
                    uu[c] = u * 2.0F + k * 0.37F;
                    vv[c] = v * 1.5F + scroll;
                    under += Math.max(0.0F, -ny) * 0.25F;
                }
                if (smoke) {
                    float sh = 0.10F + 0.12F * under;
                    for (int c = 0; c < 4; c++) {
                        vc.vertex(m, px[c], py[c], pz[c]).color(sh * 1.3F, sh * 0.7F, sh * 1.6F, a).uv(uu[c], vv[c])
                                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
                    }
                } else {
                    for (int c = 0; c < 4; c++) {
                        vertex(vc, m, n, px[c], py[c], pz[c], uu[c], vv[c], col, a * w[c]);
                    }
                    for (int c = 3; c >= 0; c--) {
                        vertex(vc, m, n, px[c], py[c], pz[c], uu[c], vv[c], col, a * w[c]);
                    }
                }
            }
        }
    }

    /** a stretched sphere of fire; rim = draw only the edge (the pressure dome), otherwise brightest face-on */
    private static void sphere(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f eye, float cx, float cy, float cz, float r, float ry,
                               float spin, float scroll, float[] col, float a, boolean rim) {
        if (a <= 0.01F || r < 0.05F) {
            return;
        }
        for (int j = 0; j < LAT; j++) {
            float v0 = (float) j / LAT, v1 = (float) (j + 1) / LAT;
            for (int i = 0; i < LON; i++) {
                float u0 = (float) i / LON, u1 = (float) (i + 1) / LON;
                float[][] c = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                Vector3f[] p = new Vector3f[4];
                float[] w = new float[4];
                for (int k = 0; k < 4; k++) {
                    float lon = c[k][0] * Mth.TWO_PI, lat = (c[k][1] - 0.5F) * Mth.PI;
                    Vector3f nn = new Vector3f(Mth.cos(lat) * Mth.cos(lon), Mth.sin(lat), Mth.cos(lat) * Mth.sin(lon));
                    p[k] = new Vector3f(cx + nn.x * r, cy + nn.y * ry, cz + nn.z * r);
                    Vector3f to = new Vector3f(eye).sub(p[k]).normalize();
                    float d = Math.abs(nn.dot(to));
                    w[k] = rim ? (float) Math.pow(1.0F - d, 3.0F) : 0.25F + 0.75F * d * d;
                }
                for (int k = 0; k < 4; k++) {
                    int kk = k;
                    vertex(vc, m, n, p[kk].x, p[kk].y, p[kk].z, c[kk][0] * 3.0F + spin, c[kk][1] * 2.0F + scroll, col, a * w[kk]);
                }
                for (int k = 3; k >= 0; k--) {
                    vertex(vc, m, n, p[k].x, p[k].y, p[k].z, c[k][0] * 3.0F + spin, c[k][1] * 2.0F + scroll, col, a * w[k]);
                }
            }
        }
    }

    /** an upright cylinder of fire from y0 to y1 */
    private static void column(VertexConsumer vc, Matrix4f m, Matrix3f n, Vector3f eye, float y0, float y1, float r, float scroll,
                               float[] col, float a) {
        if (a <= 0.01F || y1 <= y0) {
            return;
        }
        int sides = 16;
        for (int i = 0; i < sides; i++) {
            float a0 = Mth.TWO_PI * i / sides, a1 = Mth.TWO_PI * (i + 1) / sides;
            // pinched at the waist, flared at the foot
            float rb = r * 1.5F, rt = r;
            float x0 = Mth.cos(a0), z0 = Mth.sin(a0), x1 = Mth.cos(a1), z1 = Mth.sin(a1);
            float mx = (x0 + x1) * 0.5F, mz = (z0 + z1) * 0.5F;
            Vector3f to = new Vector3f(eye.x - mx * r, 0.0F, eye.z - mz * r).normalize();
            float face = 0.3F + 0.7F * Math.abs(mx * to.x + mz * to.z);
            float vl = (y1 - y0) / 10.0F;
            quad(vc, m, n, x0 * rb, y0, z0 * rb, (float) i / sides * 2.0F, scroll + vl,
                    x1 * rb, y0, z1 * rb, (float) (i + 1) / sides * 2.0F, scroll + vl,
                    x1 * rt, y1, z1 * rt, (float) (i + 1) / sides * 2.0F, scroll,
                    x0 * rt, y1, z0 * rt, (float) i / sides * 2.0F, scroll, col, a * face);
        }
    }

    /** a flat ring on the ground between two radii (v runs from the inside edge to the outside) */
    private static void annulus(VertexConsumer vc, Matrix4f m, Matrix3f n, float y, float r0, float r1, float spin, float[] col, float a) {
        if (a <= 0.01F || r1 < 0.1F) {
            return;
        }
        int seg = 64;
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float u0 = (float) i / seg * 8.0F + spin, u1 = (float) (i + 1) / seg * 8.0F + spin;
            quad(vc, m, n, Mth.cos(a0) * r0, y, Mth.sin(a0) * r0, u0, 0.0F, Mth.cos(a1) * r0, y, Mth.sin(a1) * r0, u1, 0.0F,
                    Mth.cos(a1) * r1, y, Mth.sin(a1) * r1, u1, 1.0F, Mth.cos(a0) * r1, y, Mth.sin(a0) * r1, u0, 1.0F, col, a);
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

    /** additive (eyes: blend ONE ONE), so the alpha is folded into the colour */
    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float[] col, float a) {
        a = Mth.clamp(a, 0.0F, 1.0F);
        vc.vertex(m, x, y, z).color(col[0] * a, col[1] * a, col[2] * a, 1.0F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    // ------------------------------------------------------------------ what it does to whoever is watching
    /** 0..1: how hard this explosion hits the local camera right now */
    private static float felt(Boom b, float pt, float decay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0.0F;
        }
        float t = age(b, pt);
        if (t < 0.0F) {
            return 0.0F;
        }
        double d = mc.player.getEyePosition(pt).distanceTo(b.pos());
        float near = Mth.clamp(1.0F - (float) d / (90.0F * b.size()), 0.0F, 1.0F);
        return near * (float) Math.exp(-t * decay);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (BOOMS.isEmpty()) {
            return;
        }
        float pt = (float) event.getPartialTick();
        float k = 0.0F;
        float tt = 0.0F;
        for (Boom b : BOOMS) {
            float f = felt(b, pt, 1.6F);
            if (f > k) {
                k = f;
                tt = age(b, pt) * 20.0F;
            }
        }
        if (k < 0.01F) {
            return;
        }
        float amp = 5.0F * k;
        event.setYaw(event.getYaw() + amp * (Mth.sin(tt * 2.7F) * 0.6F + Mth.sin(tt * 5.3F + 1.3F) * 0.4F));
        event.setPitch(event.getPitch() + amp * (Mth.sin(tt * 3.1F + 0.7F) * 0.6F + Mth.sin(tt * 6.1F) * 0.4F));
        event.setRoll(event.getRoll() + amp * 0.8F * Mth.sin(tt * 2.2F + 2.0F));
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (BOOMS.isEmpty()) {
            return;
        }
        float pt = event.getPartialTick();
        float k = 0.0F;
        for (Boom b : BOOMS) {
            float t = age(b, pt);
            float in = t < 0.05F ? Math.max(0.0F, t / 0.05F) : 1.0F;
            k = Math.max(k, felt(b, pt, 4.5F) * in);
        }
        if (k < 0.01F) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int a = (int) (Mth.clamp(k * 0.85F, 0.0F, 1.0F) * 255.0F);
        // a violet-white flash that bleaches the screen
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xF4E6FF);
    }
}
