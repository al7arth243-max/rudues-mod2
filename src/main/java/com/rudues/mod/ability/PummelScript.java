package com.rudues.mod.ability;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pummel, shared by the server (which keeps the enemy standing in reach) and the client (which staggers it under each
 * haymaker): the enemy stands facing the player, just in front of them.
 */
public final class PummelScript {
    private PummelScript() {
    }

    /** how far in front of the player the enemy's middle is kept (wider enemies a little further) */
    public static float distance(float width) {
        return PummelData.VICTIM_F + Math.max(0.0F, width - 0.6F) * 0.5F;
    }

    /** {lean back (degrees), twist (degrees)} of the enemy's stagger at time t (ticks after reaching it) */
    public static float[] stagger(float t) {
        float lean = 0.0F, twist = 0.0F;
        for (int i = 0; i < PummelData.PUNCHES.length; i++) {
            float age = t - PummelData.PUNCHES[i];
            if (age >= 0) {
                float k = (float) Math.exp(-age / 3.0F);
                lean += 9.0F * k;
                twist += (PummelData.RIGHT[i] ? -16.0F : 16.0F) * k;
            }
        }
        return new float[]{Math.min(lean, 18.0F), Mth.clamp(twist, -24.0F, 24.0F)};
    }

    public static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    public static Vec3 right(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
    }
}
