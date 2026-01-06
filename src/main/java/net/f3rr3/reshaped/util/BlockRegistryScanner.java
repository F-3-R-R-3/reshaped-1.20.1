package net.f3rr3.reshaped.util;

import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.block.BlockState;
import java.lang.reflect.Field;
import java.util.*;

public class BlockRegistryScanner {
    public static BlockMatrix scanAndBuildMatrix() {
        BlockMatrix matrix = new BlockMatrix();
        Map<Block, List<Block>> groupings = new HashMap<>();

        for (Block block : Registries.BLOCK) {
            // CRITICAL: Never process AIR or non-standard blocks as variants
            if (block == null || block == Blocks.AIR) continue;
            
            Block base = null;

            // Method 1: Reflection for Stairs (Highly Accurate)
            if (block instanceof StairsBlock) {
                try {
                    Field field = StairsBlock.class.getDeclaredField("baseBlockState");
                    field.setAccessible(true);
                    BlockState state = (BlockState) field.get(block);
                    if (state != null && state.getBlock() != null && state.getBlock() != Blocks.AIR) {
                        base = state.getBlock();
                    }
                } catch (Exception e) {
                    // Fallback to Mixin if reflection fails
                }
            }

            // Method 2: Mixin tracking (Only for known variant types)
            if (base == null && (block instanceof SlabBlock || block instanceof StairsBlock)) {
                if (block instanceof BlockSourceTracker tracker) {
                    base = tracker.reshaped$getSourceBlock();
                }
            }

            // Validation: Ensure valid base, no self-mapping, and no AIR
            if (base != null && base != block && base != Blocks.AIR) {
                // IMPORTANT: Prevent "chaining" (e.g. Copper -> Cut Copper -> Cut Copper Slab)
                // We only want the immediate full block. If 'base' is itself a slab or stair, ignore it.
                if (!(base instanceof SlabBlock) && !(base instanceof StairsBlock)) {
                    groupings.computeIfAbsent(base, k -> new ArrayList<>()).add(block);
                }
            }
        }

        for (Map.Entry<Block, List<Block>> entry : groupings.entrySet()) {
            matrix.addColumn(entry.getKey(), entry.getValue());
        }

        return matrix;
    }
}
