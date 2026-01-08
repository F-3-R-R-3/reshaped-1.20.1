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
        // Phase 1: Create and Register Blocks
        for (Map.Entry<Block, List<Block>> entry : matrix.getMatrix().entrySet()) {
            Block baseBlock = entry.getKey();
            Identifier baseId = Registries.BLOCK.getId(baseBlock);
            String path = baseId.getPath() + "_vertical_slab";
            Identifier id = new Identifier(Reshaped.MOD_ID, path);

            VerticalSlabBlock verticalSlab;
            AbstractBlock.Settings settings = AbstractBlock.Settings.copy(baseBlock);

            if (baseBlock instanceof Oxidizable oxidizable) {
                verticalSlab = new OxidizableVerticalSlabBlock(oxidizable.getDegradationLevel(), settings);
            } else {
                verticalSlab = new VerticalSlabBlock(settings);
            }
            
            Registry.register(Registries.BLOCK, id, verticalSlab);
            Registry.register(Registries.ITEM, id, new BlockItem(verticalSlab, new Item.Settings()));
            
            entry.getValue().add(verticalSlab);
            matrix.setReason(verticalSlab, "Dynamically registered Vertical Slab for " + baseBlock.getName().getString());
            BASE_TO_SLAB.put(baseBlock, verticalSlab);
            Reshaped.LOGGER.info("Registered vertical slab for: " + baseId);

            // Inherit Flammability
            FlammableBlockRegistry flammableRegistry = FlammableBlockRegistry.getDefaultInstance();
            FlammableBlockRegistry.Entry flammableEntry = flammableRegistry.get(baseBlock);
            if (flammableEntry != null) {
                flammableRegistry.add(verticalSlab, flammableEntry.getBurnChance(), flammableEntry.getSpreadChance());
            }
        }

        // Phase 2: Link Related Features (Oxidation, Waxing, Stripping)
        registerRelations();
    }

    private static void registerRelations() {
        for (Map.Entry<Block, VerticalSlabBlock> entry : BASE_TO_SLAB.entrySet()) {
            Block base = entry.getKey();
            VerticalSlabBlock slab = entry.getValue();

            // 1. Oxidation Linking (Weathering & Scraping)
            Optional<Block> nextOxidation = Oxidizable.getIncreasedOxidationBlock(base);
            if (nextOxidation.isPresent() && BASE_TO_SLAB.containsKey(nextOxidation.get())) {
                VerticalSlabBlock nextSlab = BASE_TO_SLAB.get(nextOxidation.get());
                OxidizableBlocksRegistry.registerOxidizableBlockPair(slab, nextSlab);
            }

            // 2. Waxing Linking
            try {
                // Use Accessor
                Map<Block, Block> unwaxedToWaxed = net.f3rr3.reshaped.mixin.HoneycombItemAccessor.getUnwaxedToWaxedSupplier().get();
                Block waxedBase = unwaxedToWaxed.get(base);
                if (waxedBase != null && BASE_TO_SLAB.containsKey(waxedBase)) {
                    VerticalSlabBlock waxedSlab = BASE_TO_SLAB.get(waxedBase);
                    OxidizableBlocksRegistry.registerWaxableBlockPair(slab, waxedSlab);
                }
            } catch (Exception e) {
                 Reshaped.LOGGER.warn("Failed to register waxing for " + base, e);
            }

            // 3. Stripping (Logs/Woods)
            try {
                // Use Accessor
                Map<Block, Block> strippedBlocks = net.f3rr3.reshaped.mixin.AxeItemAccessor.getStrippedBlocks();
                Block strippedBase = strippedBlocks.get(base);
                if (strippedBase != null && BASE_TO_SLAB.containsKey(strippedBase)) {
                    VerticalSlabBlock strippedSlab = BASE_TO_SLAB.get(strippedBase);
                    StrippableBlockRegistry.register(slab, strippedSlab);
                }
            } catch (Exception e) {
                Reshaped.LOGGER.warn("Failed to register stripping for " + base, e);
            }
        }
    }
}
