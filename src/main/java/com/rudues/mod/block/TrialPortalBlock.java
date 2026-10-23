package com.rudues.mod.block;

import com.rudues.mod.registry.ModParticles;
import com.rudues.mod.trial.TrialManager;
import net.minecraft.core.BlockPos;
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

/** Like the End portal surface: a thin glowing sheet you fall into. */
public class TrialPortalBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);

    public TrialPortalBlock(Properties properties) {
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
            TrialManager.queueEnter(player);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ModParticles.PURPLE_FLAME.get(), x, pos.getY() + 0.8, z, 0.0, 0.03 + random.nextDouble() * 0.04, 0.0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.PURPLE_EMBER.get(), pos.getX() + random.nextDouble(), pos.getY() + 0.8,
                    pos.getZ() + random.nextDouble(), 0.0, 0.08, 0.0);
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
