package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.KnightIntro;
import com.rudues.mod.network.IntroPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/**
 * The Feathered Knight's entrance, on the player's screen: a fade up from black, letterbox bars, and three shots -
 * side-on as you walk out to the middle of the roof (the camera drifting round to find her sat on the far wall),
 * close over her right shoulder as she looks back at you, and low beside where she lands, her in the foreground and
 * you beyond. Your own client walks you (so it is smooth); the server holds you to the path.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class CastleIntroClient {
    private CastleIntroClient() {
    }

    private static boolean on;
    private static long start;
    private static CameraType saved;

    public static boolean active() {
        return on;
    }

    public static void receive(IntroPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.on) {
            if (!on) {
                saved = mc.options.getCameraType();
            }
            on = true;
            start = m.start;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            stop();
        }
    }

    private static void stop() {
        if (on && saved != null) {
            Minecraft.getInstance().options.setCameraType(saved);
        }
        on = false;
        saved = null;
    }

    private static float time(float pt) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (float) (mc.level.getGameTime() - start) + pt;
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    // ------------------------------------------------------------ the walk
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !on) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !p.isAlive()) {
            stop();
            return;
        }
        float t = time(0.0F);
        if (t > KnightIntro.RELEASE + 2) {
            stop();
            return;
        }
        if (t >= KnightIntro.PLACE + 2) {
            // where the walk will be by the next tick (rendering eases between the two)
            double z = KnightIntro.walkZ(t + 1.0F);
            p.setPos(KnightIntro.WALK_X, KnightIntro.Y, z);
            p.setDeltaMovement(Vec3.ZERO);
            p.setYRot(KnightIntro.PLAYER_YAW);
            p.setXRot(0.0F);
            p.yHeadRot = KnightIntro.PLAYER_YAW;
            p.yBodyRot = KnightIntro.PLAYER_YAW;
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
    }

    // ------------------------------------------------------------ the camera
    private record Shot(Vec3 eye, Vec3 look, float fov) {
    }

    private static Vec3 v(double[] a) {
        return new Vec3(a[0], a[1], a[2]);
    }

    private static Shot shot(float pt) {
        Minecraft mc = Minecraft.getInstance();
        float t = time(pt);
        Vec3 pl = mc.player.getPosition(pt);
        Vec3 chest = pl.add(0, 1.1, 0);
        Vec3 head = v(KnightIntro.head(t));
        if (t < KnightIntro.SHOT_B) {
            // A: side-on, keeping pace with the walk; late in it the camera swings round behind your shoulder and finds
            // her, sat on the far wall
            float k = smooth((t - 40.0F) / 62.0F);
            Vec3 eye = new Vec3(pl.x + 8.0 - 5.0 * k, KnightIntro.Y + 1.5 + 0.4 * k, pl.z + 0.6 - 4.6 * k);
            return new Shot(eye, chest.lerp(head, 0.5 * k), 64.0F - 10.0F * k);
        }
        if (t < KnightIntro.SHOT_C) {
            // B: close, from the arena side, by the shoulder she looks back over - the back of her head, then her face
            float k = smooth((t - KnightIntro.SHOT_B) / (float) (KnightIntro.SHOT_C - KnightIntro.SHOT_B));
            double s = 1.0 - 0.16 * k;
            Vec3 eye = head.add(1.1 * s, 0.75, -1.3 * s);
            return new Shot(eye, head.add(0, -0.25, 0), 52.0F);
        }
        // C: low beside where she lands - she drops into the foreground, you beyond her
        float k = smooth((t - KnightIntro.SHOT_C) / (float) (KnightIntro.RELEASE - KnightIntro.SHOT_C));
        Vec3 land = new Vec3(KnightIntro.LAND_X, KnightIntro.Y, KnightIntro.LAND_Z);
        double jolt = 0.0;
        if (t > KnightIntro.LAND && t < KnightIntro.LAND + 8) {
            jolt = -0.06 * Math.sin((t - KnightIntro.LAND) * 1.6) * Math.exp(-(t - KnightIntro.LAND) * 0.4);
        }
        Vec3 eye = land.add(2.0 - 0.2 * k, 0.45 + 0.25 * k + jolt, 2.3 - 0.3 * k);
        Vec3 look = land.add(0, 1.0, 0).lerp(chest, 0.35).add(0, 0.12 * k, 0);
        return new Shot(eye, look, 64.0F);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.player == null || mc.level == null) {
            return;
        }
        float pt = (float) event.getPartialTick();
        if (time(pt) < KnightIntro.PLACE + 2) {
            return;     // still black
        }
        Method move = ClashClient.findCameraMove();
        if (move == null) {
            return;
        }
        Shot sh = shot(pt);
        Camera camera = event.getCamera();
        Vec3 delta = sh.eye().subtract(camera.getPosition());
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
        event.setRoll(0.0F);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (on && mc.player != null && time((float) event.getPartialTick()) >= KnightIntro.PLACE + 2) {
            event.setFOV(shot((float) event.getPartialTick()).fov());
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
        if (!on || mc.level == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float t = time(event.getPartialTick());
        // letterbox, in at the start and away as the fight begins
        float bars = Math.min(smooth(t / 20.0F), smooth((KnightIntro.RELEASE - t) / 10.0F));
        int bar = (int) (h * 0.1F * bars);
        if (bar > 0) {
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        // her name, as she rises from the landing
        float name = Math.min(smooth((t - KnightIntro.LAND - 6) / 10.0F), smooth((KnightIntro.RELEASE - t) / 8.0F));
        if (name > 0.02F) {
            int a = Math.max(4, (int) (name * 255));
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h * 0.70F, 0);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.drawCenteredString(mc.font, Component.translatable("entity.rudues.feathered_knight"), 0, 0, (a << 24) | 0xC8FFD8);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h * 0.70F + 22, 0);
            g.drawCenteredString(mc.font, Component.translatable("gui.rudues.knight_title"), 0, 0, (a << 24) | 0xA0B8A8);
            g.pose().popPose();
        }
        // the fade up from black
        float black = t < KnightIntro.PLACE + 2 ? 1.0F : 1.0F - smooth((t - KnightIntro.PLACE - 2) / (KnightIntro.FADE + 6.0F - KnightIntro.PLACE));
        if (black > 0.0F) {
            g.fill(0, 0, w, h, ((int) (black * 255) << 24));
        }
    }
}
