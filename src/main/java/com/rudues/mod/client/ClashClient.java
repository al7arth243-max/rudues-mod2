package com.rudues.mod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.ClashData;
import com.rudues.mod.entity.ClashScript;
import com.rudues.mod.network.ClashInputPacket;
import com.rudues.mod.network.ClashPacket;
import com.rudues.mod.network.Network;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Client side of the end fight's clashes. Everyone near sees both fighters exactly where the clash script puts them;
 * the player in the clash also gets the cinematic: the camera angles of the reference fight (a wide side shot as they
 * charge, a close profile while the fists are locked, over the winner's shoulder as the punch drives through, the
 * loser from the front as it is launched), letterbox bars, the button-mash prompt, and the locked beams.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class ClashClient {
    private static final ResourceLocation ORB_PLAYER = new ResourceLocation(RuduesMod.MODID, "textures/entity/shadow_fireball.png");
    private static final ResourceLocation ORB_HERO = new ResourceLocation(RuduesMod.MODID, "textures/entity/hero_fireball.png");
    private static final Map<Integer, ClashPacket> STATES = new HashMap<>();
    private static final Map<Integer, Float> SHOWN_BALANCE = new HashMap<>();
    @Nullable
    private static CameraType savedCamera;
    private static boolean cinematic;
    private static int lastKey = -1;
    private static float shake, shakeO, flash;
    private static Method cameraMove;
    private static boolean lookedUp;

    private ClashClient() {
    }

    // ------------------------------------------------------------ state
    public static void receive(ClashPacket m) {
        Minecraft mc = Minecraft.getInstance();
        boolean mine = mc.player != null && m.playerId == mc.player.getId();
        if (!m.active) {
            STATES.remove(m.heroId);
            SHOWN_BALANCE.remove(m.heroId);
            if (mine) {
                endCinematic();
            }
            return;
        }
        ClashPacket old = STATES.put(m.heroId, m);
        int key = m.stage * 100 + m.phase * 10 + m.result;
        boolean changed = old == null || old.start != m.start || old.phase != m.phase || old.stage != m.stage;
        if (mine && changed) {
            lastKey = key;
            if (m.phase == ClashScript.PH_WAIT) {
                endCinematic();
            } else {
                startCinematic();
            }
            if (m.phase == ClashScript.PH_STRAIN && m.stage == ClashScript.STAGE_PUNCH1) {
                kick(1.0F, 0.85F);             // the fists meet
            } else if (m.phase == ClashScript.PH_RESOLVE) {
                kick(1.2F, 0.7F);
            } else if (m.phase == ClashScript.PH_CHARGE) {
                kick(0.0F, 0.6F);              // the cut to the wide shot
            }
        }
        if (old == null || m.phase != ClashScript.PH_STRAIN) {
            SHOWN_BALANCE.put(m.heroId, m.balance);
        }
    }

    /** clash 2's impacts, felt through the camera */
    private static void poundKicks(ClashPacket s, int t) {
        switch (s.phase) {
            case ClashScript.PH_CHARGE -> {
                if (t == ClashData.POUND_CHARGE_HIT) {
                    kick(1.2F, 0.8F);           // the uppercut
                } else if (t == ClashData.POUND_CHARGE_HERO_DOWN) {
                    kick(0.9F, 0.0F);           // it hits the ground
                } else if (t == ClashData.POUND_CHARGE_MOUNT) {
                    kick(0.5F, 0.0F);
                }
            }
            case ClashScript.PH_STRAIN -> {
                int k = t % 20;
                if (k == ClashData.POUND_HIT_A || k == ClashData.POUND_HIT_B) {
                    kick(0.45F, 0.12F);
                }
            }
            case ClashScript.PH_RESOLVE -> {
                if (s.result == ClashScript.RESULT_WIN) {
                    if (t == ClashData.POUND_WIN_SLAM) {
                        kick(1.3F, 0.7F);
                    } else if (t == ClashData.POUND_WIN_LAND) {
                        kick(0.9F, 0.0F);
                    }
                } else if (t == ClashData.POUND_LOSE_KICK) {
                    kick(1.1F, 0.4F);
                }
            }
            default -> {
            }
        }
    }

    private static void kick(float s, float f) {
        shake = Math.max(shake, s);
        flash = Math.max(flash, f);
    }

    @Nullable
    public static ClashPacket stateFor(int heroId) {
        return STATES.get(heroId);
    }

    @Nullable
    static ClashPacket mine() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        for (ClashPacket s : STATES.values()) {
            if (s.playerId == mc.player.getId()) {
                return s;
            }
        }
        return null;
    }

    public static boolean cinematicActive() {
        return cinematic;
    }

    /** the script owns the fighters' positions in this phase */
    public static boolean scripted(ClashPacket s) {
        return s.phase != ClashScript.PH_WAIT;
    }

    static float time(ClashPacket s, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (mc.level.getGameTime() - s.start) + partialTick;
    }

    private static Vec3 origin(ClashPacket s) {
        return new Vec3(s.ox, s.oy, s.oz);
    }

    public static Vec3 heroPos(ClashPacket s, float partialTick) {
        float t = time(s, partialTick);
        float d = ClashScript.dist(true, s.stage, s.phase, s.result, t, s.dHero, s.dPlayer);
        return ClashScript.at(origin(s), ClashScript.axis(s.yaw), true, d, ClashScript.height(true, s.stage, s.phase, s.result, t));
    }

    public static Vec3 playerPos(ClashPacket s, float partialTick) {
        float t = time(s, partialTick);
        float d = ClashScript.dist(false, s.stage, s.phase, s.result, t, s.dHero, s.dPlayer);
        return ClashScript.at(origin(s), ClashScript.axis(s.yaw), false, d, ClashScript.height(false, s.stage, s.phase, s.result, t));
    }

    private static void startCinematic() {
        Minecraft mc = Minecraft.getInstance();
        if (!cinematic) {
            cinematic = true;
            savedCamera = mc.options.getCameraType();
        }
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);     // so the player's own Titan is drawn
    }

    private static void endCinematic() {
        Minecraft mc = Minecraft.getInstance();
        if (cinematic && savedCamera != null) {
            mc.options.setCameraType(savedCamera);
        }
        cinematic = false;
        savedCamera = null;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
        SHOWN_BALANCE.clear();
        endCinematic();
    }

    // ------------------------------------------------------------ input and the player's body
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        ClashPacket s = mine();
        if (event.phase == TickEvent.Phase.START) {
            shakeO = shake;
            shake *= 0.82F;
            flash *= 0.8F;
            STATES.values().removeIf(st -> mc.level.getEntity(st.heroId) == null && time(st, 0) > 200);
            if (s == null || !scripted(s)) {
                return;
            }
            // presses count during the struggle; any other time the button does nothing
            int n = 0;
            while (mc.options.keyAttack.consumeClick()) {
                n++;
            }
            while (mc.options.keyUse.consumeClick()) {
                // swallowed
            }
            float t = time(s, 0);
            if (n > 0 && s.phase == ClashScript.PH_STRAIN && t >= ClashScript.PROMPT_DELAY) {
                Network.CHANNEL.sendToServer(new ClashInputPacket(s.heroId, n));
                Float b = SHOWN_BALANCE.get(s.heroId);
                SHOWN_BALANCE.put(s.heroId, Math.min(1.0F, (b == null ? s.balance : b) + 0.02F * n));
            }
            if (s.stage == ClashScript.STAGE_BEAM && s.phase == ClashScript.PH_CHARGE && (int) t == ClashData.BEAM_CLASH_FIRE) {
                kick(1.1F, 0.6F);               // both beams leave the jaws
            }
            if (s.stage == ClashScript.STAGE_POUND) {
                poundKicks(s, (int) t);
            }
            return;
        }
        if (s == null || !scripted(s)) {
            return;
        }
        // end of tick: put the player where the script says next (the frames in between interpolate smoothly)
        Vec3 q = playerPos(s, 1.0F);
        Vec3 u = ClashScript.axis(s.yaw);
        float yaw = ClashScript.yawOf(u.scale(-1));
        mc.player.setPos(q.x, q.y, q.z);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setYHeadRot(yaw);
        mc.player.yHeadRotO = yaw;
        mc.player.setYBodyRot(yaw);
        mc.player.yBodyRotO = yaw;
        mc.player.setXRot(0.0F);
        mc.player.xRotO = 0.0F;
        // ease the shown struggle toward the server's
        Float b = SHOWN_BALANCE.get(s.heroId);
        float target = s.phase == ClashScript.PH_RESOLVE ? (s.result == ClashScript.RESULT_WIN ? 1.0F : 0.0F) : s.balance;
        SHOWN_BALANCE.put(s.heroId, b == null ? target : Mth.lerp(0.35F, b, target));
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        ClashPacket s = mine();
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

    // ------------------------------------------------------------ the camera
    private record Shot(Vec3 eye, Vec3 look, float fov) {
    }

    @Nullable
    private static Shot shot(float partialTick) {
        ClashPacket s = mine();
        if (s == null || !scripted(s) || !cinematic) {
            return null;
        }
        float t = time(s, partialTick);
        Vec3 o = origin(s), u = ClashScript.axis(s.yaw), up = new Vec3(0, 1, 0);
        Vec3 side = new Vec3(u.z, 0, -u.x);                   // with the camera on this side the player is on the left
        Vec3 h = heroPos(s, partialTick), p = playerPos(s, partialTick);
        boolean beam = s.stage == ClashScript.STAGE_BEAM;
        if (s.stage == ClashScript.STAGE_POUND) {
            return poundShot(s, t, o, u, side, up, h, p);
        }
        switch (s.phase) {
            case ClashScript.PH_CHARGE -> {
                if (beam) {
                    float k = Mth.clamp(t / ClashData.BEAM_CLASH_END, 0, 1);
                    return new Shot(o.add(side.scale(19 - 3 * k)).add(up.scale(1.3)), o.add(up.scale(3.6)), 66 - 6 * k);
                }
                float k = Mth.clamp(t / ClashScript.chargeTicks(s.stage), 0, 1);
                return new Shot(o.add(side.scale(15.5 - 3.0 * k)).add(up.scale(2.0)).add(u.scale(-0.8)), o.add(up.scale(3.2)), 66 - 5 * k);
            }
            case ClashScript.PH_STRAIN -> {
                float a = 0.22F * Mth.sin(t * 0.035F);
                Vec3 sd = side.scale(Mth.cos(a)).add(u.scale(Mth.sin(a)));
                if (beam) {
                    Vec3 m = meet(s, partialTick);
                    return new Shot(m.add(sd.scale(9.0)).add(up.scale(0.8)), m, 56);
                }
                Vec3 c = o.add(up.scale(ClashData.FIST_HEIGHT + 0.4));
                return new Shot(o.add(sd.scale(6.6)).add(up.scale(2.3)), c, 58);
            }
            default -> {
                boolean win = s.result == ClashScript.RESULT_WIN;
                if (win && beam && t >= 14) {
                    return new Shot(h.add(u.scale(12.0)).add(side.scale(3.0)).add(up.scale(4.5)), h.add(up.scale(2.4)), 60);
                }
                if (win) {
                    // over the player's right shoulder, the punch driving through
                    return new Shot(p.add(u.scale(4.8)).add(side.scale(2.9)).add(up.scale(6.3)), h.add(up.scale(3.0)), 70);
                }
                return new Shot(h.add(u.scale(-4.8)).add(side.scale(-2.9)).add(up.scale(6.3)), p.add(up.scale(3.0)), 70);
            }
        }
    }

    /** clash 2: tracking the rush, low under the uppercut, wide on the fall; under the pounding; the slam, the throw */
    private static Shot poundShot(ClashPacket s, float t, Vec3 o, Vec3 u, Vec3 side, Vec3 up, Vec3 h, Vec3 p) {
        Vec3 face = o.add(u.scale(ClashData.POUND_FACE_ALONG));
        switch (s.phase) {
            case ClashScript.PH_CHARGE -> {
                int hit = ClashData.POUND_CHARGE_HIT;
                if (t < hit - 5) {
                    // alongside the player as it runs in
                    Vec3 mid = h.lerp(p, 0.55);
                    return new Shot(mid.add(side.scale(11.0)).add(up.scale(2.4)), mid.add(up.scale(2.8)), 64);
                }
                if (t < hit + 9) {
                    // low, looking up the line of the uppercut into its jaw
                    Vec3 chin = o.add(u.scale(ClashData.POUND_CHIN_ALONG)).add(up.scale(ClashData.POUND_CHIN_UP));
                    float k = Mth.clamp((t - hit + 5) / 14.0F, 0, 1);
                    return new Shot(o.add(u.scale(2.6 + 0.6 * k)).add(side.scale(4.8)).add(up.scale(0.9)), chin.add(up.scale(-0.6 + 0.8 * k)), 70);
                }
                // wide: it goes over onto its back and the player is on top of it
                Vec3 c = o.add(u.scale(-2.6));
                return new Shot(c.add(side.scale(12.5)).add(up.scale(4.6)).add(u.scale(1.5)), c.add(up.scale(1.6)), 62);
            }
            case ClashScript.PH_STRAIN -> {
                // down by its head, looking up past its guard at the player raining blows
                float a = 0.18F * Mth.sin(t * 0.04F);
                Vec3 sd = side.scale(Mth.cos(a)).add(u.scale(Mth.sin(a)));
                return new Shot(face.add(u.scale(-4.2)).add(sd.scale(5.2)).add(up.scale(1.6)), p.add(up.scale(2.0)).add(u.scale(-0.6)), 66);
            }
            default -> {
                if (s.result != ClashScript.RESULT_WIN) {
                    Vec3 mid = h.lerp(p, 0.5);
                    return new Shot(mid.add(side.scale(13.0)).add(up.scale(4.0)), mid.add(up.scale(1.8)), 64);
                }
                if (t < 24) {
                    // the two-fisted slam, close
                    Vec3 chest = o.add(u.scale(ClashData.POUND_CHEST_ALONG)).add(up.scale(ClashData.POUND_CHEST_UP));
                    return new Shot(chest.add(side.scale(5.8)).add(u.scale(1.6)).add(up.scale(2.6)), chest.add(up.scale(1.2)), 62);
                }
                if (t < ClashData.POUND_WIN_RELEASE + 3) {
                    // wide and side-on as it is hauled up overhead
                    return new Shot(p.add(side.scale(12.0)).add(u.scale(-2.5)).add(up.scale(3.6)), p.add(u.scale(-1.5)).add(up.scale(3.6)), 66);
                }
                // over the player's shoulder: it flies, lands far off and gets up facing us
                return new Shot(p.add(u.scale(6.5)).add(side.scale(6.5)).add(up.scale(5.2)), h.add(up.scale(2.2)), 58);
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
        Method move = findCameraMove();
        if (move == null) {
            return;
        }
        Camera camera = event.getCamera();
        float sk = Mth.lerp(pt, shakeO, shake);
        Minecraft mc = Minecraft.getInstance();
        double jx = 0, jy = 0, jz = 0;
        if (sk > 0.01F && mc.level != null) {
            float tt = mc.level.getGameTime() + pt;
            jx = Mth.sin(tt * 2.9F) * 0.35 * sk;
            jy = Mth.sin(tt * 3.7F + 1.0F) * 0.3 * sk;
            jz = Mth.sin(tt * 3.1F + 2.0F) * 0.35 * sk;
            event.setRoll(Mth.sin(tt * 2.3F) * 3.0F * sk);
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
        Vec3 dir = sh.look().subtract(eye);
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        event.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        event.setPitch((float) Math.toDegrees(-Math.atan2(dir.y, horiz)));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        Shot sh = shot((float) event.getPartialTick());
        if (sh != null) {
            event.setFOV(sh.fov() - 6.0F * Mth.lerp((float) event.getPartialTick(), shakeO, shake));
        }
    }

    private static Method findCameraMove() {
        if (!lookedUp) {
            lookedUp = true;
            for (String name : new String[]{"m_90568_", "move"}) {
                try {
                    Method m = Camera.class.getDeclaredMethod(name, double.class, double.class, double.class);
                    m.setAccessible(true);
                    cameraMove = m;
                    break;
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return cameraMove;
    }

    // ------------------------------------------------------------ the HUD
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (cinematic && !event.getOverlay().id().equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())
                && !event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClashPacket s = mine();
        if (s == null || !cinematic || mc.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = event.getPartialTick();
        // letterbox
        int bar = (int) (h * 0.085F);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        // flashes on the impacts
        if (flash > 0.02F) {
            int a = (int) (Mth.clamp(flash, 0, 1) * 200);
            g.fill(0, 0, w, h, (a << 24) | 0xF0E0FF);
        }
        float t = time(s, pt);
        if (s.phase == ClashScript.PH_RESOLVE && t < 40) {
            boolean won = s.result == ClashScript.RESULT_WIN;
            Component banner = Component.translatable(won ? "message.rudues.clash_won" : "message.rudues.clash_lost");
            PoseStack bp = g.pose();
            bp.pushPose();
            bp.translate(w / 2.0F, h * 0.22F, 0);
            bp.scale(2.5F, 2.5F, 1.0F);
            g.drawString(mc.font, banner, -mc.font.width(banner) / 2, 0, won ? 0xE6C8FF : 0xFF6A5A, true);
            bp.popPose();
        }
        if (s.phase != ClashScript.PH_STRAIN || t < ClashScript.PROMPT_DELAY) {
            return;
        }
        int cx = w / 2, cy = h / 2;
        float now = mc.level.getGameTime() + pt;
        // the closing rings (as in the reference), all converging on the centre ring
        for (int i = 0; i < 3; i++) {
            float ph = (now * 0.055F + i / 3.0F) % 1.0F;
            float r = 20 + (1 - ph) * 105;
            ring(g, cx, cy, r, 0xFFD27A, (int) (230 * ph));
        }
        ring(g, cx, cy, 20, 0xFFF2C0, 255);
        ring(g, cx, cy, 17, 0xFFF2C0, 160);
        // holding above the line fills the centre ring; full = the clash is won
        float hold = Mth.clamp(s.hold, 0, 1);
        if (hold > 0.0F) {
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
        Component title = Component.translatable(s.stage == ClashScript.STAGE_BEAM ? "message.rudues.clash_beam" : "message.rudues.clash", s.stage + 1);
        g.drawString(mc.font, title, -mc.font.width(title) / 2, 0, 0xFFE9B0, true);
        ps.popPose();
        Float shownB = SHOWN_BALANCE.get(s.heroId);
        boolean above = (shownB == null ? s.balance : shownB) >= ClashScript.WIN_LINE;
        Component mash = above ? Component.translatable("message.rudues.clash_hold")
                : Component.translatable("message.rudues.clash_mash", mc.options.keyAttack.getTranslatedKeyMessage());
        boolean blink = ((int) (now / 3)) % 2 == 0;
        g.drawString(mc.font, mash, cx - mc.font.width(mash) / 2, cy + 34, blink ? 0xFFFFFF : 0xFFD27A, true);
        // the tug of war: your purple against its darker purple
        Float shown = SHOWN_BALANCE.get(s.heroId);
        float b = Mth.clamp(shown == null ? s.balance : shown, 0, 1);
        int bw = 220, bx = cx - bw / 2, by = cy + 52;
        g.fill(bx - 2, by - 2, bx + bw + 2, by + 8, 0xC0000000);
        int split = bx + (int) (bw * b);
        g.fill(bx, by, split, by + 6, 0xFFA846FF);
        g.fill(split, by, bx + bw, by + 6, 0xFF4E1290);
        g.fill(split - 1, by - 3, split + 1, by + 9, 0xFFFFFFFF);
        // the line to keep it above
        int line = bx + (int) (bw * ClashScript.WIN_LINE);
        g.fill(line, by - 5, line + 1, by + 11, 0xFFFFE9B0);
        if (b >= ClashScript.WIN_LINE) {
            g.fill(line + 1, by, split - 1, by + 6, 0xFFD8A8FF);
        }
        float left = 1.0F - t / ClashScript.strainTicks(s.stage);
        g.fill(bx, by + 10, bx + (int) (bw * Mth.clamp(left, 0, 1)), by + 11, 0x90FFE9B0);
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

    // ------------------------------------------------------------ the locked beams
    static Vec3 heroMouth(ClashPacket s, float pt) {
        return heroPos(s, pt).add(ClashScript.axis(s.yaw).scale(ClashData.HERO_MOUTH_F)).add(0, ClashData.HERO_MOUTH_U, 0);
    }

    static Vec3 playerMouth(ClashPacket s, float pt) {
        return playerPos(s, pt).add(ClashScript.axis(s.yaw).scale(-ClashData.PLAYER_MOUTH_F)).add(0, ClashData.PLAYER_MOUTH_U, 0);
    }

    static Vec3 meet(ClashPacket s, float pt) {
        Float shown = SHOWN_BALANCE.get(s.heroId);
        float b = Mth.clamp(shown == null ? s.balance : shown, 0, 1);
        return heroMouth(s, pt).lerp(playerMouth(s, pt), 1.0F - b);
    }

    private static boolean beamsOn(ClashPacket s, float t) {
        if (s.stage != ClashScript.STAGE_BEAM) {
            return false;
        }
        return switch (s.phase) {
            case ClashScript.PH_CHARGE -> t >= ClashData.BEAM_CLASH_FIRE;
            case ClashScript.PH_STRAIN -> true;
            case ClashScript.PH_RESOLVE -> t < 22;
            default -> false;
        };
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || STATES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = event.getPoseStack();
        for (ClashPacket s : STATES.values()) {
            float t = time(s, pt);
            if (!beamsOn(s, t)) {
                continue;
            }
            Vec3 m = meet(s, pt), hm = heroMouth(s, pt), pm = playerMouth(s, pt);
            float now = mc.level.getGameTime() + pt;
            boolean resolving = s.phase == ClashScript.PH_RESOLVE;
            boolean heroBeam = !(resolving && s.result == ClashScript.RESULT_WIN);
            boolean playerBeam = !(resolving && s.result == ClashScript.RESULT_LOSE);
            Vec3 heroEnd = heroBeam ? (resolving ? pm : m) : hm;
            Vec3 playerEnd = playerBeam ? (resolving ? hm : m) : pm;
            if (heroBeam) {
                beam(ps, buffers, cam, hm, heroEnd, now, true);
            }
            if (playerBeam) {
                beam(ps, buffers, cam, pm, playerEnd, now, false);
            }
            if (!resolving) {
                orb(ps, buffers, cam, m, now, event.getCamera().rotation());
            }
        }
        buffers.endBatch();
    }

    private static void beam(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 from, Vec3 to, float time, boolean hero) {
        Vec3 d = to.subtract(from);
        float len = (float) d.length();
        if (len < 0.1F) {
            return;
        }
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(d.y, horiz));
        ps.pushPose();
        ps.translate(from.x - cam.x, from.y - cam.y, from.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        if (hero) {
            InfernalHeroRenderer.drawDarkBeam(ps, buffers, len, time, 1.35F);
        } else {
            float pulse = 1.0F + 0.12F * Mth.sin(time * 1.9F);
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(BeamRenderer.TEXTURE));
            BeamRenderer.drawPlanes(ps, vc, len, 2.1F * pulse, -time * 0.30F, 255, 255, 255, 190);
            BeamRenderer.drawPlanes(ps, vc, len, 1.2F * pulse, -time * 0.55F, 235, 190, 255, 255);
            BeamRenderer.drawPlanes(ps, vc, len, 0.5F * pulse, -time * 0.90F, 255, 245, 255, 255);
        }
        ps.popPose();
    }

    /** the boiling knot of fire where the beams meet */
    private static void orb(PoseStack ps, MultiBufferSource buffers, Vec3 cam, Vec3 at, float time, Quaternionf camRot) {
        ps.pushPose();
        ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        ps.mulPose(camRot);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        int frame = (int) (time / 2) % 4;
        billboard(ps, buffers.getBuffer(RenderType.eyes(ORB_HERO)), 5.2F + 0.5F * Mth.sin(time * 1.3F), time * 7, frame);
        billboard(ps, buffers.getBuffer(RenderType.eyes(ORB_PLAYER)), 3.8F + 0.4F * Mth.sin(time * 1.7F + 1), -time * 9, (frame + 2) % 4);
        ps.popPose();
    }

    private static void billboard(PoseStack ps, VertexConsumer vc, float size, float spin, int frame) {
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float h = size / 2, v0 = frame / 4.0F, v1 = (frame + 1) / 4.0F;
        vtx(vc, m, n, -h, -h, 0, v1);
        vtx(vc, m, n, h, -h, 1, v1);
        vtx(vc, m, n, h, h, 1, v0);
        vtx(vc, m, n, -h, h, 0, v0);
        ps.popPose();
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }
}
