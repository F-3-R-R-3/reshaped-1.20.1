package net.f3rr3.reshaped.datagen;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.VerticalSlabBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.block.Block;
import net.minecraft.data.client.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import net.minecraft.util.math.Direction;
import java.util.List;
import java.util.Map;

public class ReshapedModelProvider extends FabricModelProvider {
    public ReshapedModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {
        if (Reshaped.MATRIX == null) return;

        for (Map.Entry<Block, List<Block>> entry : Reshaped.MATRIX.getMatrix().entrySet()) {
            Block baseBlock = entry.getKey();
            if (baseBlock == null || baseBlock.getDefaultState().isAir()) continue;

            for (Block variant : entry.getValue()) {
                Identifier variantId = Registries.BLOCK.getId(variant);
                if (!variantId.getNamespace().equals(Reshaped.MOD_ID)) continue;

                TextureMap textures = new TextureMap()
                    .put(TextureKey.BOTTOM, TextureMap.getId(baseBlock))
                    .put(TextureKey.TOP, TextureMap.getId(baseBlock))
                    .put(TextureKey.SIDE, TextureMap.getId(baseBlock));

                if (variant instanceof VerticalSlabBlock) {
                    registerVerticalSlab(blockStateModelGenerator, baseBlock, variant);
                } else if (variant instanceof SlabBlock slab) {
                    Identifier slabModelId = Models.SLAB.upload(slab, textures, blockStateModelGenerator.modelCollector);
                    Identifier slabTopModelId = Models.SLAB_TOP.upload(slab, textures, blockStateModelGenerator.modelCollector);
                    blockStateModelGenerator.blockStateCollector.accept(BlockStateModelGenerator.createSlabBlockState(slab, slabModelId, slabTopModelId, Registries.BLOCK.getId(baseBlock)));
                    blockStateModelGenerator.registerParentedItemModel(slab, slabModelId);
                } else if (variant instanceof StairsBlock stairs) {
                    Identifier stairsId = Models.STAIRS.upload(stairs, textures, blockStateModelGenerator.modelCollector);
                    Identifier innerStairsId = Models.INNER_STAIRS.upload(stairs, textures, blockStateModelGenerator.modelCollector);
                    Identifier outerStairsId = Models.OUTER_STAIRS.upload(stairs, textures, blockStateModelGenerator.modelCollector);
                    blockStateModelGenerator.blockStateCollector.accept(BlockStateModelGenerator.createStairsBlockState(stairs, stairsId, innerStairsId, outerStairsId));
                    blockStateModelGenerator.registerParentedItemModel(stairs, stairsId);
                }
            }
        }
    }

    private void registerVerticalSlab(BlockStateModelGenerator generator, Block base, Block verticalSlab) {
        // Create a more robust texture map that covers all faces of a slab
        // We use the base block's main texture for everything as a fallback
        Identifier textureId = TextureMap.getId(base);
        TextureMap textures = new TextureMap()
            .put(TextureKey.BOTTOM, textureId)
            .put(TextureKey.TOP, textureId)
            .put(TextureKey.SIDE, textureId);
        
        // Use the standard slab model but upload it specifically for this block
        Identifier modelId = Models.SLAB.upload(verticalSlab, textures, generator.modelCollector);

        // Create a horizontal facing blockstate that rotates the slab to be vertical
        generator.blockStateCollector.accept(VariantsBlockStateSupplier.create(verticalSlab)
            .coordinate(BlockStateVariantMap.create(VerticalSlabBlock.FACING)
                .register(Direction.NORTH, BlockStateVariant.create().put(VariantSettings.MODEL, modelId).put(VariantSettings.X, VariantSettings.Rotation.R90))
                .register(Direction.SOUTH, BlockStateVariant.create().put(VariantSettings.MODEL, modelId).put(VariantSettings.X, VariantSettings.Rotation.R90).put(VariantSettings.Y, VariantSettings.Rotation.R180))
                .register(Direction.EAST, BlockStateVariant.create().put(VariantSettings.MODEL, modelId).put(VariantSettings.X, VariantSettings.Rotation.R90).put(VariantSettings.Y, VariantSettings.Rotation.R90))
                .register(Direction.WEST, BlockStateVariant.create().put(VariantSettings.MODEL, modelId).put(VariantSettings.X, VariantSettings.Rotation.R90).put(VariantSettings.Y, VariantSettings.Rotation.R270))
            )
        );
        
        // Item model
        generator.registerParentedItemModel(verticalSlab, modelId);
    }


    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {
        // Handled in registerVerticalSlab usually or here if needed
    }
}
