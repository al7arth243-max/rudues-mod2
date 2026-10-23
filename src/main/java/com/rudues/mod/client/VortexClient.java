package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.InfernalShadowEntity;
import com.rudues.mod.entity.VortexShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Inferno Vortex pulls the local player on their own client, before their movement runs each tick: smooth to
 * walk against and never rubber-bands. (The server pulls every other creature.)
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class VortexClient {
    private VortexClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || player.isSpectator() || player.getAbilities().flying) {
            return;
        }
        for (InfernalShadowEntity shadow : mc.level.getEntitiesOfClass(InfernalShadowEntity.class,
                player.getBoundingBox().inflate(VortexShape.PULL_RANGE, 20, VortexShape.PULL_RANGE),
                e -> e.getMove() == InfernalShadowEntity.MOVE_VORTEX)) {
            float t = shadow.moveTime(0);
            float s = VortexShape.strength(t);
            double dx = player.getX() - shadow.getX(), dz = player.getZ() - shadow.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            double a = VortexShape.pull(dist, player.onGround(), s);
            if (a <= 0) {
                continue;
            }
            Vec3 v = player.getDeltaMovement().add(-dx / dist * a, 0, -dz / dist * a);
            // caught in the funnel: whirled around it
            if (VortexShape.inside(dx, player.getY() - shadow.getY(), dz, t, 0.3)) {
                v = v.add(-dz / dist * 0.06, 0.0, dx / dist * 0.06);
            }
            player.setDeltaMovement(v);
        }
    }
}
