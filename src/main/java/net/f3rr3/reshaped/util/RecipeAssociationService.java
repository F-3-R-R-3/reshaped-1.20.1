package net.f3rr3.reshaped.util;

import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.*;

public final class RecipeAssociationService {
    private RecipeAssociationService() {
    }

    public static Map<Block, Association> buildAssociations(MinecraftServer server, Set<Block> baseCandidates) {
        Map<Block, List<Association>> proposals = new HashMap<>();
        RecipeManager recipeManager = server.getRecipeManager();

        for (Recipe<?> recipe : recipeManager.values()) {
            ItemStack outputStack = recipe.getOutput(server.getRegistryManager());
            if (outputStack.isEmpty() || !(outputStack.getItem() instanceof BlockItem outputItem)) {
                continue;
            }

            Block outputBlock = outputItem.getBlock();
            if (isIgnoredForMatrix(outputBlock)) {
                continue;
            }
            if (baseCandidates.contains(outputBlock)) {
                continue;
            }

            Association association = analyzeRecipe(recipe, outputBlock, baseCandidates);
            if (association != null) {
                proposals.computeIfAbsent(outputBlock, key -> new ArrayList<>()).add(association);
            }
        }

        return resolveBestAssociations(proposals);
    }

    private static Association analyzeRecipe(Recipe<?> recipe, Block outputBlock, Set<Block> baseCandidates) {
        if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
            Ingredient ingredient = stonecuttingRecipe.getIngredients().isEmpty() ? Ingredient.EMPTY : stonecuttingRecipe.getIngredients().get(0);
            Block single = resolveSingleBase(ingredient, baseCandidates);
            if (single != null) {
                return new Association(single, "Associated via stonecutting recipe from " + Registries.BLOCK.getId(single), 90);
            }
            return null;
        }

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return analyzeShapedRecipe(shapedRecipe, outputBlock, baseCandidates);
        }

        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            return analyzeShapelessRecipe(shapelessRecipe, outputBlock, baseCandidates);
        }

        return null;
    }

    private static Association analyzeShapedRecipe(ShapedRecipe recipe, Block outputBlock, Set<Block> baseCandidates) {
        int width = recipe.getWidth();
        int height = recipe.getHeight();
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return null;
        String outputPath = Registries.BLOCK.getId(outputBlock).getPath();

        Map<Block, Integer> baseCounts = new HashMap<>();
        for (Ingredient ingredient : ingredients) {
            Block base = resolveSingleBase(ingredient, baseCandidates);
            if (base != null) {
                baseCounts.merge(base, 1, Integer::sum);
            }
        }

        if (outputBlock instanceof SlabBlock && width == 3 && height == 1) {
            Block base = dominantBase(baseCounts, 3);
            if (base != null) {
                return new Association(base, "Associated via slab-shaped recipe", 120);
            }
        }

        if (outputBlock instanceof StairsBlock && width == 3 && height == 3) {
            Block base = dominantBase(baseCounts, 6);
            if (base != null) {
                return new Association(base, "Associated via stairs-shaped recipe", 120);
            }
        }

        if (outputBlock instanceof WallBlock && width == 3 && height == 2) {
            Block base = dominantBase(baseCounts, 6);
            if (base != null) {
                return new Association(base, "Associated via wall recipe", 105);
            }
        }

        if (outputBlock instanceof PaneBlock && width == 3 && height == 2) {
            Block base = dominantBase(baseCounts, 6);
            if (base != null) {
                return new Association(base, "Associated via pane recipe", 105);
            }
        }

        if (outputBlock instanceof TrapdoorBlock || outputPath.endsWith("_trapdoor")) {
            int minCount = switch (width + "x" + height) {
                case "3x2", "2x3" -> 6; // plank-style trapdoors
                case "2x2" -> 4;        // metal-style trapdoors (e.g. iron)
                default -> -1;
            };
            if (minCount > 0) {
                Block base = dominantBase(baseCounts, minCount);
                if (base != null) {
                    return new Association(base, "Associated via trapdoor recipe", 105);
                }
            }
        }

        if (outputBlock instanceof DoorBlock && width == 2 && height == 3) {
            Block base = dominantBase(baseCounts, 6);
            if (base != null) {
                return new Association(base, "Associated via door recipe", 105);
            }
        }

        if ((outputBlock instanceof AbstractPressurePlateBlock || outputPath.endsWith("_pressure_plate"))
                && ((width == 2 && height == 1) || (width == 1 && height == 2))) {
            Block base = dominantBase(baseCounts, 2);
            if (base != null) {
                return new Association(base, "Associated via pressure plate recipe", 100);
            }
        }

        if ((outputBlock instanceof CarpetBlock || outputPath.endsWith("_carpet"))
                && ((width == 2 && height == 1) || (width == 1 && height == 2))) {
            Block base = dominantBase(baseCounts, 2);
            if (base != null) {
                return new Association(base, "Associated via carpet recipe", 100);
            }
        }

        if (outputBlock instanceof ButtonBlock && width == 1 && height == 1) {
            Block base = dominantBase(baseCounts, 1);
            if (base != null) {
                return new Association(base, "Associated via button recipe", 100);
            }
        }

        if (outputBlock instanceof FenceBlock && width == 3 && height == 2) {
            Block base = dominantBase(baseCounts, 4);
            if (base != null) {
                return new Association(base, "Associated via fence recipe", 95);
            }
        }

        if (outputBlock instanceof FenceGateBlock && width == 3 && height == 2) {
            Block base = dominantBase(baseCounts, 2);
            if (base != null) {
                return new Association(base, "Associated via fence gate recipe", 95);
            }
        }

        return null;
    }

    private static Association analyzeShapelessRecipe(ShapelessRecipe recipe, Block outputBlock, Set<Block> baseCandidates) {
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return null;

        String outputPath = Registries.BLOCK.getId(outputBlock).getPath();
        Map<Block, Integer> baseCounts = new HashMap<>();
        for (Ingredient ingredient : ingredients) {
            Block base = resolveSingleBase(ingredient, baseCandidates);
            if (base != null) {
                baseCounts.merge(base, 1, Integer::sum);
            }
        }

        if (outputBlock instanceof ButtonBlock || outputPath.endsWith("_button")) {
            Block base = dominantBase(baseCounts, 1);
            if (base != null) {
                return new Association(base, "Associated via button recipe", 100);
            }
        }

        return null;
    }

    private static Block dominantBase(Map<Block, Integer> counts, int minCount) {
        Block best = null;
        int bestCount = 0;
        boolean tie = false;

        for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
            int value = entry.getValue();
            if (value > bestCount) {
                best = entry.getKey();
                bestCount = value;
                tie = false;
            } else if (value == bestCount && value > 0) {
                tie = true;
            }
        }

        if (tie || best == null || bestCount < minCount) {
            return null;
        }
        return best;
    }

    private static Map<Block, Association> resolveBestAssociations(Map<Block, List<Association>> proposals) {
        Map<Block, Association> resolved = new HashMap<>();

        for (Map.Entry<Block, List<Association>> entry : proposals.entrySet()) {
            Block output = entry.getKey();
            List<Association> associations = entry.getValue();

            Map<Block, Association> bestByBase = new HashMap<>();
            for (Association association : associations) {
                Association existing = bestByBase.get(association.base());
                if (existing == null || association.score() > existing.score()) {
                    bestByBase.put(association.base(), association);
                }
            }

            Association best = null;
            boolean tie = false;
            for (Association candidate : bestByBase.values()) {
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                    tie = false;
                } else if (candidate.score() == best.score()) {
                    tie = true;
                }
            }

            if (!tie && best != null) {
                resolved.put(output, best);
            }
        }

        return resolved;
    }

    private static Block resolveSingleBase(Ingredient ingredient, Set<Block> baseCandidates) {
        if (ingredient == null || ingredient.isEmpty()) return null;

        Block resolved = null;
        for (ItemStack stack : ingredient.getMatchingStacks()) {
            Block block = resolveCandidateBlock(stack.getItem(), baseCandidates);
            if (block == null) {
                continue;
            }
            if (resolved == null) {
                resolved = block;
            } else if (resolved != block) {
                return null;
            }
        }
        return resolved;
    }

    private static Block resolveCandidateBlock(Item item, Set<Block> baseCandidates) {
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return baseCandidates.contains(block) ? block : null;
        }

        // Map compressed crafting materials (e.g. iron_ingot) to their storage block (iron_block).
        Identifier itemId = Registries.ITEM.getId(item);
        String path = itemId.getPath();
        if (!path.endsWith("_ingot")) {
            return null;
        }

        String blockPath = path.substring(0, path.length() - "_ingot".length()) + "_block";
        Identifier blockId = new Identifier(itemId.getNamespace(), blockPath);
        if (!Registries.BLOCK.containsId(blockId)) {
            return null;
        }

        Block block = Registries.BLOCK.get(blockId);
        return baseCandidates.contains(block) ? block : null;
    }

    private static boolean isIgnoredForMatrix(Block block) {
        if (block == null) return true;

        Identifier id = Registries.BLOCK.getId(block);
        String namespace = id.getNamespace();
        String path = id.getPath();
        String className = block.getClass().getName().toLowerCase(Locale.ROOT);

        return namespace.contains("copycat")
                || path.contains("copycat")
                || className.contains("copycat");
    }

    public record Association(Block base, String reason, int score) {
    }
}
