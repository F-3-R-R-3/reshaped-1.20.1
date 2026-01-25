package net.f3rr3.reshaped.client.render;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.CornerBlock;
import net.f3rr3.reshaped.block.entity.CornerBlockEntity;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
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
        if (!(state.getBlock() instanceof CornerBlock)) {
            super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
            return;
        }

        CornerBlockEntity cbe = (CornerBlockEntity) blockView.getBlockEntity(pos);
        if (cbe == null) {
            super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
            return;
        }

        // We render each active corner piece individually
        for (int i = 0; i < 8; i++) {
            if (isBitSet(state, i)) {
                Identifier materialId = cbe.getCornerMaterial(i);
                if (materialId == null) {
                    // Fallback to the block's own material if BE data is missing
                    materialId = Registries.BLOCK.getId(state.getBlock());
                }

                // Format the bitmask for this single corner piece
                String mask = getSingleBitMask(i);
                Identifier segmentModelId = new Identifier(Reshaped.MOD_ID, "block/" + materialId.getPath().replace("_corner", "") + "_corner_" + mask);
                
                BakedModel segmentModel = MinecraftClient.getInstance().getBakedModelManager().getModel(segmentModelId);
                if (segmentModel != null && segmentModel != MinecraftClient.getInstance().getBakedModelManager().getMissingModel()) {
                    ((net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel)segmentModel).emitBlockQuads(blockView, state, pos, randomSupplier, context);
                } else {
                    // Fallback to wrapped model if segment fails
                    super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
                }
            }
        }
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        // Items are usually just one material (the item itself)
        super.emitItemQuads(stack, randomSupplier, context);
    }

    private boolean isBitSet(BlockState state, int index) {
        return switch (index) {
            case 0 -> state.get(CornerBlock.DOWN_NW);
            case 1 -> state.get(CornerBlock.DOWN_NE);
            case 2 -> state.get(CornerBlock.DOWN_SW);
            case 3 -> state.get(CornerBlock.DOWN_SE);
            case 4 -> state.get(CornerBlock.UP_NW);
            case 5 -> state.get(CornerBlock.UP_NE);
            case 6 -> state.get(CornerBlock.UP_SW);
            case 7 -> state.get(CornerBlock.UP_SE);
            default -> false;
        };
    }

    private String getSingleBitMask(int index) {
        char[] bits = "00000000".toCharArray();
        bits[index] = '1';
        return new String(bits);
    }
}
