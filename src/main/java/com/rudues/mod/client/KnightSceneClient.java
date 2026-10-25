package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.FeatheredKnightEntity;
import com.rudues.mod.entity.KnightScene;
import com.rudues.mod.network.ClashInputPacket;
import com.rudues.mod.network.Network;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/**
 * Benedikta's Semi Prime scene on the player's screen: letterbox, the camera through each stage (handing over to the
 * Semi Prime power-up's own camera while the player transforms), white flashes on the big hits, and the clash: mash the
 * attack button to keep the burning uppercut ahead of her push.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class KnightSceneClient {
    private KnightSceneClient() {
    }

    @Nullable
    private static FeatheredKnightEntity knight;
    private static CameraType saved;
    private static boolean on;
    private static float shake, shakeO, flash;
    private static int lastStage = KnightScene.NONE;
    private static float shownBalance = KnightScene.START_BALANCE;

    public static boolean active() {
        return on;
    }

    /** ticks into the storm that takes the castle (her defeat), or -1 */
    public static float stormTime(float pt) {
        FeatheredKnightEntity k = knight;
        if (!on || k == null) {
            return -1.0F;
        }
        if (k.sceneStage() == KnightScene.D_STORM) {
            return k.sceneTime(pt);
        }
        return -1.0F;
    }

    @Nullable
    private static FeatheredKnightEntity find(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        if (knight != null && knight.isAlive() && knight.level() == mc.level && knight.sceneStage() >= 0 && knight.scenePlayerId() == mc.player.getId()) {
            return knight;
        }
        for (FeatheredKnightEntity k : mc.level.getEntitiesOfClass(FeatheredKnightEntity.class, new AABB(mc.player.blockPosition()).inflate(96))) {
            if (k.isAlive() && k.sceneStage() >= 0 && k.scenePlayerId() == mc.player.getId()) {
                return k;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            shakeO = shake;
            shake *= 0.86F;
            flash *= 0.8F;
            knight = find(mc);
            boolean want = knight != null;
            if (want && !on) {
                on = true;
                saved = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                flash = 1.0F;
                shownBalance = KnightScene.START_BALANCE;
            } else if (!want && on) {
                on = false;
                if (saved != null) {
                    mc.options.setCameraType(saved);
                }
                saved = null;
                lastStage = KnightScene.NONE;
            }
            if (!on) {
                return;
            }
            int stage = knight.sceneStage();
            if (stage != lastStage) {
                if (stage == KnightScene.WIN || stage == KnightScene.LOSE) {
                    flash = 0.9F;
                    shake = 1.0F;
                }
                if (stage == KnightScene.STRAIN) {
                    shake = 0.8F;
                }
                lastStage = stage;
            }
            float t = knight.sceneTime(0);
            if (stage == KnightScene.TRANSFORM && (int) t == 26) {
                shake = Math.max(shake, 0.7F);
                flash = Math.max(flash, 0.5F);
            }
            if (stage == KnightScene.STRAIN) {
                shake = Math.max(shake, 0.18F);
            }
            if (stage == KnightScene.D_STORM) {
                shake = Math.max(shake, Math.min(1.0F, 0.2F + t / 90.0F));
                if (t >= KnightScene.STORM_WHITE) {
                    StormClient.holdWhite();
                }
            }
            // the button only counts in the clash; any other time in the scene it does nothing
            int n = 0;
            while (mc.options.keyAttack.consumeClick()) {
                n++;
            }
            while (mc.options.keyUse.consumeClick()) {
                // swallowed
            }
            if (stage == KnightScene.STRAIN && t >= KnightScene.PROMPT_DELAY && n > 0) {
                Network.CHANNEL.sendToServer(new ClashInputPacket(knight.getId(), n));
                shownBalance = Math.min(1.0F, shownBalance + KnightScene.PRESS * n);
            }
            shownBalance = Mth.lerp(0.35F, shownBalance, knight.balance());
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (on) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
            event.getInput().up = false;
            event.getInput().down = false;
            event.getInput().left = false;
            event.getInput().right = false;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        on = false;
        saved = null;
        knight = null;
    }

    // ------------------------------------------------------------ the camera
    private record Shot(Vec3 eye, Vec3 look, float fov) {
    }

    @Nullable
    private static Shot shot(float pt) {
        FeatheredKnightEntity k = knight;
        if (!on || k == null) {
            return null;
        }
        int stage = k.sceneStage();
        float t = k.sceneTime(pt);
        Vec3 o = KnightScene.origin(), her = k.getPosition(pt);
        Vec3 c = KnightScene.contact();
        switch (stage) {
            case KnightScene.TRANSFORM -> {
                // in front of her, a little to the side, pushing in as the wings burst out
                float kk = (float) KnightScene.ease(t / KnightScene.length(stage));
                Vec3 s = KnightScene.start();
                Vec3 eye = s.add(2.6 - 0.7 * kk, 1.0 + 0.3 * kk, -3.3 + 0.9 * kk);
                return new Shot(eye, s.add(0, 1.15 + 0.35 * kk, 0), 58.0F);
            }
            case KnightScene.ASCEND -> {
                // over the player's shoulder, tilting up after her as she climbs
                Vec3 eye = o.add(-1.4, 1.75, -2.6);
                return new Shot(eye, her.add(0, 0.6, 0), 66.0F);
            }
            case KnightScene.PLAYER -> {
                return null;        // the Semi Prime power-up's own camera
            }
            case KnightScene.DIVE -> {
                // low behind the player: she comes down out of the sky at them
                Vec3 eye = o.add(1.9, 0.5, -3.4);
                return new Shot(eye, c.lerp(her.add(0, 1.0, 0), 0.7), 72.0F);
            }
            case KnightScene.STRAIN -> {
                double a = 0.22 * Math.sin(t * 0.03);
                Vec3 eye = o.add(7.6 * Math.cos(a), 1.5, 0.9 + 7.6 * Math.sin(a));
                return new Shot(eye, c.add(0, 1.0, 0.8), 54.0F);
            }
            case KnightScene.WIN -> {
                float kk = (float) KnightScene.ease(t / 30.0);
                Vec3 eye = o.add(8.5 + 3.0 * kk, 2.0 + 2.5 * kk, 0.6 + 3.5 * kk);
                return new Shot(eye, c.add(0, 1.0, 0.8).lerp(her.add(0, 0.8, 0), 0.25 + 0.6 * kk), 60.0F);
            }
            case KnightScene.LOSE -> {
                Vec3 eye = o.add(5.6, 1.1, -1.6);
                return new Shot(eye, o.add(0, 0.9, 0.6), 60.0F);
            }
            case KnightScene.D_FALL -> {
                return new Shot(o.add(6.5, 2.2, 4.0), her.add(0, 0.8, 0), 58.0F);
            }
            case KnightScene.D_RISE -> {
                float kk = (float) KnightScene.ease(t / KnightScene.length(stage));
                Vec3 s = KnightScene.start();
                return new Shot(s.add(1.8 - 0.4 * kk, 0.55 + 0.4 * kk, -3.3 + 0.8 * kk), s.add(0, 0.8 + 0.6 * kk, 0), 54.0F);
            }
            case KnightScene.D_ASCEND -> {
                return new Shot(o.add(-1.2, 1.7, -2.4), her.add(0, 1.0, 0), 66.0F);
            }
            case KnightScene.D_STORM -> {
                double a = t * 0.025;
                Vec3 eye = o.add(Math.sin(a) * 4.0, 1.6, -Math.cos(a) * 4.0);
                return new Shot(eye, her.add(0, 1.0, 0).lerp(o.add(0, 12, 0), 0.3), 74.0F);
            }
            default -> {
                Vec3 eye = o.add(-1.3, 1.8, -2.8);
                return new Shot(eye, her.add(0, 1.2, 0), 62.0F);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float pt = (float) event.getPartialTick();
        Shot sh = shot(pt);
        if (sh == null) {
            return;
        }
        Method move = ClashClient.findCameraMove();
        if (move == null) {
            return;
        }
        Camera camera = event.getCamera();
        Minecraft mc = Minecraft.getInstance();
        float sk = Mth.lerp(pt, shakeO, shake);
        double jx = 0, jy = 0, jz = 0;
        if (sk > 0.01F && mc.level != null) {
            float tt = mc.level.getGameTime() + pt;
            jx = Mth.sin(tt * 2.9F) * 0.25 * sk;
            jy = Mth.sin(tt * 3.7F + 1.0F) * 0.22 * sk;
            jz = Mth.sin(tt * 3.1F + 2.0F) * 0.25 * sk;
            event.setRoll(Mth.sin(tt * 2.3F) * 2.0F * sk);
        } else {
            event.setRoll(0.0F);
        }
        Vec3 eye = sh.eye().add(jx, jy, jz);
        Vec3 delta = eye.subtract(camera.getPosition());
        Quaternionf rot = new Quaternionf().rotationYXZ(-camera.getYRot() * Mth.DEG_TO_RAD, camera.getXRot() * Mth.DEG_TO_RAD, 0.0F);
        Vector3f f = new Vector3f(0.0F, 0.0F, 1.0F).rotate(rot);
        Vector3f u = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rot);
        Vector3f l = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rot);
        try {
            move.invoke(camera,
                    delta.x * f.x() + delta.y * f.y() + delta.z * f.z(),
                    delta.x * u.x() + delta.y * u.y() + delta.z * u.z(),
                    delta.x * l.x() + delta.y * l.y() + delta.z * l.z());
        } catch (Exception ignored) {
            return;
        }
        Vec3 dir = sh.look().subtract(sh.eye());
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        event.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        event.setPitch((float) Math.toDegrees(-Math.atan2(dir.y, horiz)));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        Shot sh = shot((float) event.getPartialTick());
        if (sh != null) {
            event.setFOV(sh.fov());
        }
    }

    // ------------------------------------------------------------ the screen
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (on) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        FeatheredKnightEntity k = knight;
        if (!on || k == null || mc.level == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = event.getPartialTick();
        int bar = (int) (h * 0.085F);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (flash > 0.02F) {
            int a = (int) (Mth.clamp(flash, 0, 1) * 220);
            g.fill(0, 0, w, h, (a << 24) | 0xF2FFF4);
        }
        int stage = k.sceneStage();
        float t = k.sceneTime(pt);
        if (stage == KnightScene.D_STORM && t > 56) {
            // the storm closes in white
            float wk = Mth.clamp((t - 56) / (KnightScene.STORM_WHITE - 56.0F), 0.0F, 1.0F);
            g.fill(0, 0, w, h, ((int) (wk * wk * 255) << 24) | StormClient.WHITE);
        }
        if ((stage == KnightScene.WIN || stage == KnightScene.LOSE) && t < 40) {
            boolean won = stage == KnightScene.WIN;
            Component banner = Component.translatable(won ? "message.rudues.rubble_won" : "message.rudues.rubble_lost");
            PoseStack bp = g.pose();
            bp.pushPose();
            bp.translate(w / 2.0F, h * 0.22F, 0);
            bp.scale(2.5F, 2.5F, 1.0F);
            g.drawString(mc.font, banner, -mc.font.width(banner) / 2, 0, won ? 0xE6C8FF : 0xA8FFC8, true);
            bp.popPose();
        }
        if (stage != KnightScene.STRAIN || t < KnightScene.PROMPT_DELAY) {
            return;
        }
        int cx = w / 2, cy = h / 2;
        float now = mc.level.getGameTime() + pt;
        for (int i = 0; i < 3; i++) {
            float ph = (now * 0.06F + i / 3.0F) % 1.0F;
            ring(g, cx, cy, 20 + (1 - ph) * 100, 0xB6FFD0, (int) (220 * ph));
        }
        ring(g, cx, cy, 20, 0xE8FFF0, 255);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy - 52, 0);
        ps.scale(2.0F, 2.0F, 1.0F);
        Component title = Component.translatable("message.rudues.rubble_clash");
        g.drawString(mc.font, title, -mc.font.width(title) / 2, 0, 0xE8FFF0, true);
        ps.popPose();
        Component mash = Component.translatable("message.rudues.clash_mash", mc.options.keyAttack.getTranslatedKeyMessage());
        boolean blink = ((int) (now / 3)) % 2 == 0;
        g.drawString(mc.font, mash, cx - mc.font.width(mash) / 2, cy + 34, blink ? 0xFFFFFF : 0xB6FFD0, true);
        // the tug of war: your burning purple against her green wind
        float b = Mth.clamp(shownBalance, 0, 1);
        int bw = 220, bx = cx - bw / 2, by = cy + 52;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + 8, 0xC0000000);
        int split = bx + (int) (bw * b);
        g.fill(bx, by, split, by + 6, 0xFFA846FF);
        g.fill(split, by, bx + bw, by + 6, 0xFF3FCF7A);
        g.fill(split - 1, by - 3, split + 1, by + 9, 0xFFFFFFFF);
        float left = 1.0F - t / KnightScene.STRAIN_MAX;
        g.fill(bx, by + 10, bx + (int) (bw * Mth.clamp(left, 0, 1)), by + 11, 0x90E8FFF0);
    }

    private static void ring(GuiGraphics g, int cx, int cy, float r, int rgb, int alpha) {
        if (alpha <= 4) {
            return;
        }
        int n = Math.max(24, (int) (r * 1.6F));
        int col = (Mth.clamp(alpha, 0, 255) << 24) | rgb;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x - 1, y - 1, x + 1, y + 1, col);
        }
    }
}
