package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.Slab.ReshapedSlabBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VariantCompleter {

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
        String baseNamespace = baseId.getNamespace();

        if (!hasSlab) {
            Block existingSlab = findExistingVariant(baseId.getPath(), "slab", baseNamespace);
            if (existingSlab != null) {
                if (!variants.contains(existingSlab)) {
                    variants.add(existingSlab);
                    matrix.setReason(existingSlab, "Adopted existing slab variant: " + Registries.BLOCK.getId(existingSlab));
                    Reshaped.LOGGER.info("Adopted existing slab: {} for {}", Registries.BLOCK.getId(existingSlab), baseId);
                }
            } else {
                String cleanBase = baseId.getPath().replace("_planks", "").replace("_block", "");
                String path = cleanBase + "_slab";
                Identifier id = new Identifier(Reshaped.MOD_ID, path);

                // Collision Check
                if (Registries.BLOCK.get(id) != Blocks.AIR) {
                    path = baseNamespace + "_" + baseId.getPath() + "_slab";
                    id = new Identifier(Reshaped.MOD_ID, path);
                }

                if (Registries.BLOCK.get(id) == Blocks.AIR) {
                    SlabBlock slab = new ReshapedSlabBlock(AbstractBlock.Settings.copy(base));
                    Registry.register(Registries.BLOCK, id, slab);
                    Registry.register(Registries.ITEM, id, new BlockItem(slab, new Item.Settings()));
                    variants.add(slab);
                    matrix.setReason(slab, "Dynamically registered slab variant for " + base.getName().getString());
                    Reshaped.LOGGER.info("Registered new slab: {}", id);
                }
            }
        }

        if (!hasStair) {
            Block existingStairs = findExistingVariant(baseId.getPath(), "stairs", baseNamespace);
            if (existingStairs != null) {
                if (!variants.contains(existingStairs)) {
                    variants.add(existingStairs);
                    matrix.setReason(existingStairs, "Adopted existing stairs variant: " + Registries.BLOCK.getId(existingStairs));
                    Reshaped.LOGGER.info("Adopted existing stairs: {} for {}", Registries.BLOCK.getId(existingStairs), baseId);
                }
            } else {
                String cleanBase = baseId.getPath().replace("_planks", "").replace("_block", "");
                String path = cleanBase + "_stairs";
                Identifier id = new Identifier(Reshaped.MOD_ID, path);

                // Collision Check
                if (Registries.BLOCK.get(id) != Blocks.AIR) {
                    path = baseNamespace + "_" + baseId.getPath() + "_stairs";
                    id = new Identifier(Reshaped.MOD_ID, path);
                }

                if (Registries.BLOCK.get(id) == Blocks.AIR) {
                    StairsBlock stairs = new StairsBlock(base.getDefaultState(), AbstractBlock.Settings.copy(base));
                    Registry.register(Registries.BLOCK, id, stairs);
                    Registry.register(Registries.ITEM, id, new BlockItem(stairs, new Item.Settings()));
                    variants.add(stairs);
                    matrix.setReason(stairs, "Dynamically registered stairs variant for " + base.getName().getString());
                    Reshaped.LOGGER.info("Registered new stairs: {}", id);
                }
            }
        }
    }

    private static Block findExistingVariant(String baseName, String suffix, String baseNamespace) {
        // Try various common naming patterns to find existing variants
        List<String> variantsToTry = getVariantsToTry(baseName, suffix);

        // Search Phase 1: Try the same namespace as the base block (Fastest & most likely)
        for (String candidatePath : variantsToTry) {
            Identifier id = new Identifier(baseNamespace, candidatePath);
            Block block = Registries.BLOCK.get(id);
            if (block != Blocks.AIR) {
                if (isValidVariant(block, suffix)) return block;
            }
        }

        // Search Phase 2: Try Minecraft namespace (Very likely for vanilla materials)
        if (!baseNamespace.equals("minecraft")) {
            for (String candidatePath : variantsToTry) {
                Identifier id = new Identifier("minecraft", candidatePath);
                Block block = Registries.BLOCK.get(id);
                if (block != Blocks.AIR) {
                    if (isValidVariant(block, suffix)) return block;
                }
            }
        }

        // Search Phase 3: Global search (Slower but catches other mods with different namespaces)
        Set<String> targetPaths = new HashSet<>(variantsToTry);
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (targetPaths.contains(id.getPath())) {
                Block block = Registries.BLOCK.get(id);
                if (isValidVariant(block, suffix)) return block;
            }
        }

        return null;
    }

    private static @NotNull List<String> getVariantsToTry(String baseName, String suffix) {
        List<String> variantsToTry = new ArrayList<>();

        // 1. Direct patterns
        variantsToTry.add(baseName + "_" + suffix);
        variantsToTry.add(baseName.replace("_block", "") + "_" + suffix);
        variantsToTry.add(baseName.replace("_planks", "") + "_" + suffix);

        // 2. Copper patterns (cut_copper)
        if (baseName.contains("copper") && !baseName.contains("cut_")) {
            variantsToTry.add(baseName.replace("copper", "cut_copper") + "_" + suffix);
        }

        // 3. Material patterns
        variantsToTry.add(baseName.replace("bricks", "brick") + "_" + suffix);
        variantsToTry.add(baseName.replace("tiles", "tile") + "_" + suffix);
        variantsToTry.add(baseName.replace("shingles", "shingle") + "_" + suffix);

        // 4. Plurality
        if (baseName.endsWith("s")) {
            variantsToTry.add(baseName.substring(0, baseName.length() - 1) + "_" + suffix);
        }
        return variantsToTry;
    }

    private static boolean isValidVariant(Block block, String suffix) {
        if (suffix.equals("slab") && block instanceof SlabBlock) return true;
        return suffix.equals("stairs") && block instanceof StairsBlock;
    }
}
