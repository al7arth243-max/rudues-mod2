package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.GarudaEntity;
import com.rudues.mod.entity.GarudaScene;
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
 * Garuda's scenes on the player's screen: the storm clearing on the Hero of Hell, her sweep past him; and the end
 * fight - the camera through each stage, the clash gauges (mash the attack button), and the Hero of Hell drawn where
 * the script puts him (in the sky, on top of the wind ball, at her throat).
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class GarudaSceneClient {
    private GarudaSceneClient() {
    }

    @Nullable
    private static GarudaEntity garuda;
    private static boolean on;
    private static CameraType saved;
    private static float shake, shakeO, flash, shownBalance = GarudaScene.START_BALANCE;
    private static int lastStage = GarudaScene.NONE;

    public static boolean active() {
        return on;
    }

    @Nullable
    private static GarudaEntity find(Minecraft mc, int playerId) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        if (garuda != null && garuda.isAlive() && garuda.level() == mc.level && garuda.stage() >= 0 && garuda.scenePlayerId() == playerId) {
            return garuda;
        }
        for (GarudaEntity g : mc.level.getEntitiesOfClass(GarudaEntity.class, new AABB(mc.player.blockPosition()).inflate(160))) {
            if (g.isAlive() && g.stage() >= 0 && g.scenePlayerId() == playerId) {
                return g;
            }
        }
        return null;
    }

    /** where to draw this player's Hero of Hell during the end fight (null: where the player is) */
    @Nullable
    public static Vec3 avatarAt(int playerId, float pt) {
        Minecraft mc = Minecraft.getInstance();
        GarudaEntity g = (mc.player != null && playerId == mc.player.getId()) ? garuda : find(mc, playerId);
        if (g == null || g.stage() <= GarudaScene.ARRIVE || g.scenePlayerId() != playerId) {
            return null;
        }
        return GarudaScene.heroAt(g.stage(), g.stageTime(pt), g.balance());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || event.phase != TickEvent.Phase.START) {
            return;
        }
        shakeO = shake;
        shake *= 0.86F;
        flash *= 0.82F;
        garuda = find(mc, mc.player.getId());
        boolean want = garuda != null;
        if (want && !on) {
            on = true;
            saved = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            shownBalance = GarudaScene.START_BALANCE;
        } else if (!want && on) {
            on = false;
            if (saved != null) {
                mc.options.setCameraType(saved);
            }
            saved = null;
            lastStage = GarudaScene.NONE;
        }
        if (!on) {
            return;
        }
        int st = garuda.stage();
        float t = garuda.stageTime(0);
        if (st == GarudaScene.ARRIVE && t >= 1) {
            StormClient.release();
        }
        if (st != lastStage) {
            if (st == GarudaScene.WIN1 || st == GarudaScene.LOSE1 || st == GarudaScene.HELL) {
                shake = 1.0F;
            }
            if (st == GarudaScene.STRAIN1 || st == GarudaScene.STRAIN2) {
                shake = 0.7F;
                shownBalance = garuda.balance();
            }
            lastStage = st;
        }
        if ((st == GarudaScene.WIN1 && (int) t == 8) || (st == GarudaScene.HELL && (int) t == 3) || (st == GarudaScene.LOSE1 && (int) t == 4)) {
            flash = 1.0F;
            shake = 1.2F;
        }
        if (st == GarudaScene.ARRIVE && (int) t == GarudaScene.FLY_START + 12) {
            shake = 0.8F;
        }
        int ti = (int) t;
        if (st == GarudaScene.BLAST && ti == com.rudues.mod.entity.GarudaMoveData.WIND_BLAST_IMPACT) {
            flash = Math.max(flash, 0.55F);
            shake = 1.1F;
        }
        if (st == GarudaScene.BLAST && ti == 49) {
            shake = Math.max(shake, 0.6F);
        }
        if (st == GarudaScene.SNATCH) {
            if (ti == com.rudues.mod.entity.GarudaMoveData.SNATCH_GRAB) {
                shake = Math.max(shake, 0.7F);
            }
            if (ti == com.rudues.mod.entity.GarudaMoveData.SNATCH_SLAM1 || ti == com.rudues.mod.entity.GarudaMoveData.SNATCH_SLAM2) {
                shake = 1.3F;
                flash = Math.max(flash, 0.25F);
            }
            if (ti == com.rudues.mod.entity.GarudaMoveData.SNATCH_SCREAM) {
                shake = Math.max(shake, 0.5F);
            }
        }
        if (st == GarudaScene.BALL && garuda.regrowing() && ti == com.rudues.mod.entity.GarudaMoveData.REGROW_GROW1 + 6) {
            shake = Math.max(shake, 0.6F);
        }
        if (st == GarudaScene.STRAIN1 || st == GarudaScene.STRAIN2) {
            shake = Math.max(shake, 0.22F);
        }
        if (st == GarudaScene.PUNCH && ((int) t % 12) == 3) {
            shake = Math.max(shake, 0.5F);
        }
        int n = 0;
        while (mc.options.keyAttack.consumeClick()) {
            n++;
        }
        while (mc.options.keyUse.consumeClick()) {
            // swallowed
        }
        boolean strain = st == GarudaScene.STRAIN1 || st == GarudaScene.STRAIN2;
        if (strain && t >= GarudaScene.PROMPT_DELAY && n > 0) {
            Network.CHANNEL.sendToServer(new ClashInputPacket(garuda.getId(), n));
            shownBalance = Math.min(1.0F, shownBalance + GarudaScene.PRESS * n);
        }
        shownBalance = Mth.lerp(0.35F, shownBalance, garuda.balance());
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
        garuda = null;
    }

    // ------------------------------------------------------------ the camera
    private record Shot(Vec3 eye, Vec3 look, float fov) {
    }

    @Nullable
    private static Shot shot(float pt) {
        GarudaEntity g = garuda;
        Minecraft mc = Minecraft.getInstance();
        if (!on || g == null || mc.player == null) {
            return null;
        }
        int st = g.stage();
        float t = g.stageTime(pt);
        Vec3 her = g.getPosition(pt);
        Vec3 hs = GarudaScene.heroSpot(), gs = GarudaScene.herSpot();
        Vec3 mid = hs.lerp(gs, 0.5);
        Vec3 hero = GarudaScene.heroAt(st, t, g.balance());
        if (hero == null) {
            hero = mc.player.getPosition(pt);
        }
        switch (st) {
            case GarudaScene.BLAST -> {
                int hit = com.rudues.mod.entity.GarudaMoveData.WIND_BLAST_IMPACT;
                if (t < hit) {
                    // over his left shoulder at her, the wind knotting in her hand
                    float k = (float) GarudaScene.ease(t / hit);
                    return new Shot(hs.add(-3.4 + 0.6 * k, 7.6, 7.5 - 1.5 * k), her.add(0, 4.6, 0), 58.0F - 6.0F * k);
                }
                // on him as it takes the arm, and his roar
                float k = (float) GarudaScene.ease((t - hit) / 30.0);
                return new Shot(hs.add(8.5 - 1.5 * k, 5.0 + 0.6 * k, -4.0 + 1.0 * k), hs.add(0, 4.6, 0.6), 60.0F - 4.0F * k);
            }
            case GarudaScene.SNATCH -> {
                // the two of them from the side, the frame riding up and down with them; after the throw, up at him
                Vec3 focus = hero.add(0, 3.0, 0).lerp(her.add(0, 3.5, 0), 0.5);
                if (t > com.rudues.mod.entity.GarudaMoveData.SNATCH_RELEASE) {
                    float k = (float) GarudaScene.ease((t - com.rudues.mod.entity.GarudaMoveData.SNATCH_RELEASE) / 26.0);
                    Vec3 from = focus.add(16, 2.0, 5);
                    Vec3 low = hs.add(10, 2.5, -6);
                    return new Shot(from.lerp(low, k), focus.lerp(hero.add(0, 4, 0), k), 62.0F);
                }
                double lift = Math.max(0, focus.y - hs.y - 6) * 0.35;
                return new Shot(focus.add(16, 1.5 - lift, 6), focus, 62.0F);
            }
            case GarudaScene.ARRIVE -> {
                Vec3 a = GarudaScene.heroArrive();
                if (t < GarudaScene.SCREECH) {
                    float k = (float) GarudaScene.ease(t / GarudaScene.SCREECH);
                    return new Shot(a.add(1.6 - 0.5 * k, 2.6 + 0.6 * k, -11.5 + 2.0 * k), a.add(0, 3.6 + 0.6 * k, 0), 60.0F);
                }
                if (t < GarudaScene.FLY_END) {
                    return new Shot(a.add(-3.2, 6.0, 8.5), a.add(2.0, 4.0, -14), 62.0F);
                }
                float k = (float) GarudaScene.ease((t - GarudaScene.FLY_END) / 40.0);
                return new Shot(a.add(-3.4, 6.6, 6.5), a.add(2.0, 4.6, -14).lerp(her.add(0, 5.5, 0), Math.min(1, k * 1.5)), 60.0F - 12.0F * k);
            }
            case GarudaScene.CAST -> {
                Vec3 look = her.add(0, 4, 0).lerp(hero.add(0, 4, 0), 0.5);
                return new Shot(mid.add(17, 4 + 0.3 * Math.max(0, hero.y - hs.y), 3), look, 64.0F);
            }
            case GarudaScene.BALL, GarudaScene.STRAIN1 -> {
                int closeUntil = com.rudues.mod.entity.GarudaMoveData.WINDBALL_FORM_SHOOT - 6;
                if (st == GarudaScene.BALL && g.regrowing() && t < closeUntil) {
                    // up with him: the arm burning back, the roar; she is far below gathering the ball
                    float k = (float) GarudaScene.ease(t / closeUntil);
                    return new Shot(hero.add(9.0 - 1.5 * k, 4.6 + 0.8 * k, -8.5 + 1.5 * k), hero.add(0, 4.4, 0).lerp(her.add(0, 4, 0), 0.06 * k),
                            58.0F + 4.0F * k);
                }
                double a = st == GarudaScene.STRAIN1 ? 0.18 * Math.sin(t * 0.03) : 0.0;
                Vec3 focus = GarudaScene.ballLow().lerp(hero.add(0, 2, 0), 0.5);
                return new Shot(focus.add(20 * Math.cos(a), 1.0, 20 * Math.sin(a)), focus, 60.0F);
            }
            case GarudaScene.WIN1 -> {
                float k = (float) GarudaScene.ease(t / 30.0);
                return new Shot(mid.add(21, 9 - 3 * k, 4), gs.add(0, 3, 0).lerp(mid.add(0, 4, 0), 0.4 * k), 62.0F);
            }
            case GarudaScene.LOSE1 -> {
                return new Shot(mid.add(18, 8, 6), hero.add(0, 3, 0), 62.0F);
            }
            case GarudaScene.GRAB, GarudaScene.PUNCH -> {
                return new Shot(hero.add(-4.5, 7.4, 6.0), her.add(0, 4.4, 0), 58.0F);
            }
            case GarudaScene.KICK, GarudaScene.STRAIN2, GarudaScene.LOSE2 -> {
                double a = st == GarudaScene.STRAIN2 ? 0.2 * Math.sin(t * 0.03) : 0.0;
                Vec3 focus = hero.lerp(her, 0.5).add(0, 4.5, 0);
                return new Shot(focus.add(11 * Math.cos(a), 1.2, 11 * Math.sin(a)), focus, 58.0F);
            }
            case GarudaScene.HELL -> {
                float k = (float) GarudaScene.ease(t / 40.0);
                return new Shot(hero.add(9 + 6 * k, 6 + 3 * k, 6 + 4 * k), hero.add(0, 4, 0).lerp(her.add(0, 3, 0), 0.3 + 0.6 * k), 62.0F);
            }
            default -> {
                return null;
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
            jx = Mth.sin(tt * 2.9F) * 0.35 * sk;
            jy = Mth.sin(tt * 3.7F + 1.0F) * 0.3 * sk;
            jz = Mth.sin(tt * 3.1F + 2.0F) * 0.35 * sk;
            event.setRoll(Mth.sin(tt * 2.3F) * 2.5F * sk);
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
        GarudaEntity gd = garuda;
        if (!on || gd == null || mc.level == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = event.getPartialTick();
        int st = gd.stage();
        float t = gd.stageTime(pt);
        int bar = (int) (h * 0.085F);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (st == GarudaScene.ARRIVE && t < 36) {
            // the storm clears
            float k = 1.0F - Mth.clamp(t / 36.0F, 0.0F, 1.0F);
            g.fill(0, 0, w, h, ((int) (k * 255) << 24) | StormClient.WHITE);
        }
        if (st == GarudaScene.ARRIVE && t > GarudaScene.FLY_END + 30 && t < GarudaScene.ARRIVE_END) {
            float a = Mth.clamp((t - GarudaScene.FLY_END - 30) / 8.0F, 0, 1) * Mth.clamp((GarudaScene.ARRIVE_END - t) / 6.0F, 0, 1);
            int al = Math.max(4, (int) (a * 255));
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(w / 2.0F, h * 0.72F, 0);
            ps.scale(2.4F, 2.4F, 1.0F);
            g.drawCenteredString(mc.font, Component.translatable("entity.rudues.garuda"), 0, 0, (al << 24) | 0xB8FFCC);
            ps.popPose();
            g.drawCenteredString(mc.font, Component.translatable("gui.rudues.garuda_title"), w / 2, (int) (h * 0.72F) + 26, (al << 24) | 0x9AB8A4);
        }
        if (flash > 0.02F) {
            int a = (int) (Mth.clamp(flash, 0, 1) * 220);
            g.fill(0, 0, w, h, (a << 24) | 0xF4E8FF);
        }
        boolean strain = st == GarudaScene.STRAIN1 || st == GarudaScene.STRAIN2;
        if (!strain || t < GarudaScene.PROMPT_DELAY) {
            return;
        }
        int cx = w / 2, cy = h / 2;
        float now = mc.level.getGameTime() + pt;
        for (int i = 0; i < 3; i++) {
            float ph = (now * 0.06F + i / 3.0F) % 1.0F;
            ring(g, cx, cy, 20 + (1 - ph) * 100, 0xD8A8FF, (int) (220 * ph));
        }
        ring(g, cx, cy, 20, 0xF0E0FF, 255);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy - 52, 0);
        ps.scale(2.0F, 2.0F, 1.0F);
        Component title = Component.translatable(st == GarudaScene.STRAIN1 ? "message.rudues.garuda_clash1" : "message.rudues.garuda_clash2");
        g.drawString(mc.font, title, -mc.font.width(title) / 2, 0, 0xF0E0FF, true);
        ps.popPose();
        Component mash = Component.translatable("message.rudues.clash_mash", mc.options.keyAttack.getTranslatedKeyMessage());
        boolean blink = ((int) (now / 3)) % 2 == 0;
        g.drawString(mc.font, mash, cx - mc.font.width(mash) / 2, cy + 34, blink ? 0xFFFFFF : 0xD8A8FF, true);
        float b = Mth.clamp(shownBalance, 0, 1);
        int bw = 220, bx = cx - bw / 2, by = cy + 52;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + 8, 0xC0000000);
        int split = bx + (int) (bw * b);
        g.fill(bx, by, split, by + 6, 0xFFA846FF);
        g.fill(split, by, bx + bw, by + 6, 0xFF3FCF7A);
        g.fill(split - 1, by - 3, split + 1, by + 9, 0xFFFFFFFF);
        float left = 1.0F - t / GarudaScene.STRAIN_MAX;
        g.fill(bx, by + 10, bx + (int) (bw * Mth.clamp(left, 0, 1)), by + 11, 0x90F0E0FF);
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
