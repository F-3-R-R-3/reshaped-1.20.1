package net.f3rr3.reshaped.registry;

import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class VariantRegistry {
    private static final List<BlockVariantType> VARIANTS = new ArrayList<>();

    static {
        VARIANTS.add(new VerticalSlabVariant());
    }

    public static void registerVariant(BlockVariantType variant) {
        VARIANTS.add(variant);
    }

    /**
     * Registers all applicable variants for a base block.
     */
    public static void registerAll(Block baseBlock, BlockMatrix matrix) {
        for (BlockVariantType variant : VARIANTS) {
            if (variant.appliesTo(baseBlock)) {
                variant.register(baseBlock, matrix);
            }
        }
    }

    public static Identifier getModelId(BlockState state) {
        for (BlockVariantType variant : VARIANTS) {
            Identifier id = variant.getModelId(state);
            if (id != null) return id;
        }
        return null;
    }

    public static ModelRotation getRotation(BlockState state) {
        for (BlockVariantType variant : VARIANTS) {
            ModelRotation rotation = variant.getRotation(state);
            if (rotation != null) return rotation;
        }
        return null;
    }

    public static String generateModelJson(String path, Block block) {
        for (BlockVariantType variant : VARIANTS) {
            String json = variant.generateModelJson(path, block);
            if (json != null) return json;
        }
        return null;
    }

    public static List<BlockVariantType> getVariants() {
        return VARIANTS;
    }
}
