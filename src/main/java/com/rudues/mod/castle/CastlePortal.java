package com.rudues.mod.castle;

import com.rudues.mod.registry.ModBlocks;
import com.rudues.mod.world.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The way to the Feathered Hold: the Infernal Trial's ring again (12 blocks round an empty 3x3, corners left out), but
 * of calcite; an emerald thrown into it opens it.
 */
public final class CastlePortal {
    private CastlePortal() {
    }

    private static boolean isFrame(ServerLevel level, BlockPos centre) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int ax = Math.abs(dx), az = Math.abs(dz);
                boolean ring = Math.max(ax, az) == 2 && !(ax == 2 && az == 2);
                if (ring && !level.getBlockState(centre.offset(dx, 0, dz)).is(Blocks.CALCITE)) {
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

    /** an emerald resting at itemPos: opens the ring it lies in, if it lies in one */
    public static boolean tryActivate(ServerLevel level, BlockPos itemPos) {
        if (level.dimension() == ModDimensions.CASTLE || level.dimension() == ModDimensions.TRIAL) {
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
                level.setBlock(centre.offset(dx, 0, dz), ModBlocks.CASTLE_PORTAL.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        double x = centre.getX() + 0.5, y = centre.getY() + 0.8, z = centre.getZ() + 0.5;
        level.playSound(null, centre, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 0.9F);
        level.playSound(null, centre, SoundEvents.ELYTRA_FLYING, SoundSource.BLOCKS, 1.0F, 1.4F);
        level.sendParticles(ParticleTypes.CLOUD, x, y, z, 60, 1.2, 0.3, 1.2, 0.08);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 50, 1.4, 0.5, 1.4, 0.1);
        level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 0.5, z, 80, 1.5, 1.0, 1.5, 0.05);
    }
}
