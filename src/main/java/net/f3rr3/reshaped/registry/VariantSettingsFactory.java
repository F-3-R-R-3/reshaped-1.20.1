package net.f3rr3.reshaped.registry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

final class VariantSettingsFactory {
    private VariantSettingsFactory() {
    }

    static AbstractBlock.Settings create(Block baseBlock) {
        BlockState baseState = baseBlock.getDefaultState();
        
        // Copy most settings from the base block (hardness, resistance, sounds, etc.).
        // This ensures variants inherit the correct durability and physical properties.
        AbstractBlock.Settings settings = AbstractBlock.Settings.copy(baseBlock)
                .nonOpaque(); // Reshaped variants are never full blocks

        // Safely override functional properties that might otherwise crash if they 
        // attempt to access properties from the variant's BlockState that don't exist.
        // We delegate these to the base block's default state.
        settings.luminance(state -> baseState.getLuminance())
                .allowsSpawning((state, world, pos, type) -> baseState.allowsSpawning(world, pos, type))
                .solidBlock((state, world, pos) -> false)
                .suffocates((state, world, pos) -> false)
                .blockVision((state, world, pos) -> false);

        // For mapColor, we try to use the value from the default state.
        try {
            settings.mapColor(baseState.getMapColor(null, null));
        } catch (Exception ignored) {
            // Fallback if the block's getMapColor requires non-null view/pos.
            // copy() would have already copied the base block's map color logic.
        }

        return settings;
    }
}
