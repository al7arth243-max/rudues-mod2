package com.rudues.mod.trial;

import com.rudues.mod.registry.ModBlocks;
import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.world.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The portal is built like an End portal: 12 crying obsidian blocks in a ring around an empty 3x3 square
 * (corners left out). Throwing a Fire Fiend into the middle opens it.
 */
public final class TrialPortal {
    private TrialPortal() {
    }

    /** The 12 frame positions around a centre, End-portal style. */
    private static boolean isFrame(ServerLevel level, BlockPos centre) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ax = Math.abs(dx), az = Math.abs(dz);
                boolean ring = Math.max(ax, az) == 2 && !(ax == 2 && az == 2);
                if (ring && !level.getBlockState(centre.offset(dx, 0, dz)).is(Blocks.CRYING_OBSIDIAN)) {
                    return false;
                }
                if (Math.max(ax, az) <= 1) {
                    BlockState inside = level.getBlockState(centre.offset(dx, 0, dz));
                    if (!inside.isAir() && !inside.canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Called while a thrown Fire Fiend rests on the ground. Returns true if it opened a portal. */
    public static boolean tryActivate(ServerLevel level, BlockPos itemPos) {
        if (level.dimension() == ModDimensions.TRIAL) {
            return false;
        }
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos centre = itemPos.offset(dx, dy, dz);
                    if (isFrame(level, centre)) {
                        open(level, centre);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void open(ServerLevel level, BlockPos centre) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(centre.offset(dx, 0, dz), ModBlocks.TRIAL_PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        double x = centre.getX() + 0.5, y = centre.getY() + 0.8, z = centre.getZ() + 0.5;
        level.playSound(null, centre, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.playSound(null, centre, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.5F, 0.5F);
        level.sendParticles(ModParticles.PURPLE_FLAME_BIG.get(), x, y, z, 60, 1.2, 0.3, 1.2, 0.05);
        level.sendParticles(ModParticles.PURPLE_EMBER.get(), x, y, z, 80, 1.5, 0.6, 1.5, 0.1);
    }
}
