package com.rudues.mod.world;

import com.rudues.mod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;

/** Places {@link ArenaLayout} into the Infernal Trial dimension, and the sword at its centre. */
public final class ArenaBuilder {
    public static final BlockPos SWORD_POS = new BlockPos(0, ArenaLayout.FLOOR + 1, 0);

    private ArenaBuilder() {
    }

    private static Map<ArenaLayout.Blk, BlockState> states() {
        Map<ArenaLayout.Blk, BlockState> m = new EnumMap<>(ArenaLayout.Blk.class);
        m.put(ArenaLayout.Blk.AIR, Blocks.AIR.defaultBlockState());
        m.put(ArenaLayout.Blk.SMOOTH_BASALT, Blocks.SMOOTH_BASALT.defaultBlockState());
        m.put(ArenaLayout.Blk.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        m.put(ArenaLayout.Blk.BLACKSTONE, Blocks.BLACKSTONE.defaultBlockState());
        m.put(ArenaLayout.Blk.CRACKED_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        m.put(ArenaLayout.Blk.DEEPSLATE, Blocks.DEEPSLATE.defaultBlockState());
        m.put(ArenaLayout.Blk.LAVA, Blocks.LAVA.defaultBlockState());
        m.put(ArenaLayout.Blk.NETHERRACK, Blocks.NETHERRACK.defaultBlockState());
        m.put(ArenaLayout.Blk.FIRE, Blocks.FIRE.defaultBlockState());
        m.put(ArenaLayout.Blk.MAGMA, Blocks.MAGMA_BLOCK.defaultBlockState());
        m.put(ArenaLayout.Blk.BASALT, Blocks.BASALT.defaultBlockState());
        m.put(ArenaLayout.Blk.BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        m.put(ArenaLayout.Blk.CRACKED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        m.put(ArenaLayout.Blk.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        m.put(ArenaLayout.Blk.CRACKED_DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
        m.put(ArenaLayout.Blk.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILES.defaultBlockState());
        m.put(ArenaLayout.Blk.POLISHED_BASALT, Blocks.POLISHED_BASALT.defaultBlockState());
        m.put(ArenaLayout.Blk.CHAIN, Blocks.CHAIN.defaultBlockState());
        m.put(ArenaLayout.Blk.IRON_BARS, Blocks.IRON_BARS.defaultBlockState());
        m.put(ArenaLayout.Blk.LIGHT, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 12));
        m.put(ArenaLayout.Blk.GILDED_BLACKSTONE, Blocks.GILDED_BLACKSTONE.defaultBlockState());
        m.put(ArenaLayout.Blk.CRYING_OBSIDIAN, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        m.put(ArenaLayout.Blk.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        return m;
    }

    /** One-off: writes the whole arena (about 770k block placements; takes a few seconds on first start). */
    public static int build(ServerLevel level) {
        Map<ArenaLayout.Blk, BlockState> map = states();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] count = {0};
        ArenaLayout.build((x, y, z, b) -> {
            pos.set(x, y, z);
            level.setBlock(pos, map.get(b), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            count[0]++;
        });
        placeSword(level, true);
        return count[0];
    }

    /** The sword stands in the centre whenever no trial is being fought. */
    public static void placeSword(ServerLevel level, boolean present) {
        BlockState now = level.getBlockState(SWORD_POS);
        if (present && !now.is(ModBlocks.EMBEDDED_SWORD.get())) {
            level.setBlock(SWORD_POS, ModBlocks.EMBEDDED_SWORD.get().defaultBlockState(), Block.UPDATE_ALL);
        } else if (!present && now.is(ModBlocks.EMBEDDED_SWORD.get())) {
            level.setBlock(SWORD_POS, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
