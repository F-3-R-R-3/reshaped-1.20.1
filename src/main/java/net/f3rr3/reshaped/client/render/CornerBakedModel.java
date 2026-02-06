package net.f3rr3.reshaped.client.render;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.CornerBlock;
import net.f3rr3.reshaped.block.MixedCornerBlock;
import net.f3rr3.reshaped.block.entity.CornerBlockEntity;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Supplier;

public class CornerBakedModel extends ForwardingBakedModel {
    public CornerBakedModel(BakedModel baseModel) {
        this.wrapped = baseModel;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        if (!(state.getBlock() instanceof CornerBlock) && !(state.getBlock() instanceof MixedCornerBlock)) {
            super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
            return;
        }

        CornerBlockEntity cbe = null;
        BlockEntity be = blockView.getBlockEntity(pos);
        if (be instanceof CornerBlockEntity entity) {
            cbe = entity;
        }

        // We render each active corner piece individually
        for (int i = 0; i < 8; i++) {
            if (isBitSet(state, i)) {
                Identifier materialId = null;
                if (cbe != null) {
                    materialId = cbe.getCornerMaterial(i);
                }

                if (materialId == null) {
                    // Fallback to the block's own material if BE data is missing
                    // This handles simple CornerBlocks (without BE data) and uninitialized MixedCornerBlocks (shouldn't happen but safe backup)
                    if (state.getBlock() instanceof CornerBlock) {
                        materialId = Registries.BLOCK.getId(state.getBlock());
                    } else {
                        // MixedCornerBlock without data - skip
                        continue;
                    }
                }

                // Format the bitmask for this single corner piece
                String mask = getSingleBitMask(i);

                // Cleanup ID: reshaped:oak_corner -> oak.  block/oak_corner_mask
                String path = materialId.getPath();
                if (path.endsWith("_corner")) {
                    path = path.substring(0, path.length() - 7); // remove "_corner"
                }

                Identifier segmentModelId = new Identifier(Reshaped.MOD_ID, "block/" + path + "_corner_" + mask);

                BakedModel segmentModel = MinecraftClient.getInstance().getBakedModelManager().getModel(segmentModelId);
                if (segmentModel != null && segmentModel != MinecraftClient.getInstance().getBakedModelManager().getMissingModel()) {
                    segmentModel.emitBlockQuads(blockView, state, pos, randomSupplier, context);
                }
            }
        }
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        // Items are usually just one material (the item itself)
        // We could implement logic here to render item model with corner cut,
        // but typically item model is static JSON.
        super.emitItemQuads(stack, randomSupplier, context);
    }

    private boolean isBitSet(BlockState state, int index) {
        if (index < 0 || index >= net.f3rr3.reshaped.util.BlockSegmentUtils.CORNER_PROPERTIES.length) {
            return false;
        }
        return state.get(net.f3rr3.reshaped.util.BlockSegmentUtils.CORNER_PROPERTIES[index]);
    }

    private String getSingleBitMask(int index) {
        char[] bits = "00000000".toCharArray();
        bits[index] = '1';
        return new String(bits);
    }
}
