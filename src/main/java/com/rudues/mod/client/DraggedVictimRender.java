package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.DemonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hero's Rush: while the demon drags a victim along the ground, draw that victim lying on its back, head toward
 * where the demon is running, neck in his claw and body trailing behind. Purely visual: the hitbox stays upright.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public class DraggedVictimRender {
    /** ticks to tip over onto the back as he slams them down */
    private static final float LAY_TICKS = 4.0F;
    /** how far along the body the claw holds them (0 = feet, 1 = top of head) */
    private static final float NECK = 0.85F;

    private record Drag(int start, DemonEntity demon) {
    }

    private static final Map<Integer, Drag> DRAGGED = new HashMap<>();
    private static final Set<Integer> PUSHED = new HashSet<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        DRAGGED.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof DemonEntity d && d.getDraggedId() >= 0) {
                DRAGGED.put(d.getDraggedId(), new Drag(d.getDragStart(), d));
            }
        }
    }

    // LOWEST + no cancelled events: only push once we know the renderer will really run (and fire Post)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (DRAGGED.isEmpty()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Drag drag = DRAGGED.get(victim.getId());
        Minecraft mc = Minecraft.getInstance();
        if (drag == null || mc.level == null) {
            return;
        }
        float pt = event.getPartialTick();
        float lay = Mth.clamp(((int) mc.level.getGameTime() - drag.start() + pt) / LAY_TICKS, 0.0F, 1.0F);
        lay = lay * lay * (3.0F - 2.0F * lay);

        // the direction he is running: his player's view yaw (what the demon model is drawn with)
        DemonEntity demon = drag.demon();
        UUID ownerId = demon.getOwnerUUID();
        Player owner = ownerId == null ? null : mc.level.getPlayerByUUID(ownerId);
        float runYaw = owner != null ? owner.getViewYRot(pt) : Mth.rotLerp(pt, demon.yRotO, demon.getYRot());
        double yr = Math.toRadians(runYaw);
        float fx = (float) -Math.sin(yr);
        float fz = (float) Math.cos(yr);

        float h = victim.getBbHeight();
        float w = victim.getBbWidth();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        PUSHED.add(victim.getId());
        // neck in his claw (the entity's position), feet trailing behind, back resting on the ground
        ps.translate(-fx * h * NECK * lay, w * 0.5F * lay, -fz * h * NECK * lay);
        // tip over around the sideways axis so the head points the way he runs
        ps.mulPose(new Quaternionf().rotationAxis(Mth.HALF_PI * lay, fz, 0.0F, -fx));
        // replace the victim's own facing with "facing backwards", so tipping over leaves it face-up
        float bodyYaw = Mth.rotLerp(pt, victim.yBodyRotO, victim.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyYaw - (runYaw + 180.0F)) * lay));
        // a body being dragged does not walk
        victim.walkAnimation.setSpeed(0.0F);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }
}
