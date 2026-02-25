package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.VerticalSlab.OxidizableVerticalSlabBlock;
import net.f3rr3.reshaped.block.VerticalSlab.VerticalSlabBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.f3rr3.reshaped.util.RuntimeResourceGenerator;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.Oxidizable;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

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
        AbstractBlock.Settings settings = VariantSettingsFactory.create(baseBlock);

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
        Reshaped.LOGGER.info("Registered vertical slab for: {}", baseId);

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
        } catch (Exception ignored) {
        }

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
        } catch (Exception ignored) {
        }
    }

    @Override
    public String generateModelJson(String path, Block block) {
        String cleanPath = RuntimeResourceGenerator.stripRandomVariantSuffix(path);
        int randomIndex = RuntimeResourceGenerator.extractRandomVariantIndex(path);
        if (cleanPath.contains("_vertical_slab")) {
            Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
            if (baseBlock != null) {
                Map<String, String> textures = RuntimeResourceGenerator.getModelTextures(baseBlock, randomIndex);
                return RuntimeResourceGenerator.generateModelFromTemplate("block/vertical_slab", textures);
            }
        }
        return null;
    }
}
