package net.f3rr3.reshaped.datagen;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.VerticalSlabBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

public class ReshapedLanguageProvider extends FabricLanguageProvider {
    public ReshapedLanguageProvider(FabricDataOutput dataOutput) {
        super(dataOutput);
    }

    @Override
    public void generateTranslations(TranslationBuilder translationBuilder) {
        if (Reshaped.MATRIX == null) return;

        for (Map.Entry<Block, List<Block>> entry : Reshaped.MATRIX.getMatrix().entrySet()) {
            Block baseBlock = entry.getKey();
            String baseName = baseBlock.getName().getString();

            for (Block variant : entry.getValue()) {
                Identifier variantId = Registries.BLOCK.getId(variant);
                if (!variantId.getNamespace().equals(Reshaped.MOD_ID)) continue;

                String variantName = formatName(baseName, variant);
                translationBuilder.add(variant, variantName);
            }
        }
    }

    private String formatName(String baseName, Block variant) {
        // Clean up base name for common suffixes
        String cleanedBase = baseName
            .replace(" Planks", "")
            .replace(" Block", "")
            .replace(" Bricks", " Brick");

        if (variant instanceof VerticalSlabBlock) {
            return cleanedBase + " Vertical Slab";
        } else if (variant instanceof SlabBlock) {
            return cleanedBase + " Slab";
        } else if (variant instanceof StairsBlock) {
            return cleanedBase + " Stairs";
        }
        
        return baseName + " Variant";
    }
}
