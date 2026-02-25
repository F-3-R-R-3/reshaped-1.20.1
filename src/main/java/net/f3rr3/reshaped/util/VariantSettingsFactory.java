package net.f3rr3.reshaped.util;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;

public final class VariantSettingsFactory {
    private VariantSettingsFactory() {
    }

    public static AbstractBlock.Settings forBase(Block baseBlock) {
        int baseLuminance = 0;
        try {
            baseLuminance = baseBlock.getDefaultState().getLuminance();
        } catch (Throwable ignored) {
        }

        final int luminance = Math.max(0, Math.min(15, baseLuminance));
        return AbstractBlock.Settings.copy(baseBlock).luminance(state -> luminance);
    }
}

