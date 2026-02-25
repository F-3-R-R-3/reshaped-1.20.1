package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.Slab.ReshapedSlabBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VariantCompleter {
    private VariantCompleter() {
    }

    public static void completeVariant(Block base, BlockMatrix matrix) {
        if (base == null || base == Blocks.AIR) return;

        List<Block> variants = matrix.getMatrix().get(base);
        if (variants == null) return;

        boolean hasSlab = false;
        boolean hasStair = false;
        for (Block v : variants) {
            if (v instanceof SlabBlock) hasSlab = true;
            if (v instanceof StairsBlock) hasStair = true;
        }

        Identifier baseId = Registries.BLOCK.getId(base);
        String baseNamespace = baseId.getNamespace();

        if (!hasSlab) {
            Block existingSlab = findExistingVariant(baseId.getPath(), "slab", baseNamespace);
            if (existingSlab != null) {
                if (!variants.contains(existingSlab)) {
                    variants.add(existingSlab);
                    matrix.setReason(existingSlab, "Adopted existing slab variant: " + Registries.BLOCK.getId(existingSlab));
                }
            } else {
                String cleanBase = baseId.getPath().replace("_planks", "").replace("_block", "");
                String path = cleanBase + "_slab";
                Identifier id = new Identifier(Reshaped.MOD_ID, path);
                if (Registries.BLOCK.get(id) != Blocks.AIR) {
                    path = baseNamespace + "_" + baseId.getPath() + "_slab";
                    id = new Identifier(Reshaped.MOD_ID, path);
                }
                if (Registries.BLOCK.get(id) == Blocks.AIR) {
                    SlabBlock slab = new ReshapedSlabBlock(VariantSettingsFactory.create(base));
                    Registry.register(Registries.BLOCK, id, slab);
                    Registry.register(Registries.ITEM, id, new BlockItem(slab, new Item.Settings()));
                    variants.add(slab);
                    matrix.setReason(slab, "Dynamically registered slab variant for " + base.getName().getString());
                }
            }
        }

        if (!hasStair) {
            Block existingStairs = findExistingVariant(baseId.getPath(), "stairs", baseNamespace);
            if (existingStairs != null) {
                if (!variants.contains(existingStairs)) {
                    variants.add(existingStairs);
                    matrix.setReason(existingStairs, "Adopted existing stairs variant: " + Registries.BLOCK.getId(existingStairs));
                }
            } else {
                String cleanBase = baseId.getPath().replace("_planks", "").replace("_block", "");
                String path = cleanBase + "_stairs";
                Identifier id = new Identifier(Reshaped.MOD_ID, path);
                if (Registries.BLOCK.get(id) != Blocks.AIR) {
                    path = baseNamespace + "_" + baseId.getPath() + "_stairs";
                    id = new Identifier(Reshaped.MOD_ID, path);
                }
                if (Registries.BLOCK.get(id) == Blocks.AIR) {
                    // Use a neutral base state to avoid inheriting state-specific random tick logic
                    // from incompatible source blocks (e.g., chorus flower AGE property).
                    StairsBlock stairs = new StairsBlock(Blocks.STONE.getDefaultState(), VariantSettingsFactory.create(base));
                    Registry.register(Registries.BLOCK, id, stairs);
                    Registry.register(Registries.ITEM, id, new BlockItem(stairs, new Item.Settings()));
                    variants.add(stairs);
                    matrix.setReason(stairs, "Dynamically registered stairs variant for " + base.getName().getString());
                }
            }
        }
    }

    private static Block findExistingVariant(String baseName, String suffix, String baseNamespace) {
        List<String> variantsToTry = getVariantsToTry(baseName, suffix);

        for (String candidatePath : variantsToTry) {
            Identifier id = new Identifier(baseNamespace, candidatePath);
            Block block = Registries.BLOCK.get(id);
            if (block != Blocks.AIR && isValidVariant(block, suffix)) {
                return block;
            }
        }

        if (!baseNamespace.equals("minecraft")) {
            for (String candidatePath : variantsToTry) {
                Identifier id = new Identifier("minecraft", candidatePath);
                Block block = Registries.BLOCK.get(id);
                if (block != Blocks.AIR && isValidVariant(block, suffix)) {
                    return block;
                }
            }
        }

        Set<String> targetPaths = new HashSet<>(variantsToTry);
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (!targetPaths.contains(id.getPath())) continue;
            Block block = Registries.BLOCK.get(id);
            if (block != Blocks.AIR && isValidVariant(block, suffix)) {
                return block;
            }
        }

        return null;
    }

    private static List<String> getVariantsToTry(String baseName, String suffix) {
        List<String> variantsToTry = new ArrayList<>();
        variantsToTry.add(baseName + "_" + suffix);
        variantsToTry.add(baseName.replace("_block", "") + "_" + suffix);
        variantsToTry.add(baseName.replace("_planks", "") + "_" + suffix);

        if (baseName.contains("copper") && !baseName.contains("cut_")) {
            variantsToTry.add(baseName.replace("copper", "cut_copper") + "_" + suffix);
        }

        variantsToTry.add(baseName.replace("bricks", "brick") + "_" + suffix);
        variantsToTry.add(baseName.replace("tiles", "tile") + "_" + suffix);
        variantsToTry.add(baseName.replace("shingles", "shingle") + "_" + suffix);

        if (baseName.endsWith("s")) {
            variantsToTry.add(baseName.substring(0, baseName.length() - 1) + "_" + suffix);
        }
        return variantsToTry;
    }

    private static boolean isValidVariant(Block block, String suffix) {
        if ("slab".equals(suffix)) return block instanceof SlabBlock;
        if ("stairs".equals(suffix)) return block instanceof StairsBlock;
        return false;
    }
}
