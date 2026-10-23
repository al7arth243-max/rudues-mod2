package com.rudues.mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.Set;

/**
 * Client side of the Shadow's purple fire: which entities are burning (told by the server), purple flames drawn
 * around them like vanilla burning, embers coming off them, and purple flames licking up the screen when it is you.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class ClientShadowFire {
    /** 8 frames of 16x16, stacked */
    private static final ResourceLocation FIRE = new ResourceLocation(RuduesMod.MODID, "textures/effect/purple_fire.png");
    private static final int FRAMES = 8;
    private static final Set<Integer> BURNING = new HashSet<>();

    private ClientShadowFire() {
    }

    public static void set(int entityId, boolean burning) {
        if (burning) {
            BURNING.add(entityId);
        } else {
            BURNING.remove(entityId);
        }
    }

    public static boolean isBurning(Entity entity) {
        return BURNING.contains(entity.getId());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BURNING.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null || BURNING.isEmpty() || mc.isPaused()) {
            return;
        }
        BURNING.removeIf(id -> {
            Entity e = mc.level.getEntity(id);
            if (e == null) {
                return true;
            }
            if (mc.level.random.nextInt(3) == 0 && !(e == mc.player && mc.options.getCameraType().isFirstPerson())) {
                mc.level.addParticle(mc.level.random.nextInt(3) == 0 ? ModParticles.PURPLE_EMBER.get() : ModParticles.PURPLE_FLAME.get(),
                        e.getRandomX(0.6), e.getY() + mc.level.random.nextDouble() * e.getBbHeight(), e.getRandomZ(0.6), 0, 0.02, 0);
            }
            return false;
        });
    }

    /** the flames, the same shape as vanilla's burning overlay */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        Entity e = event.getEntity();
        if (!isBurning(e) || e.isInvisible()) {
            return;
        }
        PoseStack ps = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        ps.pushPose();
        float scale = e.getBbWidth() * 1.4F;
        ps.scale(scale, scale, scale);
        float halfW = 0.5F, height = e.getBbHeight() / scale, y = 0.0F, z = 0.0F;
        ps.mulPose(Axis.YP.rotationDegrees(-Minecraft.getInstance().gameRenderer.getMainCamera().getYRot()));
        ps.translate(0.0F, 0.0F, -0.3F + (int) height * 0.02F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(FIRE));
        PoseStack.Pose pose = ps.last();
        int base = (int) ((e.tickCount + event.getPartialTick()) / 2.0F);
        for (int i = 0; height > 0.0F; i++) {
            int frame = (base + i * 3) % FRAMES;
            float v0 = (float) frame / FRAMES, v1 = (float) (frame + 1) / FRAMES;
            float u0 = 0.0F, u1 = 1.0F;
            if (i / 2 % 2 == 0) {
                u0 = 1.0F;
                u1 = 0.0F;
            }
            fireVertex(vc, pose, halfW, -y, z, u1, v1);
            fireVertex(vc, pose, -halfW, -y, z, u0, v1);
            fireVertex(vc, pose, -halfW, 1.4F - y, z, u0, v0);
            fireVertex(vc, pose, halfW, 1.4F - y, z, u1, v0);
            height -= 0.45F;
            y -= 0.45F;
            halfW *= 0.9F;
            z += 0.03F;
        }
        ps.popPose();
    }

    private static void fireVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        vc.vertex(m, x, y, z).color(1.0F, 1.0F, 1.0F, 0.95F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** first person: purple flames rising from the bottom corners of the screen */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isBurning(mc.player) || !mc.options.getCameraType().isFirstPerson() || mc.player.isSpectator()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int fw = w / 2 + w / 8, fh = (int) (h * 0.5F);
        int frame = (int) ((mc.player.tickCount + event.getPartialTick()) / 2.0F) % FRAMES;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.7F);
        g.blit(FIRE, -w / 16, h - fh, fw, fh, 0.0F, frame * 16.0F, 16, 16, 16, 16 * FRAMES);
        g.blit(FIRE, w - fw + w / 16, h - fh, fw, fh, 0.0F, ((frame + 3) % FRAMES) * 16.0F, 16, 16, 16, 16 * FRAMES);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
