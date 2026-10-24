package com.rudues.mod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.AwakenData;
import com.rudues.mod.entity.ClashScript;
import com.rudues.mod.entity.FinalData;
import com.rudues.mod.entity.InfernalShadowEntity;
import com.rudues.mod.network.AwakenPacket;
import com.rudues.mod.network.ClashInputPacket;
import com.rudues.mod.network.Network;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * The Shadow's scenes, for the player in them: the scripted body position and a cinematic camera for the awakening
 * (the exchange, the punch, the fall, the knee and the breath, the arm catching fire, the fist held up through the QTE,
 * the corruption) and for the last clash (the run, the struggle, the punch to its face with an impact frame, its
 * fall and transformation), plus both QTEs' HUD.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class AwakenClient {
    private AwakenClient() {
    }

    private static final Map<Integer, AwakenPacket> STATES = new HashMap<>();
    private static final Map<Integer, Float> SHOWN = new HashMap<>();
    @Nullable
    private static CameraType savedCamera;
    private static boolean cinematic;

    /** the awakening / final clash cinematic owns the camera */
    public static boolean cinematicActive() {
        return cinematic;
    }
    private static float shake, shakeO, flash;
    /** the impact frame: client ticks left of it */
    private static int impact;

    public static void receive(AwakenPacket m) {
        Minecraft mc = Minecraft.getInstance();
        boolean mine = mc.player != null && m.playerId == mc.player.getId();
        if (!m.active) {
            STATES.remove(m.shadowId);
            SHOWN.remove(m.shadowId);
            if (mine) {
                end();
            }
            return;
        }
        AwakenPacket old = STATES.put(m.shadowId, m);
        boolean changed = old == null || old.phase != m.phase || old.start != m.start;
        if (changed || m.phase != InfernalShadowEntity.P_STRAIN) {
            SHOWN.put(m.shadowId, m.gauge);
        }
        if (mine && changed) {
            if (scripted(m)) {
                if (!cinematic) {
                    cinematic = true;
                    savedCamera = mc.options.getCameraType();
                }
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);     // so the stand-in is drawn
            } else {
                end();
            }
            if (m.phase == InfernalShadowEntity.P_A || m.phase == InfernalShadowEntity.P_CHARGE) {
                kick(0.0F, 0.6F);
            } else if (m.phase == InfernalShadowEntity.P_STRAIN) {
                kick(1.0F, 0.8F);
            } else if (m.phase == InfernalShadowEntity.P_B) {
                kick(0.6F, 0.7F);
            }
        }
    }

    private static boolean scripted(AwakenPacket s) {
        return s.phase != InfernalShadowEntity.P_WAIT;
    }

    private static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (cinematic && savedCamera != null) {
            mc.options.setCameraType(savedCamera);
        }
        cinematic = false;
        savedCamera = null;
        impact = 0;
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

    private static float last(float[] a) {
        return a[a.length - 1];
    }

    private static Vec3 origin(AwakenPacket s) {
        return new Vec3(s.ox, s.oy, s.oz);
    }

    private static Vec3 along(AwakenPacket s, double a, double h) {
        return origin(s).add(ClashScript.axis(s.yaw).scale(a)).add(0, h, 0);
    }

    /** the player's place: mirrors InfernalShadowEntity.scenePlayerPos */
    private static Vec3 playerPos(AwakenPacket s, float t) {
        if (s.scene == InfernalShadowEntity.SCENE_AWAKEN) {
            if (s.phase == InfernalShadowEntity.P_A) {
                return along(s, sample(AwakenData.PLAYER_ALONG, t), sample(AwakenData.PLAYER_H, t));
            }
            return along(s, last(AwakenData.PLAYER_ALONG), 0);
        }
        return switch (s.phase) {
            case InfernalShadowEntity.P_CHARGE -> along(s, sample(FinalData.CHARGE_D, t), 0);
            case InfernalShadowEntity.P_WIN -> along(s, sample(FinalData.WIN_D, t), 0);
            case InfernalShadowEntity.P_LOSE -> along(s, sample(FinalData.LOSE_D, t), sample(FinalData.LOSE_H, t));
            case InfernalShadowEntity.P_TRANSFORM -> along(s, last(FinalData.WIN_D), 0);
            default -> along(s, FinalData.DC, 0);
        };
    }

    private static Vec3 shadowPos(AwakenPacket s, float t) {
        if (s.scene == InfernalShadowEntity.SCENE_AWAKEN) {
            return origin(s);
        }
        return switch (s.phase) {
            case InfernalShadowEntity.P_CHARGE -> along(s, -sample(FinalData.CHARGE_D, t), 0);
            case InfernalShadowEntity.P_WIN -> along(s, -sample(FinalData.FALL_D, t), sample(FinalData.FALL_H, t));
            case InfernalShadowEntity.P_LOSE -> along(s, -sample(FinalData.WIN_D, t), 0);
            case InfernalShadowEntity.P_TRANSFORM -> along(s, -last(FinalData.FALL_D), 0);
            default -> along(s, -FinalData.DC, 0);
        };
    }

    private static boolean qte(AwakenPacket s) {
        return s.phase == InfernalShadowEntity.P_HOLD || s.phase == InfernalShadowEntity.P_STRAIN;
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
            if (impact > 0) {
                impact--;
            }
            STATES.values().removeIf(st -> time(st, 0) > 20 * 60 * 2);
            if (s == null || !scripted(s)) {
                if (cinematic) {
                    end();
                }
                return;
            }
            int n = 0;
            while (mc.options.keyAttack.consumeClick()) {
                n++;
            }
            while (mc.options.keyUse.consumeClick()) {
                // swallowed: it is a cutscene
            }
            int t = (int) time(s, 0);
            if (n > 0 && qte(s) && t >= ClashScript.PROMPT_DELAY) {
                Network.CHANNEL.sendToServer(new ClashInputPacket(s.shadowId, n));
                Float b = SHOWN.get(s.shadowId);
                SHOWN.put(s.shadowId, Math.min(1.0F, (b == null ? s.gauge : b) + 0.02F * n));
            }
            impacts(s, t);
            return;
        }
        if (s == null || !scripted(s)) {
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
        // ease the shown gauge toward the server's
        Float b = SHOWN.get(s.shadowId);
        SHOWN.put(s.shadowId, b == null ? s.gauge : Mth.lerp(0.35F, b, s.gauge));
    }

    /** the shakes and flashes on the beats */
    private static void impacts(AwakenPacket s, int t) {
        if (s.scene == InfernalShadowEntity.SCENE_AWAKEN) {
            if (s.phase == InfernalShadowEntity.P_A) {
                for (int c : AwakenData.CONTACTS) {
                    if (t == c) {
                        kick(0.35F, 0.25F);
                    }
                }
                if (t == AwakenData.PUNCH) {
                    kick(1.0F, 0.5F);
                } else if (t == AwakenData.LAND) {
                    kick(0.7F, 0.0F);
                } else if (t == AwakenData.IGNITE) {
                    kick(0.3F, 0.3F);
                }
            } else if (s.phase == InfernalShadowEntity.P_B) {
                if (t == AwakenData.ARM || t == AwakenData.LEGS) {
                    kick(0.5F, 0.2F);
                } else if (t == AwakenData.ROAR) {
                    kick(1.3F, 0.7F);
                }
            }
            return;
        }
        if (s.phase == InfernalShadowEntity.P_STRAIN) {
            // the brawl's blows
            int bt = t % FinalData.BRAWL_LEN;
            for (int i = 0; i < FinalData.BRAWL_T.length; i++) {
                if (FinalData.BRAWL_T[i] == bt) {
                    switch (FinalData.BRAWL_KIND[i]) {
                        case 0 -> kick(0.65F, 0.12F);
                        case 1 -> kick(0.35F, 0.05F);
                        case 3 -> kick(0.9F, 0.35F);
                        case 4 -> kick(0.5F, 0.0F);
                        default -> {
                        }
                    }
                }
            }
        } else if (s.phase == InfernalShadowEntity.P_WIN) {
            if (t == FinalData.IMPACT) {
                impact = 4;                 // the impact frame
                kick(1.6F, 0.0F);
            } else if (t == FinalData.FALL_LAND) {
                kick(0.8F, 0.0F);
            }
        } else if (s.phase == InfernalShadowEntity.P_LOSE && t == FinalData.IMPACT) {
            kick(1.2F, 0.5F);
        } else if (s.phase == InfernalShadowEntity.P_TRANSFORM && t == InfernalShadowEntity.TRANSFORM_TICKS - 10) {
            kick(1.0F, 0.6F);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        AwakenPacket s = mine();
        if (s != null && scripted(s)) {
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
        SHOWN.clear();
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
        if (s == null || !cinematic || !scripted(s)) {
            return null;
        }
        float t = time(s, pt);
        Vec3 o = origin(s), u = ClashScript.axis(s.yaw), up = new Vec3(0, 1, 0);
        Vec3 side = new Vec3(u.z, 0, -u.x);          // the player's right (they face -u)
        Vec3 p = playerPos(s, t), sh = shadowPos(s, t);
        if (s.scene == InfernalShadowEntity.SCENE_AWAKEN) {
            return awakenShot(s, t, o, u, side, up, p);
        }
        Vec3 m = o.add(up.scale(FinalData.FIST_U));
        switch (s.phase) {
            case InfernalShadowEntity.P_CHARGE -> {
                if (t < 20) {
                    // both of them, side-on, the fire gathering
                    return new Shot(o.add(side.scale(10.0)).add(up.scale(2.2)), o.add(up.scale(1.2)), 62);
                }
                // low and close as they run in
                float k = smooth((t - 20) / (FinalData.MEET - 20));
                return new Shot(o.add(side.scale(5.5 - 1.5 * k)).add(up.scale(0.7)), m, 64);
            }
            case InfernalShadowEntity.P_STRAIN -> {
                // the brawl: wide enough for both, drifting round them
                float a = 0.45F * Mth.sin(t * 0.03F);
                Vec3 sd = side.scale(Mth.cos(a)).add(u.scale(Mth.sin(a)));
                Vec3 look = o.add(up.scale(1.15));
                return new Shot(look.add(sd.scale(4.6)).add(up.scale(0.5 + 0.2 * Mth.sin(t * 0.05F))), look, 60);
            }
            case InfernalShadowEntity.P_WIN -> {
                if (t < FinalData.IMPACT + FinalData.HITSTOP + 2) {
                    // right on its face as the fist lands
                    Vec3 face = sh.add(up.scale(1.55));
                    return new Shot(face.add(side.scale(1.9)).add(u.scale(0.9)).add(up.scale(0.1)), face, 46);
                }
                if (t < FinalData.FALL_LAND + 8) {
                    // wide, as it is thrown back
                    return new Shot(o.add(side.scale(9.0)).add(u.scale(-3.0)).add(up.scale(2.4)), sh.add(up.scale(0.8)), 62);
                }
                // in front of it, low: it gets up onto one knee
                return new Shot(sh.add(u.scale(3.4)).add(side.scale(1.4)).add(up.scale(0.8)), sh.add(up.scale(0.9)), 56);
            }
            case InfernalShadowEntity.P_TRANSFORM -> {
                // pulling back as it burns black and the Hero rises
                float k = smooth(t / InfernalShadowEntity.TRANSFORM_TICKS);
                return new Shot(sh.add(u.scale(3.4 + 7.0 * k)).add(side.scale(1.4 + 2.0 * k)).add(up.scale(0.8 + 4.0 * k)),
                        sh.add(up.scale(0.9 + 2.5 * k)), 56 + 8 * k);
            }
            default -> {
                // knocked down: wide
                Vec3 mid = sh.lerp(p, 0.5);
                return new Shot(mid.add(side.scale(10.0)).add(up.scale(3.0)), mid.add(up.scale(1.2)), 62);
            }
        }
    }

    private static Shot awakenShot(AwakenPacket s, float t, Vec3 o, Vec3 u, Vec3 side, Vec3 up, Vec3 p) {
        switch (s.phase) {
            case InfernalShadowEntity.P_A -> {
                if (t < AwakenData.PUNCH - 5) {
                    float k = smooth(t / (AwakenData.PUNCH - 5));
                    Vec3 mid = o.add(u.scale(1.1));
                    return new Shot(mid.add(side.scale(4.4 - 0.6 * k)).add(up.scale(1.5)).add(u.scale(-0.3)), mid.add(up.scale(1.25)), 58);
                }
                if (t < AwakenData.PUNCH + 3) {
                    return new Shot(o.add(u.scale(0.7)).add(side.scale(2.4)).add(up.scale(0.9)), o.add(u.scale(1.4)).add(up.scale(1.2)), 52);
                }
                if (t < AwakenData.LAND + 9) {
                    return new Shot(o.add(u.scale(3.6)).add(side.scale(7.5)).add(up.scale(2.4)), p.add(up.scale(0.6)), 60);
                }
                if (t < AwakenData.KNEEL) {
                    // over the Shadow's shoulder as they get up
                    return new Shot(o.add(u.scale(-1.6)).add(side.scale(-1.0)).add(up.scale(2.3)), p.add(up.scale(0.7)), 50);
                }
                if (t < AwakenData.IGNITE) {
                    // close, low, beside them on one knee: the breaths
                    return new Shot(p.add(side.scale(2.0)).add(u.scale(-0.9)).add(up.scale(0.75)), p.add(up.scale(0.85)), 50);
                }
                if (t < AwakenData.STOOD + 6) {
                    // low by the left arm as it catches fire and they stand
                    float k = smooth((t - AwakenData.IGNITE) / (AwakenData.STOOD + 6 - AwakenData.IGNITE));
                    return new Shot(p.add(side.scale(-1.15)).add(u.scale(-0.95)).add(up.scale(0.45 + 0.15 * k)),
                            p.add(side.scale(-0.45)).add(up.scale(0.85 + 0.15 * k)), 50);
                }
                // the burning fist raised before the bowed face
                return fistShot(p, u, side, up, smooth((t - AwakenData.STOOD - 6) / (AwakenData.END_A - AwakenData.STOOD - 6)) * 0.2F);
            }
            case InfernalShadowEntity.P_HOLD -> {
                return fistShot(p, u, side, up, 0.2F + 0.35F * smooth(t / 120.0F));
            }
            default -> {
                if (t < AwakenData.ROAR - 2) {
                    float k = smooth(t / (AwakenData.ROAR - 2));
                    return new Shot(p.add(u.scale(-(3.2 - 0.9 * k))).add(side.scale(1.2)).add(up.scale(1.0)), p.add(up.scale(1.2)), 62);
                }
                if (t < AwakenData.ROAR + 20) {
                    return new Shot(p.add(u.scale(3.2)).add(side.scale(2.6)).add(up.scale(2.0)), o.add(up.scale(1.2)), 66);
                }
                Vec3 mid = o.lerp(p, 0.5);
                return new Shot(mid.add(side.scale(9.0)).add(up.scale(2.6)), mid.add(up.scale(1.2)), 62);
            }
        }
    }

    /** the fist before the face, from the front; push = how far the camera has crept in */
    private static Shot fistShot(Vec3 p, Vec3 u, Vec3 side, Vec3 up, float push) {
        Vec3 face = p.add(up.scale(1.5)).add(u.scale(-0.15));
        return new Shot(face.add(u.scale(-(1.7 - push))).add(side.scale(-0.3)).add(up.scale(0.05)), face.add(side.scale(-0.08)), 44);
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

    // ------------------------------------------------------------ the HUD
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
        if (s == null || !cinematic || mc.options.hideGui || !scripted(s)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = event.getPartialTick();
        if (impact > 0) {
            impactFrame(g, w, h, impact);
        }
        int bar = (int) (h * 0.085F);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (flash > 0.02F) {
            int a = (int) (Mth.clamp(flash, 0, 1) * 180);
            g.fill(0, 0, w, h, (a << 24) | 0xF0E0FF);
        }
        float t = time(s, pt);
        if (s.phase == InfernalShadowEntity.P_LOSE && t < 40) {
            Component banner = Component.translatable("message.rudues.clash_lost");
            PoseStack bp = g.pose();
            bp.pushPose();
            bp.translate(w / 2.0F, h * 0.22F, 0);
            bp.scale(2.5F, 2.5F, 1.0F);
            g.drawString(mc.font, banner, -mc.font.width(banner) / 2, 0, 0xFF6A5A, true);
            bp.popPose();
        }
        if (!qte(s) || t < ClashScript.PROMPT_DELAY) {
            return;
        }
        boolean awaken = s.phase == InfernalShadowEntity.P_HOLD;
        int cx = w / 2, cy = (int) (h * (awaken ? 0.62F : 0.5F));
        float now = mc.level.getGameTime() + pt;
        for (int i = 0; i < 3; i++) {
            float ph = (now * 0.055F + i / 3.0F) % 1.0F;
            ring(g, cx, cy, 20 + (1 - ph) * 105, awaken ? 0xC08CFF : 0xFFD27A, (int) (230 * ph));
        }
        ring(g, cx, cy, 20, 0xFFF2C0, 255);
        ring(g, cx, cy, 17, 0xFFF2C0, 160);
        if (!awaken) {
            float hold = Mth.clamp(s.hold, 0, 1);
            int n = 64;
            for (int i = 0; i < (int) (n * hold); i++) {
                double a = -Math.PI / 2 + Math.PI * 2 * i / n;
                for (float rr = 21.5F; rr <= 25.5F; rr += 1.0F) {
                    int x = cx + (int) Math.round(Math.cos(a) * rr), y = cy + (int) Math.round(Math.sin(a) * rr);
                    g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFC08CFF);
                }
            }
        }
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy - 52, 0);
        ps.scale(2.0F, 2.0F, 1.0F);
        Component title = Component.translatable(awaken ? "message.rudues.awaken_qte" : "message.rudues.final_clash");
        g.drawString(mc.font, title, -mc.font.width(title) / 2, 0, awaken ? 0xD8A8FF : 0xFFE9B0, true);
        ps.popPose();
        Float shownB = SHOWN.get(s.shadowId);
        float b = Mth.clamp(shownB == null ? s.gauge : shownB, 0, 1);
        Component mash = !awaken && b >= InfernalShadowEntity.LINE ? Component.translatable("message.rudues.clash_hold")
                : Component.translatable("message.rudues.clash_mash", mc.options.keyAttack.getTranslatedKeyMessage());
        boolean blink = ((int) (now / 3)) % 2 == 0;
        g.drawString(mc.font, mash, cx - mc.font.width(mash) / 2, cy + 34, blink ? 0xFFFFFF : 0xFFD27A, true);
        int bw = 220, bx = cx - bw / 2, by = cy + 52;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + 8, 0xC0000000);
        int split = bx + (int) (bw * b);
        g.fill(bx, by, split, by + 6, 0xFFA846FF);
        g.fill(split, by, bx + bw, by + 6, awaken ? 0xFF2A1040 : 0xFF4E1290);
        g.fill(split - 1, by - 3, split + 1, by + 9, 0xFFFFFFFF);
        int line = bx + (int) (bw * InfernalShadowEntity.LINE);
        g.fill(line, by - 5, line + 1, by + 11, 0xFFFFE9B0);
        if (!awaken) {
            float left = 1.0F - t / ClashScript.STRAIN_TICKS_PUNCH;
            g.fill(bx, by + 10, bx + (int) (bw * Mth.clamp(left, 0, 1)), by + 11, 0x90FFE9B0);
        }
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

    /**
     * The impact frame: for a moment the picture is inverted and slashed with speed lines converging on the punch,
     * then a white flash as it releases.
     */
    private static void impactFrame(GuiGraphics g, int w, int h, int left) {
        g.flush();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        if (left >= 2) {
            // everything inverted
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            b.vertex(m, 0, h, 0).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            b.vertex(m, w, h, 0).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            b.vertex(m, w, 0, 0).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            b.vertex(m, 0, 0, 0).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
            BufferUploader.drawWithShader(b.end());
            RenderSystem.defaultBlendFunc();
        }
        // speed lines: thin dark wedges from the edges toward the middle
        float cx = w * 0.5F, cy = h * 0.47F, outer = (float) Math.hypot(w, h) * 0.6F;
        java.util.Random rnd = new java.util.Random(left * 7919L);
        float shade = left >= 2 ? 0.0F : 1.0F;
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 46; i++) {
            double a = Math.PI * 2 * (i + rnd.nextFloat() * 0.7) / 46;
            double half = 0.012 + rnd.nextFloat() * 0.02;
            float inner = (0.22F + rnd.nextFloat() * 0.2F) * Math.min(w, h);
            float alpha = left >= 2 ? 0.85F : 0.5F;
            b.vertex(m, (float) (cx + Math.cos(a - half) * outer), (float) (cy + Math.sin(a - half) * outer), 0).color(shade, shade, shade, alpha).endVertex();
            b.vertex(m, (float) (cx + Math.cos(a + half) * outer), (float) (cy + Math.sin(a + half) * outer), 0).color(shade, shade, shade, alpha).endVertex();
            b.vertex(m, (float) (cx + Math.cos(a) * inner), (float) (cy + Math.sin(a) * inner), 0).color(shade, shade, shade, 0.0F).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        if (left == 1) {
            g.fill(0, 0, w, h, 0x90FFFFFF);
        }
    }
}
