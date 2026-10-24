package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.ability.SemiPrime;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/**
 * The Semi Prime power-up, seen from outside: while your stand-in plays it the camera swings round in front of you,
 * low, drifting across as the growths erupt, and pushes in for the roar. Then hands the camera back.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class SemiPrimeClient {
    private SemiPrimeClient() {
    }

    private static boolean on;
    private static CameraType saved;
    private static int start;
    private static float yaw;

    private static boolean wanted(Minecraft mc) {
        return mc.player != null && SemiPrime.CLIP.equals(PuppetClient.clipOf(mc.player.getId()))
                && !AwakenClient.cinematicActive() && !ClashClient.cinematicActive();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean want = wanted(mc);
        if (want && !on) {
            on = true;
            saved = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);     // so your stand-in is drawn
            start = mc.player.tickCount;
            yaw = PuppetClient.yawOf(mc.player.getId());
        } else if (!want && on) {
            on = false;
            if (saved != null) {
                mc.options.setCameraType(saved);
            }
            saved = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.player == null) {
            return;
        }
        Method move = ClashClient.findCameraMove();
        if (move == null) {
            return;
        }
        float pt = (float) event.getPartialTick();
        float t = (mc.player.tickCount - start + pt) / SemiPrime.TRANSFORM_TICKS;      // 0..1 over the power-up
        float k = Mth.clamp(t, 0.0F, 1.0F);
        // in front of you, sweeping from your left to your right; closer and lower for the roar
        float roar = Mth.clamp((t - 0.5F) / 0.25F, 0.0F, 1.0F);
        roar = roar * roar * (3.0F - 2.0F * roar);
        float around = -40.0F + 70.0F * k;
        double dist = 4.4 - 1.3 * roar;
        double height = 1.55 - 0.5 * roar;
        float camYaw = yaw + around;                   // the direction from you to the camera (yaw = the way you face)
        double r = Math.toRadians(camYaw);
        Vec3 base = mc.player.getPosition(pt);
        Vec3 eye = base.add(-Math.sin(r) * dist, height, Math.cos(r) * dist);
        Vec3 look = base.add(0, 1.15 + 0.15 * roar, 0);

        Camera camera = event.getCamera();
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
        Vec3 dir = look.subtract(eye);
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        event.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        event.setPitch((float) -Math.toDegrees(Math.atan2(dir.y, horiz)));
    }
}
