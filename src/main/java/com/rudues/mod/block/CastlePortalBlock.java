package com.rudues.mod.block;

import com.rudues.mod.castle.CastleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The surface of an opened calcite ring: into the Feathered Hold - or, inside it, home again. */
public class CastlePortalBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);

    public CastlePortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && !player.isPassenger() && !player.isVehicle()
                && entity.getBoundingBox().intersects(SHAPE.bounds().move(pos))) {
            CastleManager.queueTravel(player, pos);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // white feathers of wind rising off it, and green motes
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + random.nextDouble(), pos.getY() + 0.8, pos.getZ() + random.nextDouble(),
                    0.0, 0.05, 0.0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.getX() + random.nextDouble(), pos.getY() + 0.85, pos.getZ() + random.nextDouble(),
                    0.0, 0.04, 0.0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + random.nextDouble(), pos.getY() + 0.8, pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.05, 0.06, (random.nextDouble() - 0.5) * 0.05);
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }
}
