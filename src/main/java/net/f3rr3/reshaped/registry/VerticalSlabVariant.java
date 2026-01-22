package net.f3rr3.reshaped.registry;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.OxidizableVerticalSlabBlock;
import net.f3rr3.reshaped.block.VerticalSlabBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.RuntimeResourceGenerator;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Oxidizable;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VerticalSlabVariant implements BlockVariantType {
    private static final Map<Block, VerticalSlabBlock> BASE_TO_SLAB = new HashMap<>();

    @Override
    public String getName() {
        return "vertical_slab";
    }

    @Override
    public boolean appliesTo(Block baseBlock) {
        // Vertical slabs apply to most solid blocks (handled by scanner logic mostly)
        return true;
    }

    @Override
    public void register(Block baseBlock, BlockMatrix matrix) {
        Identifier baseId = Registries.BLOCK.getId(baseBlock);
        
        // Cleaner naming: reshaped:oak_vertical_slab instead of reshaped:minecraft_oak_planks_vertical_slab
        String baseName = baseId.getPath().replace("_planks", "").replace("_block", "");
        String path = baseName + "_vertical_slab";
        Identifier id = new Identifier(Reshaped.MOD_ID, path);

        // Check if this ID is already taken by a block for a DIFFERENT base block
        Block existingAtId = Registries.BLOCK.get(id);
        if (existingAtId != Blocks.AIR) {
            Block existingBase = matrix.getBaseBlock(existingAtId);
            if (existingBase != null && existingBase != baseBlock) {
                // Collision! Fallback to namespace-prefixed name
                path = baseId.getNamespace() + "_" + baseId.getPath() + "_vertical_slab";
                id = new Identifier(Reshaped.MOD_ID, path);
            }
        }

        // Check if already registered
        if (Registries.BLOCK.get(id) != Blocks.AIR) {
            Block existing = Registries.BLOCK.get(id);
            if (existing instanceof VerticalSlabBlock verticalSlab) {
                BASE_TO_SLAB.putIfAbsent(baseBlock, verticalSlab);
                List<Block> variants = matrix.getMatrix().get(baseBlock);
                if (variants != null && !variants.contains(verticalSlab)) {
                    variants.add(verticalSlab);
                }
            }
            return;
        }

        VerticalSlabBlock verticalSlab;
        AbstractBlock.Settings settings = AbstractBlock.Settings.copy(baseBlock);

        if (baseBlock instanceof Oxidizable oxidizable) {
            verticalSlab = new OxidizableVerticalSlabBlock(oxidizable.getDegradationLevel(), settings);
        } else {
            verticalSlab = new VerticalSlabBlock(settings);
        }
        
        Registry.register(Registries.BLOCK, id, verticalSlab);
        Registry.register(Registries.ITEM, id, new BlockItem(verticalSlab, new Item.Settings()));
        
        matrix.addVariant(baseBlock, verticalSlab, true);
        matrix.setReason(verticalSlab, "Dynamically registered Vertical Slab for " + baseBlock.getName().getString());
        BASE_TO_SLAB.put(baseBlock, verticalSlab);
        Reshaped.LOGGER.info("Registered vertical slab for: " + baseId);

        // Inherit Flammability
        FlammableBlockRegistry flammableRegistry = FlammableBlockRegistry.getDefaultInstance();
        FlammableBlockRegistry.Entry flammableEntry = flammableRegistry.get(baseBlock);
        if (flammableEntry != null) {
            flammableRegistry.add(verticalSlab, flammableEntry.getBurnChance(), flammableEntry.getSpreadChance());
        }

        // Link relations
        linkRelations(baseBlock, verticalSlab);
        
        // Check other existing slabs
        for (Map.Entry<Block, VerticalSlabBlock> entry : BASE_TO_SLAB.entrySet()) {
            if (entry.getKey() != baseBlock) {
                linkRelations(entry.getKey(), entry.getValue());
            }
        }
    }

    private void linkRelations(Block base, VerticalSlabBlock slab) {
        // Oxidation Linking
        Optional<Block> nextOxidation = Oxidizable.getIncreasedOxidationBlock(base);
        if (nextOxidation.isPresent() && BASE_TO_SLAB.containsKey(nextOxidation.get())) {
            VerticalSlabBlock nextSlab = BASE_TO_SLAB.get(nextOxidation.get());
            OxidizableBlocksRegistry.registerOxidizableBlockPair(slab, nextSlab);
        }

        // Waxing Linking
        try {
            Map<Block, Block> unwaxedToWaxed = net.f3rr3.reshaped.mixin.HoneycombItemAccessor.getUnwaxedToWaxedSupplier().get();
            Block waxedBase = unwaxedToWaxed.get(base);
            if (waxedBase != null && BASE_TO_SLAB.containsKey(waxedBase)) {
                VerticalSlabBlock waxedSlab = BASE_TO_SLAB.get(waxedBase);
                OxidizableBlocksRegistry.registerWaxableBlockPair(slab, waxedSlab);
            }
            
            for (Map.Entry<Block, Block> entry : unwaxedToWaxed.entrySet()) {
                if (entry.getValue() == base && BASE_TO_SLAB.containsKey(entry.getKey())) {
                    VerticalSlabBlock unwaxedSlab = BASE_TO_SLAB.get(entry.getKey());
                    OxidizableBlocksRegistry.registerWaxableBlockPair(unwaxedSlab, slab);
                }
            }
        } catch (Exception ignored) {}

        // Stripping
        try {
            Map<Block, Block> strippedBlocks = net.f3rr3.reshaped.mixin.AxeItemAccessor.getStrippedBlocks();
            Block strippedBase = strippedBlocks.get(base);
            if (strippedBase != null && BASE_TO_SLAB.containsKey(strippedBase)) {
                VerticalSlabBlock strippedSlab = BASE_TO_SLAB.get(strippedBase);
                StrippableBlockRegistry.register(slab, strippedSlab);
            }

            for (Map.Entry<Block, Block> entry : strippedBlocks.entrySet()) {
                if (entry.getValue() == base && BASE_TO_SLAB.containsKey(entry.getKey())) {
                    VerticalSlabBlock unstrippedSlab = BASE_TO_SLAB.get(entry.getKey());
                    StrippableBlockRegistry.register(unstrippedSlab, slab);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public Identifier getModelId(BlockState state) {
        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        String path = id.getPath();

        if (path.endsWith("_vertical_slab")) {
            if (state.get(Properties.SLAB_TYPE) == SlabType.DOUBLE) {
                Block base = Reshaped.MATRIX != null ? Reshaped.MATRIX.getBaseBlock(block) : null;
                if (base != null) {
                    Identifier resolvedModelId = RuntimeResourceGenerator.resolveBlockModelId(base);
                    if (resolvedModelId != null) {
                        return resolvedModelId;
                    }
                    Identifier baseId = Registries.BLOCK.getId(base);
                    return new Identifier(baseId.getNamespace(), "block/" + baseId.getPath());
                }
            }
            Direction facing = state.get(Properties.HORIZONTAL_FACING);
            String dirSuffix = "_" + facing.getName();
            return new Identifier(Reshaped.MOD_ID, "block/" + path + dirSuffix);
        }
        return null;
    }

    @Override
    public ModelRotation getRotation(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id.getPath().endsWith("_vertical_slab")) {
            return ModelRotation.X0_Y0;
        }
        return null;
    }

    @Override
    public String generateModelJson(String path, Block block) {
        if (path.contains("_vertical_slab")) {
            String corePath = path;
            String directionSuffix = "";
            
            if (corePath.endsWith("_north")) { directionSuffix = "_north"; corePath = corePath.substring(0, corePath.length() - 6); }
            else if (corePath.endsWith("_south")) { directionSuffix = "_south"; corePath = corePath.substring(0, corePath.length() - 6); }
            else if (corePath.endsWith("_east")) { directionSuffix = "_east"; corePath = corePath.substring(0, corePath.length() - 5); }
            else if (corePath.endsWith("_west")) { directionSuffix = "_west"; corePath = corePath.substring(0, corePath.length() - 5); }

            Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
            if (baseBlock != null) {
                Identifier baseId = Registries.BLOCK.getId(baseBlock);
                Map<String, String> textures = RuntimeResourceGenerator.getModelTextures(baseBlock);
                String textureId = baseId.getNamespace() + ":block/" + baseId.getPath();
                
                String all = textures.getOrDefault("all", textureId);
                String up = textures.getOrDefault("up", textures.getOrDefault("top", textures.getOrDefault("end", all)));
                String down = textures.getOrDefault("down", textures.getOrDefault("bottom", textures.getOrDefault("end", all)));
                String side = textures.getOrDefault("side", all);
                String particle = textures.getOrDefault("particle", side);
                
                String texturesJson = "{\"parent\":\"minecraft:block/block\",\"textures\":{" +
                        "\"particle\":\"" + particle + "\"," +
                        "\"top\":\"" + up + "\"," +
                        "\"bottom\":\"" + down + "\"," +
                        "\"side\":\"" + side + "\"" +
                        "}";
                
                if (directionSuffix.isEmpty() || directionSuffix.equals("_north")) {
                    return texturesJson + ",\"elements\":[{\"from\":[0,0,8],\"to\":[16,16,16],\"faces\":{" +
                        "\"down\":{\"uv\":[0,0,16,8],\"texture\":\"#bottom\",\"cullface\":\"down\"}," +
                        "\"up\":{\"uv\":[0,8,16,16],\"texture\":\"#top\",\"cullface\":\"up\"}," +
                        "\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}," +
                        "\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"south\"}," +
                        "\"west\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"west\"}," +
                        "\"east\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"east\"}" +
                        "}}]}";
                } else if (directionSuffix.equals("_south")) {
                    return texturesJson + ",\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,8],\"faces\":{" +
                        "\"down\":{\"uv\":[0,8,16,16],\"texture\":\"#bottom\",\"cullface\":\"down\"}," +
                        "\"up\":{\"uv\":[0,0,16,8],\"texture\":\"#top\",\"cullface\":\"up\"}," +
                        "\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"north\"}," +
                        "\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}," +
                        "\"west\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"west\"}," +
                        "\"east\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"east\"}" +
                        "}}]}";
                } else if (directionSuffix.equals("_east")) {
                    return texturesJson + ",\"elements\":[{\"from\":[0,0,0],\"to\":[8,16,16],\"faces\":{" +
                        "\"down\":{\"uv\":[0,0,8,16],\"texture\":\"#bottom\",\"cullface\":\"down\"}," +
                        "\"up\":{\"uv\":[0,0,8,16],\"texture\":\"#top\",\"cullface\":\"up\"}," +
                        "\"north\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"north\"}," +
                        "\"south\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"south\"}," +
                        "\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"west\"}," +
                        "\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}" +
                        "}}]}";
                } else if (directionSuffix.equals("_west")) {
                    return texturesJson + ",\"elements\":[{\"from\":[8,0,0],\"to\":[16,16,16],\"faces\":{" +
                        "\"down\":{\"uv\":[8,0,16,16],\"texture\":\"#bottom\",\"cullface\":\"down\"}," +
                        "\"up\":{\"uv\":[8,0,16,16],\"texture\":\"#top\",\"cullface\":\"up\"}," +
                        "\"north\":{\"uv\":[0,0,8,16],\"texture\":\"#side\",\"cullface\":\"north\"}," +
                        "\"south\":{\"uv\":[8,0,16,16],\"texture\":\"#side\",\"cullface\":\"south\"}," +
                        "\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#side\"}," +
                        "\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#side\",\"cullface\":\"east\"}" +
                        "}}]}";
                }
            }
        }
        return null;
    }
}
