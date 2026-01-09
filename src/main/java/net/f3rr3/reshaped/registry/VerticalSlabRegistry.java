package net.f3rr3.reshaped.registry;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.OxidizableBlocksRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.OxidizableVerticalSlabBlock;
import net.f3rr3.reshaped.block.VerticalSlabBlock;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.Oxidizable;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HoneycombItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VerticalSlabRegistry {
    private static final Map<Block, VerticalSlabBlock> BASE_TO_SLAB = new HashMap<>();

    public static void registerVerticalSlabs(BlockMatrix matrix) {
        for (Block baseBlock : matrix.getMatrix().keySet()) {
            registerVerticalSlabForBase(baseBlock, matrix);
        }
    }

    public static void registerVerticalSlabForBase(Block baseBlock, BlockMatrix matrix) {
        Identifier baseId = Registries.BLOCK.getId(baseBlock);
        
        // Cleaner naming: reshaped:oak_vertical_slab instead of reshaped:minecraft_oak_planks_vertical_slab
        String baseName = baseId.getPath().replace("_planks", "").replace("_block", "");
        String path = baseName + "_vertical_slab";
        Identifier id = new Identifier(Reshaped.MOD_ID, path);

        // Check if this ID is already taken by a block for a DIFFERENT base block
        // (If it's the same base block, we'll handle it in the next check)
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
            // If already registered, ensure it's in our map and matrix
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
        
        List<Block> variants = matrix.getMatrix().get(baseBlock);
        if (variants != null) {
            variants.add(verticalSlab);
        }
        matrix.setReason(verticalSlab, "Dynamically registered Vertical Slab for " + baseBlock.getName().getString());
        BASE_TO_SLAB.put(baseBlock, verticalSlab);
        Reshaped.LOGGER.info("Registered vertical slab for: " + baseId);

        // Inherit Flammability
        FlammableBlockRegistry flammableRegistry = FlammableBlockRegistry.getDefaultInstance();
        FlammableBlockRegistry.Entry flammableEntry = flammableRegistry.get(baseBlock);
        if (flammableEntry != null) {
            flammableRegistry.add(verticalSlab, flammableEntry.getBurnChance(), flammableEntry.getSpreadChance());
        }

        // Link relations for this new slab
        linkRelations(baseBlock, verticalSlab);
        
        // Also check if other existing slabs should link to this one
        for (Map.Entry<Block, VerticalSlabBlock> entry : BASE_TO_SLAB.entrySet()) {
            if (entry.getKey() != baseBlock) {
                linkRelations(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void linkRelations(Block base, VerticalSlabBlock slab) {
        // 1. Oxidation Linking (Weathering & Scraping)
        Optional<Block> nextOxidation = Oxidizable.getIncreasedOxidationBlock(base);
        if (nextOxidation.isPresent() && BASE_TO_SLAB.containsKey(nextOxidation.get())) {
            VerticalSlabBlock nextSlab = BASE_TO_SLAB.get(nextOxidation.get());
            OxidizableBlocksRegistry.registerOxidizableBlockPair(slab, nextSlab);
        }

        // 2. Waxing Linking
        try {
            Map<Block, Block> unwaxedToWaxed = net.f3rr3.reshaped.mixin.HoneycombItemAccessor.getUnwaxedToWaxedSupplier().get();
            Block waxedBase = unwaxedToWaxed.get(base);
            if (waxedBase != null && BASE_TO_SLAB.containsKey(waxedBase)) {
                VerticalSlabBlock waxedSlab = BASE_TO_SLAB.get(waxedBase);
                OxidizableBlocksRegistry.registerWaxableBlockPair(slab, waxedSlab);
            }
            
            // Reverse check: is this the waxed version of something?
            for (Map.Entry<Block, Block> entry : unwaxedToWaxed.entrySet()) {
                if (entry.getValue() == base && BASE_TO_SLAB.containsKey(entry.getKey())) {
                    VerticalSlabBlock unwaxedSlab = BASE_TO_SLAB.get(entry.getKey());
                    OxidizableBlocksRegistry.registerWaxableBlockPair(unwaxedSlab, slab);
                }
            }
        } catch (Exception e) {
            // Reshaped.LOGGER.warn("Failed to register waxing for " + base, e);
        }

        // 3. Stripping (Logs/Woods)
        try {
            Map<Block, Block> strippedBlocks = net.f3rr3.reshaped.mixin.AxeItemAccessor.getStrippedBlocks();
            Block strippedBase = strippedBlocks.get(base);
            if (strippedBase != null && BASE_TO_SLAB.containsKey(strippedBase)) {
                VerticalSlabBlock strippedSlab = BASE_TO_SLAB.get(strippedBase);
                StrippableBlockRegistry.register(slab, strippedSlab);
            }

            // Reverse check
            for (Map.Entry<Block, Block> entry : strippedBlocks.entrySet()) {
                if (entry.getValue() == base && BASE_TO_SLAB.containsKey(entry.getKey())) {
                    VerticalSlabBlock unstrippedSlab = BASE_TO_SLAB.get(entry.getKey());
                    StrippableBlockRegistry.register(unstrippedSlab, slab);
                }
            }
        } catch (Exception e) {
            // Reshaped.LOGGER.warn("Failed to register stripping for " + base, e);
        }
    }

    private static void registerRelations() {
        for (Map.Entry<Block, VerticalSlabBlock> entry : BASE_TO_SLAB.entrySet()) {
            linkRelations(entry.getKey(), entry.getValue());
        }
    }
}
