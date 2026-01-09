package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class VariantCompleter {
    public static void completeMatrix(BlockMatrix matrix) {
        for (Block base : matrix.getMatrix().keySet()) {
            completeVariant(base, matrix);
        }
    }

    public static void completeVariant(Block base, BlockMatrix matrix) {
        if (base == Blocks.AIR) return;

        List<Block> variants = matrix.getMatrix().get(base);
        if (variants == null) return;

        boolean hasSlab = false;
        boolean hasStair = false;

        for (Block v : variants) {
            if (v instanceof SlabBlock) hasSlab = true;
            if (v instanceof StairsBlock) hasStair = true;
        }

        Identifier baseId = Registries.BLOCK.getId(base);

        if (!hasSlab) {
            Block existingSlab = findExistingVariant(baseId.getPath(), "slab");
            if (existingSlab != null) {
                if (!variants.contains(existingSlab)) {
                    variants.add(existingSlab);
                    matrix.setReason(existingSlab, "Adopted existing slab variant based on naming pattern");
                    Reshaped.LOGGER.info("Adopted existing slab: " + Registries.BLOCK.getId(existingSlab));
                }
            } else {
                Identifier id = new Identifier(Reshaped.MOD_ID, baseId.getPath() + "_slab");
                // Check if already registered (could happen with dynamic loading if not careful)
                if (Registries.BLOCK.get(id) == Blocks.AIR) {
                    SlabBlock slab = new SlabBlock(AbstractBlock.Settings.copy(base));
                    Registry.register(Registries.BLOCK, id, slab);
                    Registry.register(Registries.ITEM, id, new BlockItem(slab, new Item.Settings()));
                    variants.add(slab);
                    matrix.setReason(slab, "Dynamically registered as missing slab variant for " + base.getName().getString());
                    Reshaped.LOGGER.info("Registered new slab: " + id);
                }
            }
        }

        if (!hasStair) {
            Block existingStairs = findExistingVariant(baseId.getPath(), "stairs");
            if (existingStairs != null) {
                if (!variants.contains(existingStairs)) {
                    variants.add(existingStairs);
                    matrix.setReason(existingStairs, "Adopted existing stairs variant based on naming pattern");
                    Reshaped.LOGGER.info("Adopted existing stairs: " + Registries.BLOCK.getId(existingStairs));
                }
            } else {
                Identifier id = new Identifier(Reshaped.MOD_ID, baseId.getPath() + "_stairs");
                if (Registries.BLOCK.get(id) == Blocks.AIR) {
                    StairsBlock stairs = new StairsBlock(base.getDefaultState(), AbstractBlock.Settings.copy(base));
                    Registry.register(Registries.BLOCK, id, stairs);
                    Registry.register(Registries.ITEM, id, new BlockItem(stairs, new Item.Settings()));
                    variants.add(stairs);
                    matrix.setReason(stairs, "Dynamically registered as missing stairs variant for " + base.getName().getString());
                    Reshaped.LOGGER.info("Registered new stairs: " + id);
                }
            }
        }
    }

    private static Block findExistingVariant(String baseName, String suffix) {
        // Try various common naming patterns to find existing variants
        List<String> candidates = Arrays.asList(
            baseName + "_" + suffix,                             // e.g. oak_planks -> oak_planks_slab
            baseName.replace("_planks", "") + "_" + suffix,      // e.g. oak_planks -> oak_slab
            baseName.replace("_block", "") + "_" + suffix,       // e.g. quartz_block -> quartz_slab
            baseName.replace("bricks", "brick") + "_" + suffix,  // e.g. bricks -> brick_slab
            baseName.replace("tiles", "tile") + "_" + suffix,    // e.g. tiles -> tile_slab
            baseName.replace("shingles", "shingle") + "_" + suffix, // create mod compat
            baseName.replace("tiles", "tile") + "_" + suffix,    // create mod compat
            (baseName.endsWith("s") ? baseName.substring(0, baseName.length() - 1) : baseName) + "_" + suffix // generic plural
        );

        for (String name : candidates) {
            if (name.equals(baseName)) continue; // Safety check
            
            for (Identifier id : Registries.BLOCK.getIds()) {
                if (id.getPath().equals(name)) {
                    Block block = Registries.BLOCK.get(id);
                    if (suffix.equals("slab") && block instanceof SlabBlock) return block;
                    if (suffix.equals("stairs") && block instanceof StairsBlock) return block;
                }
            }
        }
        return null;
    }
}
