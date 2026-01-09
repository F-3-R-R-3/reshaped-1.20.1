package net.f3rr3.reshaped.util;

import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.registry.VariantCompleter;
import net.f3rr3.reshaped.registry.VerticalSlabRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.block.BlockState;
import java.lang.reflect.Field;
import java.util.*;

public class BlockRegistryScanner {
    public static void init(BlockMatrix matrix) {
        // 1. Process blocks already in the registry
        // We use a copy of the list to avoid concurrent modification if completeVariant registers new blocks
        List<Block> initialBlocks = new ArrayList<>();
        for (Block block : Registries.BLOCK) {
            initialBlocks.add(block);
        }

        for (Block block : initialBlocks) {
            processBlock(block, matrix, false); // Don't refresh inside the loop
        }
        matrix.refresh();

        // 2. Reactively process blocks added later
        RegistryEntryAddedCallback.event(Registries.BLOCK).register((rawId, id, block) -> {
            processBlock(block, matrix, true); // Refresh for individual additions
        });
    }

    private static void processBlock(Block block, BlockMatrix matrix, boolean shouldRefresh) {
        // CRITICAL: Never process AIR or non-standard blocks as variants
        if (block == null || block == Blocks.AIR) return;
        
        // If this block is already in the matrix (as base or variant), skip
        if (matrix.hasBlock(block)) return;

        Block base = null;
        String reason = null;

        // Method 1: Reflection for Stairs (Highly Accurate)
        if (block instanceof StairsBlock) {
            try {
                Field field = StairsBlock.class.getDeclaredField("baseBlockState");
                field.setAccessible(true);
                BlockState state = (BlockState) field.get(block);
                if (state != null && state.getBlock() != null && state.getBlock() != Blocks.AIR) {
                    base = state.getBlock();
                    reason = "Classified as Stairs based on its baseBlockState (" + base.getName().getString() + ")";
                }
            } catch (Exception e) {
                // Fallback to Mixin if reflection fails
            }
        }

        // Method 2: Mixin tracking (Only for known variant types)
        if (base == null && (block instanceof SlabBlock || block instanceof StairsBlock)) {
            if (block instanceof net.f3rr3.reshaped.util.BlockSourceTracker tracker) {
                base = tracker.reshaped$getSourceBlock();
                if (base != null) {
                    reason = "Tracked via Mixin as a variant of " + base.getName().getString() + " (copied settings)";
                }
            }
        }

        // Validation: Ensure valid base, no self-mapping, no AIR, and no chaining
        if (base != null && base != block && base != Blocks.AIR) {
            // IMPORTANT: Prevent "chaining" (e.g. Copper -> Cut Copper -> Cut Copper Slab)
            // We only want the immediate full block. If 'base' is itself a slab or stair, ignore it.
            if (!(base instanceof SlabBlock) && !(base instanceof StairsBlock)) {
                // We found a variant!
                List<Block> variants = new ArrayList<>();
                variants.add(block);
                matrix.addColumn(base, variants, shouldRefresh);
                matrix.setReason(block, reason);
                matrix.setReason(base, "Base block for the group");

                // Now that we have a base block, complete its other variants (slabs/stairs)
                // This will also check for existing ones before registering
                VariantCompleter.completeVariant(base, matrix);
                
                // And register its vertical slab
                VerticalSlabRegistry.registerVerticalSlabForBase(base, matrix);

                // Ensure the matrix and its reverse mapping are up to date
                if (shouldRefresh) matrix.refresh();
            }
        }
    }
}
