package net.f3rr3.reshaped.registry;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.OxidizableVerticalStairsBlock;
import net.f3rr3.reshaped.block.VerticalStairsBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.f3rr3.reshaped.block.VerticalStairsBlock.VerticalStairOrientation;
import net.minecraft.block.Oxidizable;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VerticalStairsVariant implements BlockVariantType {
    private static final Map<Block, VerticalStairsBlock> BASE_TO_STAIRS = new HashMap<>();

    @Override
    public String getName() {
        return "vertical_stairs";
    }

    @Override
    public boolean appliesTo(Block baseBlock) {
        // Apply to blocks that can normally have stairs
        return true;
    }

    @Override
    public void register(Block baseBlock, BlockMatrix matrix) {
        Identifier baseId = Registries.BLOCK.getId(baseBlock);
        String baseName = baseId.getPath().replace("_planks", "").replace("_block", "");
        String path = baseName + "_vertical_stairs";
        Identifier id = new Identifier(Reshaped.MOD_ID, path);

        // Collision Check
        Block existingAtId = Registries.BLOCK.get(id);
        if (existingAtId != Blocks.AIR) {
            Block existingBase = matrix.getBaseBlock(existingAtId);
            if (existingBase != null && existingBase != baseBlock) {
                path = baseId.getNamespace() + "_" + baseId.getPath() + "_vertical_stairs";
                id = new Identifier(Reshaped.MOD_ID, path);
            }
        }

        if (Registries.BLOCK.get(id) != Blocks.AIR) {
            Block existing = Registries.BLOCK.get(id);
            if (existing instanceof VerticalStairsBlock verticalStairs) {
                BASE_TO_STAIRS.putIfAbsent(baseBlock, verticalStairs);
                List<Block> variants = matrix.getMatrix().get(baseBlock);
                if (variants != null && !variants.contains(verticalStairs)) {
                    variants.add(verticalStairs);
                }
            }
            return;
        }

        VerticalStairsBlock verticalStairs;
        AbstractBlock.Settings settings = AbstractBlock.Settings.copy(baseBlock);

        if (baseBlock instanceof Oxidizable oxidizable) {
            verticalStairs = new OxidizableVerticalStairsBlock(oxidizable.getDegradationLevel(), settings);
        } else {
            verticalStairs = new VerticalStairsBlock(settings);
        }

        Registry.register(Registries.BLOCK, id, verticalStairs);
        Registry.register(Registries.ITEM, id, new BlockItem(verticalStairs, new Item.Settings()));

        matrix.addVariant(baseBlock, verticalStairs, true);
        matrix.setReason(verticalStairs, "Dynamically registered Vertical Stairs for " + baseBlock.getName().getString());
        BASE_TO_STAIRS.put(baseBlock, verticalStairs);
        
        // Inherit Flammability
        FlammableBlockRegistry flammableRegistry = FlammableBlockRegistry.getDefaultInstance();
        FlammableBlockRegistry.Entry flammableEntry = flammableRegistry.get(baseBlock);
        if (flammableEntry != null) {
            flammableRegistry.add(verticalStairs, flammableEntry.getBurnChance(), flammableEntry.getSpreadChance());
        }

        linkRelations(baseBlock, verticalStairs);
        for (Map.Entry<Block, VerticalStairsBlock> entry : BASE_TO_STAIRS.entrySet()) {
            if (entry.getKey() != baseBlock) {
                linkRelations(entry.getKey(), entry.getValue());
            }
        }
    }

    private void linkRelations(Block base, VerticalStairsBlock stairs) {
        // Oxidation
        Optional<Block> nextOxidation = Oxidizable.getIncreasedOxidationBlock(base);
        if (nextOxidation.isPresent() && BASE_TO_STAIRS.containsKey(nextOxidation.get())) {
            VerticalStairsBlock nextStairs = BASE_TO_STAIRS.get(nextOxidation.get());
            OxidizableBlocksRegistry.registerOxidizableBlockPair(stairs, nextStairs);
        }

        // Waxing
        try {
            Map<Block, Block> unwaxedToWaxed = net.f3rr3.reshaped.mixin.HoneycombItemAccessor.getUnwaxedToWaxedSupplier().get();
            Block waxedBase = unwaxedToWaxed.get(base);
            if (waxedBase != null && BASE_TO_STAIRS.containsKey(waxedBase)) {
                VerticalStairsBlock waxedStairs = BASE_TO_STAIRS.get(waxedBase);
                OxidizableBlocksRegistry.registerWaxableBlockPair(stairs, waxedStairs);
            }
            for (Map.Entry<Block, Block> entry : unwaxedToWaxed.entrySet()) {
                if (entry.getValue() == base && BASE_TO_STAIRS.containsKey(entry.getKey())) {
                    VerticalStairsBlock unwaxedStairs = BASE_TO_STAIRS.get(entry.getKey());
                    OxidizableBlocksRegistry.registerWaxableBlockPair(unwaxedStairs, stairs);
                }
            }
        } catch (Exception ignored) {}

        // Stripping
        try {
            Map<Block, Block> strippedBlocks = net.f3rr3.reshaped.mixin.AxeItemAccessor.getStrippedBlocks();
            Block strippedBase = strippedBlocks.get(base);
            if (strippedBase != null && BASE_TO_STAIRS.containsKey(strippedBase)) {
                VerticalStairsBlock strippedStairs = BASE_TO_STAIRS.get(strippedBase);
                StrippableBlockRegistry.register(stairs, strippedStairs);
            }
            for (Map.Entry<Block, Block> entry : strippedBlocks.entrySet()) {
                if (entry.getValue() == base && BASE_TO_STAIRS.containsKey(entry.getKey())) {
                    VerticalStairsBlock unstrippedStairs = BASE_TO_STAIRS.get(entry.getKey());
                    StrippableBlockRegistry.register(unstrippedStairs, stairs);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public Identifier getModelId(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof VerticalStairsBlock) {
            Identifier id = Registries.BLOCK.getId(block);
            String path = id.getPath();
            VerticalStairOrientation orientation = state.get(VerticalStairsBlock.ORIENTATION);
            return new Identifier(Reshaped.MOD_ID, "block/" + path + "_" + orientation.asString());
        }
        return null;
    }

    @Override
    public ModelRotation getRotation(BlockState state) {
        if (state.getBlock() instanceof VerticalStairsBlock) {
            // Rotations are handled by orientation states (quadrants)
            return ModelRotation.X0_Y0;
        }
        return null;
    }

    @Override
    public String generateModelJson(String path, Block block) {
        if (path.contains("_vertical_stairs")) {
            Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
            if (baseBlock != null) {
                Identifier baseId = Registries.BLOCK.getId(baseBlock);
                String textureId = baseId.getNamespace() + ":block/" + baseId.getPath();
                
                String orientationName = "";
                if (path.endsWith("_plus_x_plus_y")) orientationName = "plus_x_plus_y";
                else if (path.endsWith("_minus_x_plus_y")) orientationName = "minus_x_plus_y";
                else if (path.endsWith("_plus_x_minus_y")) orientationName = "plus_x_minus_y";
                else if (path.endsWith("_minus_x_minus_y")) orientationName = "minus_x_minus_y";

                if (!orientationName.isEmpty()) {
                    String boxes = switch (orientationName) {
                        case "plus_x_plus_y" -> // Missing South-East (+X +Z)
                            "{\"from\":[0,0,0],\"to\":[8,16,16],\"faces\":{\"north\":{\"uv\":[8,0,16,16],\"texture\":\"#side\"},\"south\":{\"uv\":[0,0,8,16],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"west\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"up\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"up\"},\"down\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"down\"}}}," +
                            "{\"from\":[8,0,0],\"to\":[16,16,8],\"faces\":{\"north\":{\"uv\":[0,0,8,16],\"texture\":\"#side\"},\"south\":{\"uv\":[8,0,16,16],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"east\"},\"up\":{\"uv\":[8,0,16,8],\"texture\":\"#side\",\"cullface\":\"up\"},\"down\":{\"uv\":[8,8,16,16],\"texture\":\"#side\",\"cullface\":\"down\"}}}";
                        case "minus_x_plus_y" -> // Missing South-West (-X +Z)
                            "{\"from\":[8,0,0],\"to\":[16,16,16],\"faces\":{\"north\":{\"uv\":[0,0,8,16],\"texture\":\"#side\"},\"south\":{\"uv\":[8,0,16,16],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"east\"},\"up\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"up\"},\"down\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"down\"}}}," +
                            "{\"from\":[0,0,0],\"to\":[8,16,8],\"faces\":{\"north\":{\"uv\":[8,0,16,16],\"texture\":\"#side\"},\"south\":{\"uv\":[0,0,8,16],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"west\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"up\":{\"uv\":[0,0,8,8],\"texture\":\"#side\",\"cullface\":\"up\"},\"down\":{\"uv\":[0,8,8,16],\"texture\":\"#side\",\"cullface\":\"down\"}}}";
                        case "plus_x_minus_y" -> // Missing North-East (+X -Z)
                            "{\"from\":[0,0,0],\"to\":[8,16,16],\"faces\":{\"north\":{\"uv\":[8,0,16,16],\"texture\":\"#side\"},\"south\":{\"uv\":[0,0,8,16],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"west\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"up\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"up\"},\"down\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"down\"}}}," +
                            "{\"from\":[8,0,8],\"to\":[16,16,16],\"faces\":{\"north\":{\"uv\":[0,0,8,16],\"texture\":\"#side\"},\"south\":{\"uv\":[8,0,16,16],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"east\"},\"up\":{\"uv\":[8,8,16,16],\"texture\":\"#side\",\"cullface\":\"up\"},\"down\":{\"uv\":[8,0,16,8],\"texture\":\"#side\",\"cullface\":\"down\"}}}";
                        case "minus_x_minus_y" -> // Missing North-West (-X -Z)
                            "{\"from\":[8,0,0],\"to\":[16,16,16],\"faces\":{\"north\":{\"uv\":[0,0,8,16],\"texture\":\"#side\"},\"south\":{\"uv\":[8,0,16,16],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"east\"},\"up\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"up\"},\"down\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"down\"}}}," +
                            "{\"from\":[0,0,8],\"to\":[8,16,16],\"faces\":{\"north\":{\"uv\":[8,0,16,16],\"texture\":\"#side\"},\"south\":{\"uv\":[0,0,8,16],\"texture\":\"#side\"},\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"west\"},\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"},\"up\":{\"uv\":[0,8,8,16],\"texture\":\"#side\",\"cullface\":\"up\"},\"down\":{\"uv\":[0,0,8,8],\"texture\":\"#side\",\"cullface\":\"down\"}}}";
                        default -> "";
                    };

                    return "{\"parent\":\"minecraft:block/block\",\"textures\":{\"side\":\"" + textureId + "\",\"particle\":\"" + textureId + "\"},\"elements\":[" +
                            boxes + "]}";
                }
            }
        }
        return null;
    }
}
