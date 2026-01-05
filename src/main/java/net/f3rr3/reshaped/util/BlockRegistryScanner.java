package net.f3rr3.reshaped.util;

import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

public class BlockRegistryScanner {
    public static BlockMatrix scanAndBuildMatrix() {
        BlockMatrix matrix = new BlockMatrix();
        Map<String, Block> baseBlocks = new HashMap<>();
        Map<String, Block> slabs = new HashMap<>();
        Map<String, Block> stairs = new HashMap<>();

        for (Block block : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(block);
            String path = id.getPath();

            if (block instanceof SlabBlock) {
                slabs.put(path.replace("_slab", ""), block);
            } else if (block instanceof StairsBlock) {
                stairs.put(path.replace("_stairs", ""), block);
            } else {
                baseBlocks.put(path, block);
            }
        }

        for (Map.Entry<String, Block> entry : baseBlocks.entrySet()) {
            String name = entry.getKey();
            Block base = entry.getValue();
            List<Block> variants = new ArrayList<>();

            if (slabs.containsKey(name)) variants.add(slabs.get(name));
            if (stairs.containsKey(name)) variants.add(stairs.get(name));

            // Also check for common aliases (e.g., "oak_planks" -> "oak")
            if (variants.isEmpty()) {
                String strippedPlanks = name.replace("_planks", "");
                if (slabs.containsKey(strippedPlanks)) variants.add(slabs.get(strippedPlanks));
                if (stairs.containsKey(strippedPlanks)) variants.add(stairs.get(strippedPlanks));
            }

            if (!variants.isEmpty()) {
                matrix.addColumn(base, variants);
            }
        }

        return matrix;
    }
}
