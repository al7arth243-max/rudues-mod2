package com.nazarick.block;

import com.nazarick.ceremony.ThroneCeremony;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The seat of the Throne of Nazarick. FACING is the direction the throne looks.
 * Right-click with the Staff of Ainz Ooal Gown to claim Nazarick; the ruler can sit on it afterwards.
 */
public class ThroneBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SEAT = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape FACING_NORTH = Shapes.or(SEAT, Block.box(0, 8, 8, 16, 16, 16));
    private static final VoxelShape FACING_SOUTH = Shapes.or(SEAT, Block.box(0, 8, 0, 16, 16, 8));
    private static final VoxelShape FACING_EAST = Shapes.or(SEAT, Block.box(0, 8, 0, 8, 16, 16));
    private static final VoxelShape FACING_WEST = Shapes.or(SEAT, Block.box(8, 8, 0, 16, 16, 16));

    public ThroneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> FACING_SOUTH;
            case EAST -> FACING_EAST;
            case WEST -> FACING_WEST;
            default -> FACING_NORTH;
        };
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ThroneCeremony.onThroneUsed(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
