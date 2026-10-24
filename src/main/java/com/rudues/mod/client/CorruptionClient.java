package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/** Which players carry the Shadow's corruption (synced by the trial every two seconds); they smoulder and wear its growths. */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class CorruptionClient {
    private CorruptionClient() {
    }

    private static final Set<Integer> IDS = new HashSet<>();
    /** client ticks since the last sync (it is dropped if the syncs stop: left the arena, trial over) */
    private static int sinceSync;

    public static void set(int[] ids) {
        IDS.clear();
        for (int id : ids) {
            IDS.add(id);
        }
        sinceSync = 0;
    }

    public static boolean has(int entityId) {
        return IDS.contains(entityId);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || IDS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ++sinceSync > 140) {
            IDS.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        for (int id : IDS) {
            Entity e = mc.level.getEntity(id);
            if (!(e instanceof Player p) || p.isInvisible() || mc.level.random.nextInt(3) != 0) {
                continue;
            }
            // embers off the left arm and the greaves
            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);       // the player's right
            boolean arm = mc.level.random.nextBoolean();
            double side = arm ? -0.37 : (mc.level.random.nextBoolean() ? -0.12 : 0.12);
            double up = arm ? 0.8 + mc.level.random.nextDouble() * 0.6 : mc.level.random.nextDouble() * 0.7;
            mc.level.addParticle(mc.level.random.nextInt(4) == 0 ? ModParticles.PURPLE_FLAME.get() : ModParticles.PURPLE_EMBER.get(),
                    p.getX() + rx * side, p.getY() + up, p.getZ() + rz * side, 0, 0.02, 0);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        IDS.clear();
    }

    /** a new dimension (or respawn): entity ids start over, and the trial's corruption stays behind */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        IDS.clear();
    }
}
