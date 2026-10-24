package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.AwakenData;
import com.rudues.mod.entity.ClashScript;
import com.rudues.mod.network.AwakenPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * The awakening, for the player in it: the scripted body position (sword's length, the punch, the flight, where they
 * land) and a cinematic camera - side-on for the exchange, close on the punch, tracking the fall, over the Shadow's
 * shoulder as they get up, low in front of them as the corruption tears out, behind them for the roar.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class AwakenClient {
    private AwakenClient() {
    }

    private static final Map<Integer, AwakenPacket> STATES = new HashMap<>();
    @Nullable
    private static CameraType savedCamera;
    private static boolean cinematic;
    private static float shake, shakeO, flash;

    public static void receive(AwakenPacket m) {
        Minecraft mc = Minecraft.getInstance();
        boolean mine = mc.player != null && m.playerId == mc.player.getId();
        if (!m.active) {
            STATES.remove(m.shadowId);
            if (mine) {
                end();
            }
            return;
        }
        STATES.put(m.shadowId, m);
        if (mine) {
            if (!cinematic) {
                cinematic = true;
                savedCamera = mc.options.getCameraType();
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);     // so the stand-in is drawn
            flash = 0.6F;
        }
    }

    private static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (cinematic && savedCamera != null) {
            mc.options.setCameraType(savedCamera);
        }
        cinematic = false;
        savedCamera = null;
    }

    @Nullable
    private static AwakenPacket mine() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        for (AwakenPacket s : STATES.values()) {
            if (s.playerId == mc.player.getId()) {
                return s;
            }
        }
        return null;
    }

    private static float time(AwakenPacket s, float pt) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (mc.level.getGameTime() - s.start) + pt;
    }

    private static float sample(float[] a, float t) {
        if (t <= 0) {
            return a[0];
        }
        int i = (int) t;
        if (i >= a.length - 1) {
            return a[a.length - 1];
        }
        return Mth.lerp(t - i, a[i], a[i + 1]);
    }

    private static Vec3 origin(AwakenPacket s) {
        return new Vec3(s.ox, s.oy, s.oz);
    }

    private static Vec3 playerPos(AwakenPacket s, float t) {
        Vec3 u = ClashScript.axis(s.yaw);
        return origin(s).add(u.scale(sample(AwakenData.PLAYER_ALONG, t))).add(0, sample(AwakenData.PLAYER_H, t), 0);
    }

    private static void kick(float s, float f) {
        shake = Math.max(shake, s);
        flash = Math.max(flash, f);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        AwakenPacket s = mine();
        if (event.phase == TickEvent.Phase.START) {
            shakeO = shake;
            shake *= 0.82F;
            flash *= 0.8F;
            STATES.values().removeIf(st -> time(st, 0) > AwakenData.END + 60);
            if (s == null) {
                if (cinematic) {
                    end();
                }
                return;
            }
            while (mc.options.keyAttack.consumeClick()) {
                // swallowed: it is a cutscene
            }
            while (mc.options.keyUse.consumeClick()) {
                // swallowed
            }
            int t = (int) time(s, 0);
            for (int c : AwakenData.CONTACTS) {
                if (t == c) {
                    kick(0.35F, 0.25F);
                }
            }
            if (t == AwakenData.PUNCH) {
                kick(1.0F, 0.5F);
            } else if (t == AwakenData.LAND) {
                kick(0.7F, 0.0F);
            } else if (t == AwakenData.ARM || t == AwakenData.LEGS) {
                kick(0.5F, 0.2F);
            } else if (t == AwakenData.ROAR) {
                kick(1.3F, 0.7F);
            }
            return;
        }
        if (s == null) {
            return;
        }
        // the body goes where the script says (the frames in between interpolate)
        Vec3 q = playerPos(s, time(s, 1.0F));
        float yaw = s.yaw + 180.0F;
        mc.player.setPos(q.x, q.y, q.z);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.fallDistance = 0;
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setYHeadRot(yaw);
        mc.player.yHeadRotO = yaw;
        mc.player.setYBodyRot(yaw);
        mc.player.yBodyRotO = yaw;
        mc.player.setXRot(0.0F);
        mc.player.xRotO = 0.0F;
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (mine() != null) {
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
        STATES.clear();
        end();
    }

    // ------------------------------------------------------------ the camera
    private record Shot(Vec3 eye, Vec3 look, float fov) {
    }

    private static float smooth(float u) {
        u = Mth.clamp(u, 0.0F, 1.0F);
        return u * u * (3.0F - 2.0F * u);
    }

    @Nullable
    private static Shot shot(float pt) {
        AwakenPacket s = mine();
        if (s == null || !cinematic) {
            return null;
        }
        float t = time(s, pt);
        Vec3 o = origin(s), u = ClashScript.axis(s.yaw), up = new Vec3(0, 1, 0);
        Vec3 side = new Vec3(u.z, 0, -u.x);
        Vec3 p = playerPos(s, t);
        if (t < AwakenData.PUNCH - 5) {
            // side-on to the exchange, drifting in
            float k = smooth(t / (AwakenData.PUNCH - 5));
            Vec3 mid = o.add(u.scale(1.1));
            return new Shot(mid.add(side.scale(4.4 - 0.6 * k)).add(up.scale(1.5)).add(u.scale(-0.3)), mid.add(up.scale(1.25)), 58);
        }
        if (t < AwakenData.PUNCH + 3) {
            // low and close on the punch
            return new Shot(o.add(u.scale(0.7)).add(side.scale(2.4)).add(up.scale(0.9)), o.add(u.scale(1.4)).add(up.scale(1.2)), 52);
        }
        if (t < AwakenData.LAND + 9) {
            // wide, following them as they fly and land
            return new Shot(o.add(u.scale(3.6)).add(side.scale(7.5)).add(up.scale(2.4)), p.add(up.scale(0.6)), 60);
        }
        if (t < 112) {
            // over the Shadow's shoulder as they get up
            return new Shot(o.add(u.scale(-1.6)).add(side.scale(-1.0)).add(up.scale(2.3)), p.add(up.scale(0.7)), 50);
        }
        if (t < AwakenData.ROAR - 2) {
            // low, in front of them, pushing in as the corruption tears out
            float k = smooth((t - 112) / (AwakenData.ROAR - 2 - 112));
            return new Shot(p.add(u.scale(-(3.2 - 0.9 * k))).add(side.scale(1.2)).add(up.scale(1.0)), p.add(up.scale(1.2)), 62);
        }
        if (t < AwakenData.ROAR + 20) {
            // behind them: the roar rolls out over the Shadow
            return new Shot(p.add(u.scale(3.2)).add(side.scale(2.6)).add(up.scale(2.0)), o.add(up.scale(1.2)), 66);
        }
        Vec3 mid = o.lerp(p, 0.5);
        return new Shot(mid.add(side.scale(9.0)).add(up.scale(2.6)), mid.add(up.scale(1.2)), 62);
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
        float sk = Mth.lerp(pt, shakeO, shake);
        Minecraft mc = Minecraft.getInstance();
        double jx = 0, jy = 0, jz = 0;
        if (sk > 0.01F && mc.level != null) {
            float tt = mc.level.getGameTime() + pt;
            jx = Mth.sin(tt * 2.9F) * 0.3 * sk;
            jy = Mth.sin(tt * 3.7F + 1.0F) * 0.25 * sk;
            jz = Mth.sin(tt * 3.1F + 2.0F) * 0.3 * sk;
            event.setRoll(Mth.sin(tt * 2.3F) * 2.5F * sk);
        }
        Vec3 eye = sh.eye().add(jx, jy, jz);
        Vec3 delta = eye.subtract(camera.getPosition());
        Quaternionf rot = new Quaternionf().rotationYXZ(-camera.getYRot() * Mth.DEG_TO_RAD, camera.getXRot() * Mth.DEG_TO_RAD, 0.0F);
        Vector3f f = new Vector3f(0.0F, 0.0F, 1.0F).rotate(rot);
        Vector3f uu = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rot);
        Vector3f l = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rot);
        try {
            move.invoke(camera,
                    delta.x * f.x() + delta.y * f.y() + delta.z * f.z(),
                    delta.x * uu.x() + delta.y * uu.y() + delta.z * uu.z(),
                    delta.x * l.x() + delta.y * l.y() + delta.z * l.z());
        } catch (Exception ignored) {
            return;
        }
        Vec3 dir = sh.look().subtract(eye);
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        event.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        event.setPitch((float) Math.toDegrees(-Math.atan2(dir.y, horiz)));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        Shot sh = shot((float) event.getPartialTick());
        if (sh != null) {
            event.setFOV(sh.fov() - 5.0F * Mth.lerp((float) event.getPartialTick(), shakeO, shake));
        }
    }

    // ------------------------------------------------------------ the HUD: letterbox only
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (cinematic && mine() != null && !event.getOverlay().id().equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())
                && !event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())
                && !event.getOverlay().id().equals(VanillaGuiOverlay.TITLE_TEXT.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        AwakenPacket s = mine();
        if (s == null || !cinematic || mc.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = (int) (h * 0.085F);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (flash > 0.02F) {
            int a = (int) (Mth.clamp(flash, 0, 1) * 180);
            g.fill(0, 0, w, h, (a << 24) | 0xF0E0FF);
        }
        float t = time(s, event.getPartialTick());
        if (t > 120 && t < 200) {
            Component line = Component.translatable("message.rudues.awakening");
            float a = Mth.clamp(Math.min((t - 120) / 10.0F, (200 - t) / 10.0F), 0, 1);
            if (a < 0.06F) {
                return;         // (text with almost no alpha is drawn opaque)
            }
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(w / 2.0F, h - bar - 18, 0);
            g.drawString(mc.font, line, -mc.font.width(line) / 2, 0, ((int) (a * 255) << 24) | 0xD8A8FF, true);
            ps.popPose();
        }
    }
}
