package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.network.CastAbilityPacket;
import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModEffects;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import com.rudues.mod.ability.HeroConstants;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.lang.reflect.Method;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {
    private static boolean wheelWasDown = false;
    private static boolean wasDemon = false;
    private static Method cameraMove;
    private static boolean cameraLookedUp = false;
    private static float camBlend = 0.0F;
    private static float camBlendO = 0.0F;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            wasDemon = false;
            camBlend = camBlendO = 0.0F;
            com.rudues.mod.entity.DemonEntity.CLIENT_OWNERS.clear();
            return;
        }
        trackDemonOwners(mc);

        if (ClientPowerState.heroTicks > 0) {
            ClientPowerState.heroTicks--;
        }
        // (the power-up itself does not count down: the server sends the full time while it plays)
        if (ClientPowerState.semiTicks > 0 && ClientPowerState.semiTicks < com.rudues.mod.ability.SemiPrime.DURATION) {
            ClientPowerState.semiTicks--;
        }
        if (ClientPowerState.hasPowers && mc.player.isOnFire()) {
            mc.player.clearFire();
        }

        // when the local player turns into (or back from) the demon: resize hitbox, show them the demon
        boolean demon = mc.player.hasEffect(ModEffects.HERO_OF_HELL.get());
        if (demon != wasDemon) {
            wasDemon = demon;
            mc.player.refreshDimensions();
            if (demon) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
        // shoulder camera eases in when he transforms and back out when the form ends
        camBlendO = camBlend;
        boolean shoulder = demon && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK;
        camBlend = Mth.clamp(camBlend + (shoulder ? 0.08F : -0.12F), 0.0F, 1.0F);

        // hold R -> skill wheel (rising edge only, so it does not reopen while R is still held)
        boolean rDown = ClientKeys.isHeld(ClientKeys.OPEN_WHEEL);
        if (rDown && !wheelWasDown && mc.screen == null && ClientPowerState.hasPowers) {
            mc.setScreen(new SkillWheelScreen());
        }
        wheelWasDown = rDown;
        while (ClientKeys.OPEN_WHEEL.consumeClick()) {
            // drain
        }

        // universal cast button
        while (ClientKeys.CAST_SELECTED.consumeClick()) {
            if (mc.screen == null && ClientPowerState.hasPowers) {
                Network.CHANNEL.sendToServer(new CastAbilityPacket(ClientPowerState.getSelected().ordinal()));
            }
        }
    }

    /** The player is drawn as the GeckoLib demon instead of the normal player model (other players too). */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().hasEffect(ModEffects.HERO_OF_HELL.get())
                || com.rudues.mod.entity.DemonEntity.CLIENT_OWNERS.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    /** which players' demons are in this world right now; a change resizes that player's hitbox here */
    private static void trackDemonOwners(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        java.util.Set<java.util.UUID> now = new java.util.HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof com.rudues.mod.entity.DemonEntity d && d.getOwnerUUID() != null) {
                now.add(d.getOwnerUUID());
            }
        }
        java.util.Set<java.util.UUID> owners = com.rudues.mod.entity.DemonEntity.CLIENT_OWNERS;
        if (now.equals(owners)) {
            return;
        }
        java.util.Set<java.util.UUID> changed = new java.util.HashSet<>(owners);
        changed.addAll(now);
        for (java.util.UUID id : now) {
            if (owners.contains(id)) {
                changed.remove(id);
            }
        }
        for (java.util.UUID id : owners) {
            if (now.contains(id)) {
                changed.remove(id);
            }
        }
        owners.clear();
        owners.addAll(now);
        for (java.util.UUID id : changed) {
            net.minecraft.world.entity.player.Player p = mc.level.getPlayerByUUID(id);
            if (p != null) {
                p.refreshDimensions();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasEffect(ModEffects.HERO_OF_HELL.get())) {
            event.setCanceled(true);
        }
    }

    /** No burning-screen overlay for anyone carrying the power. */
    @SubscribeEvent
    public static void onScreenEffect(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.FIRE && ClientPowerState.hasPowers) {
            event.setCanceled(true);
        }
    }

    /**
     * Over-the-shoulder camera while transformed: the demon fills the left of the screen, the camera sits behind
     * his right shoulder and orbits it with your aim, and the view turns slightly so the crosshair lands on what
     * he is actually aiming at. Walls pull the camera in. It eases in and out over ~0.6 s.
     */
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (ClashClient.cinematicActive()) {
            return;             // the clash cinematic owns the camera
        }
        Method move = findCameraMove();
        if (move == null) {
            return;
        }
        Camera camera = event.getCamera();
        // facing the camera (F5 twice): just back off so the whole demon fits
        if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            if (mc.player.hasEffect(ModEffects.HERO_OF_HELL.get())) {
                try {
                    move.invoke(camera, -HeroConstants.CAMERA_EXTRA, 1.5D, 0.0D);
                } catch (Exception ignored) {
                    // leave the camera alone if anything goes wrong
                }
            }
            return;
        }
        float pt = (float) event.getPartialTick();
        float b = Mth.lerp(pt, camBlendO, camBlend);
        if (b <= 0.001F || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            return;
        }
        b = b * b * (3.0F - 2.0F * b);
        Entity e = camera.getEntity();
        if (e == null) {
            return;
        }
        Vec3 look = Vec3.directionFromRotation(event.getPitch(), event.getYaw());
        double yr = Math.toRadians(event.getYaw());
        Vec3 right = new Vec3(-Math.cos(yr), 0.0, -Math.sin(yr));

        // shoulder pivot, then back off along the view so the camera orbits the shoulder
        Vec3 base = e.getPosition(pt).add(0.0, HeroConstants.CAM_HEIGHT, 0.0);
        Vec3 want = base.add(right.scale(HeroConstants.CAM_SIDE)).subtract(look.scale(HeroConstants.CAM_DISTANCE));
        BlockHitResult wall = mc.level.clip(new ClipContext(base, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
        if (wall.getType() != HitResult.Type.MISS) {
            Vec3 d = want.subtract(base);
            double keep = Math.max(0.0, base.distanceTo(wall.getLocation()) - 0.35);
            want = base.add(d.scale(keep / d.length()));
        }
        Vec3 cur = camera.getPosition();
        Vec3 target = cur.add(want.subtract(cur).scale(b));
        Vec3 delta = target.subtract(cur);
        // the camera's own axes, built exactly the way Camera.setRotation builds them
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

        // converge: aim the view at the point the demon's own line of sight reaches, so the crosshair is honest
        Vec3 eye = e.getEyePosition(pt);
        BlockHitResult aim = mc.level.clip(new ClipContext(eye, eye.add(look.scale(64.0)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, e));
        double dist = aim.getType() == HitResult.Type.MISS ? 64.0 : Math.max(14.0, eye.distanceTo(aim.getLocation()));
        Vec3 dir = eye.add(look.scale(dist)).subtract(target);
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(dir.y, horiz));
        event.setYaw(Mth.rotLerp(b, event.getYaw(), yaw));
        event.setPitch(Mth.lerp(b, event.getPitch(), pitch));
    }

    private static Method findCameraMove() {
        if (!cameraLookedUp) {
            cameraLookedUp = true;
            // runtime (obfuscated) name first, then the dev-environment name
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
}
