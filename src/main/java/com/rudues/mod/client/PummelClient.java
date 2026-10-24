package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.ability.PummelData;
import com.rudues.mod.ability.PummelScript;
import com.rudues.mod.network.PummelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pummel's victim, drawn the way it is being beaten: standing in front of the player facing them, its head and body
 * snapped round and back by each haymaker, then thrown back off its feet by the last punch and righting itself as it
 * flies. Placement comes from {@link PummelScript} relative to the player as drawn here. Purely visual.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class PummelClient {
    private PummelClient() {
    }

    /** after the last punch: ticks to come upright again while it flies */
    private static final float TUMBLE = 16.0F;

    private record Hold(int playerId, long start, float yaw) {
    }

    private static final Map<Integer, Hold> HELD = new HashMap<>();
    private static final Set<Integer> PUSHED = new HashSet<>();

    public static void receive(PummelPacket m) {
        if (m.active) {
            HELD.put(m.victimId, new Hold(m.playerId, m.start, m.yaw));
        } else {
            HELD.values().removeIf(h -> h.playerId() == m.playerId);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HELD.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            HELD.clear();
            return;
        }
        long now = mc.level.getGameTime();
        HELD.entrySet().removeIf(en -> now - en.getValue().start() > PummelData.BIG + TUMBLE + 4 || mc.level.getEntity(en.getKey()) == null);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HELD.clear();
    }

    private static float smooth(float u) {
        u = Mth.clamp(u, 0.0F, 1.0F);
        return u * u * (3.0F - 2.0F * u);
    }

    /**
     * Tips the victim over in the pose stack (already at its interpolated feet). Returns false when it isn't being
     * pummelled. The caller pops the pose afterwards when this returns true.
     */
    public static boolean apply(LivingEntity victim, float pt, PoseStack ps) {
        Hold h = HELD.get(victim.getId());
        Minecraft mc = Minecraft.getInstance();
        if (h == null || mc.level == null) {
            return false;
        }
        Entity player = mc.level.getEntity(h.playerId());
        if (player == null) {
            return false;
        }
        float t = (mc.level.getGameTime() - h.start()) + pt;
        float height = victim.getBbHeight();
        Vec3 here = victim.getPosition(pt);
        Vec3 f = PummelScript.forward(h.yaw()), r = PummelScript.right(h.yaw());
        Vec3 mid;
        float lean, twist = 0.0F;
        if (t <= PummelData.BIG) {
            // in front of the player as drawn here, on its own feet
            Vec3 pp = player.getPosition(pt);
            float dist = PummelScript.distance(victim.getBbWidth());
            mid = new Vec3(pp.x + f.x * dist, here.y + height * 0.5F, pp.z + f.z * dist);
            float[] st = PummelScript.stagger(t);
            lean = st[0];
            twist = st[1];
        } else {
            // the last punch: knocked back off its feet, righting itself as it flies
            float k = (t - PummelData.BIG) / TUMBLE;
            if (k >= 1.0F) {
                return false;
            }
            lean = 55.0F * (float) Math.sin(Math.PI * Math.min(1.0F, k * 1.4F)) * (1.0F - smooth(k));
            mid = here.add(0, height * 0.5F, 0);
        }
        ps.pushPose();
        Vec3 d = mid.subtract(here);
        ps.translate(d.x, d.y, d.z);
        // leans away from the player (around their right) and turns with the blow
        Quaternionf q = new Quaternionf().rotationAxis(-lean * Mth.DEG_TO_RAD, (float) r.x, 0, (float) r.z)
                .mul(new Quaternionf().rotationY(twist * Mth.DEG_TO_RAD));
        ps.translate(0, -height * 0.5F, 0);
        // pivot at its feet: they stay planted while the body rocks back
        ps.mulPose(q);
        // swap its own facing for "facing the player" (the renderer turns it by its body yaw next)
        float body = Mth.rotLerp(pt, victim.yBodyRotO, victim.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(body - (h.yaw() + 180.0F))));
        victim.walkAnimation.setSpeed(0.0F);
        return true;
    }

    /** the rotation taking (forward a1, up a2) onto (forward b1, up b2) */
    private static Quaternionf basisTurn(Vector3f a1, Vector3f a2, Vector3f b1, Vector3f b2) {
        Matrix3f a = frame(a1, a2), b = frame(b1, b2);
        Matrix3f m = new Matrix3f(b).mul(a.transpose());
        return new Quaternionf().setFromNormalized(m);
    }

    private static Matrix3f frame(Vector3f fwd, Vector3f up) {
        Vector3f x = new Vector3f(fwd).normalize(), y = new Vector3f(up).normalize();
        Vector3f z = new Vector3f(x).cross(y);
        return new Matrix3f(x, y, z);
    }

    // vanilla-rendered victims (GeckoLib ones, like the Shadow, call apply() from their own renderer)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (HELD.isEmpty()) {
            return;
        }
        if (apply(event.getEntity(), event.getPartialTick(), event.getPoseStack())) {
            PUSHED.add(event.getEntity().getId());
        }
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }
}
