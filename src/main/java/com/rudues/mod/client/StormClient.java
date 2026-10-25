package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.KnightScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Benedikta's storm: a wall of wind that wheels round the keep, closing in on you as she screams in the sky, while
 * the fog thickens and everything goes white. The white is held across the move into the crater, until Garuda's
 * scene there takes over.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class StormClient {
    private StormClient() {
    }

    public static final int WHITE = 0xE6ECE8;
    private static long holdUntil;

    /** keep the screen white for a few seconds (the dimension change), unless the crater scene clears it */
    public static void holdWhite() {
        holdUntil = System.currentTimeMillis() + 7000L;
    }

    public static void release() {
        holdUntil = 0L;
    }

    public static boolean holding() {
        return System.currentTimeMillis() < holdUntil;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float t = KnightSceneClient.stormTime(0);
        if (t < 0 || mc.level == null || mc.player == null) {
            return;
        }
        // debris whirling in the wall
        float r = radius(t);
        Vec3 o = KnightScene.origin();
        for (int i = 0; i < 12; i++) {
            double a = mc.level.random.nextDouble() * Math.PI * 2, y = o.y - 20 + mc.level.random.nextDouble() * 60;
            double rr = r * (0.85 + 0.3 * mc.level.random.nextDouble());
            double vx = -Math.sin(a) * 1.4, vz = Math.cos(a) * 1.4;
            mc.level.addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD, o.x + Math.cos(a) * rr, y, o.z + Math.sin(a) * rr, vx, 0.1, vz);
        }
        for (int i = 0; i < 6; i++) {
            double a = mc.level.random.nextDouble() * Math.PI * 2, rr = 2 + mc.level.random.nextDouble() * 8;
            mc.level.addParticle(ParticleTypes.CLOUD, mc.player.getX() + Math.cos(a) * rr, mc.player.getY() + mc.level.random.nextDouble() * 4,
                    mc.player.getZ() + Math.sin(a) * rr, -Math.sin(a) * 0.9, 0.05, Math.cos(a) * 0.9);
        }
    }

    /** the wall's radius: far out round the castle, closing on the keep */
    private static float radius(float t) {
        float k = Mth.clamp(t / KnightScene.STORM_WHITE, 0.0F, 1.0F);
        return Mth.lerp(k * k * (3 - 2 * k), 110.0F, 9.0F);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        float pt = event.getPartialTick();
        float t = KnightSceneClient.stormTime(pt);
        if (t < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        Vec3 o = KnightScene.origin();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(o.x - cam.x, o.y - 70 - cam.y, o.z - cam.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderType rt = RenderType.eyes(WindRenderers.STREAK);
        VertexConsumer vc = buf.getBuffer(rt);
        float r = radius(t);
        float age = t;
        float k = Mth.clamp(t / 20.0F, 0.0F, 1.0F);
        int layers = 14;
        float height = 190.0F;
        for (int i = 0; i < layers; i++) {
            float h0 = height * i / layers, h1 = height * (i + 1.3F) / layers;
            // the funnel flares as it climbs
            float r0 = r * (0.8F + 0.5F * h0 / height), r1 = r * (0.8F + 0.5F * h1 / height);
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(-age * (9.0F + 1.5F * i)));
            float kk = k * (0.45F + 0.15F * Mth.sin(age * 0.2F + i));
            WindRenderers.band(ps, vc, r0, r1, h0, h1, 40, age * 0.03F + i * 0.31F, 6.0F, kk, 0, 0, 0, 0);
            WindRenderers.band(ps, vc, r0 * 1.12F, r1 * 1.12F, h0, h1, 40, -age * 0.02F + i * 0.17F, 4.0F, kk * 0.5F, 0, 0, 0, 0);
            ps.popPose();
        }
        ps.popPose();
        buf.endBatch(rt);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onFog(ViewportEvent.RenderFog event) {
        float t = KnightSceneClient.stormTime((float) event.getPartialTick());
        if (t < 0 && !holding()) {
            return;
        }
        float k = t < 0 ? 1.0F : Mth.clamp(t / KnightScene.STORM_WHITE, 0.0F, 1.0F);
        float far = Mth.lerp(k, Math.min(160.0F, event.getFarPlaneDistance()), 10.0F);
        event.setNearPlaneDistance(far * 0.05F);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float t = KnightSceneClient.stormTime((float) event.getPartialTick());
        if (t < 0 && !holding()) {
            return;
        }
        float k = t < 0 ? 1.0F : Mth.clamp(t / KnightScene.STORM_WHITE, 0.0F, 1.0F);
        event.setRed(Mth.lerp(k, event.getRed(), 0.86F));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.9F));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.88F));
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (holding() && !KnightSceneClient.active()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (!holding() || KnightSceneClient.active() || GarudaSceneClient.active()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFF000000 | WHITE);
    }
}
