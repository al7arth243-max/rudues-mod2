package com.rudues.mod.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes {@link CastleLayout} into the Feathered Hold dimension. Blocks go in without neighbour updates (fast, and
 * nothing falls or flows while half built); afterwards the panes, bars and walls are given their connections.
 */
public final class CastleBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CastleBuilder() {
    }

    public static int build(ServerLevel level) {
        Map<String, BlockState> cache = new HashMap<>();
        List<BlockPos> connect = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] count = {0};
        var lookup = level.holderLookup(Registries.BLOCK);
        CastleLayout.build((x, y, z, b) -> {
            BlockState st = cache.computeIfAbsent(b, k -> {
                try {
                    return BlockStateParser.parseForBlock(lookup, k, false).blockState();
                } catch (CommandSyntaxException e) {
                    LOGGER.warn("Feathered Hold: unknown block {}", k);
                    return Blocks.STONE_BRICKS.defaultBlockState();
                }
            });
            pos.set(x, y, z);
            level.setBlock(pos, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (st.getBlock() instanceof CrossCollisionBlock || st.getBlock() instanceof WallBlock) {
                connect.add(pos.immutable());
            }
            count[0]++;
        });
        // panes, bars, walls and fences: join them up to whatever now stands beside them
        for (BlockPos p : connect) {
            BlockState st = level.getBlockState(p);
            BlockState joined = Block.updateFromNeighbourShapes(st, level, p);
            if (joined != st) {
                level.setBlock(p, joined, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        return count[0];
    }
}
