package net.f3rr3.reshaped.registry;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

final class VariantSettingsFactory {
    private VariantSettingsFactory() {
    }

    static AbstractBlock.Settings create(Block baseBlock) {
        // Avoid copying full settings from arbitrary mod blocks.
        // Some blocks carry state-dependent callbacks that can crash when reused on reshaped variants.
        AbstractBlock.Settings settings = AbstractBlock.Settings.create()
                .strength(2.0f)
                .nonOpaque();

        BlockState baseState = baseBlock.getDefaultState();
        if (baseState.isToolRequired()) {
            settings.requiresTool();
        }

        return settings;
    }
}
