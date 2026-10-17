package com.rudues.mod.ability;

import com.rudues.mod.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class FxUtil {
    /** A burst of purple flame wisps, embers and lightning streaks. */
    public static void purpleBurst(ServerLevel level, Vec3 pos, int count, double spread) {
        level.sendParticles(ModParticles.PURPLE_FLAME.get(), pos.x, pos.y, pos.z, count, spread, spread, spread, 0.03);
        level.sendParticles(ModParticles.PURPLE_EMBER.get(), pos.x, pos.y, pos.z, Math.max(2, count / 2), spread * 1.3, spread * 1.3, spread * 1.3, 0.06);
        level.sendParticles(ModParticles.PURPLE_SPARK.get(), pos.x, pos.y, pos.z, Math.max(1, count / 4), spread, spread, spread, 0.15);
    }
}
