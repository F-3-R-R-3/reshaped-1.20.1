package net.f3rr3.reshaped.client.render;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.*;
import net.f3rr3.reshaped.block.entity.*;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Supplier;

public class CompositeBakedModel extends ForwardingBakedModel {
    public CompositeBakedModel(BakedModel baseModel) {
        this.wrapped = baseModel;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        Block block = state.getBlock();
        BlockEntity be = blockView.getBlockEntity(pos);

        if (block instanceof CornerBlock || block instanceof MixedCornerBlock) {
            renderCornerBlock(blockView, state, pos, randomSupplier, context, be);
        } else if (block instanceof VerticalStepBlock || block instanceof MixedVerticalStepBlock) {
            renderVerticalStepBlock(blockView, state, pos, randomSupplier, context, be);
        } else if (block instanceof StepBlock || block instanceof MixedStepBlock) {
            renderStepBlock(blockView, state, pos, randomSupplier, context, be);
        } else if (block instanceof VerticalSlabBlock || block instanceof MixedVerticalSlabBlock) {
            renderVerticalSlabBlock(blockView, state, pos, randomSupplier, context, be);
        } else if (block instanceof SlabBlock || block instanceof MixedSlabBlock) {
            renderSlabBlock(blockView, state, pos, randomSupplier, context, be);
        } else {
            super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        }
    }

    private void renderCornerBlock(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context, BlockEntity be) {
        CornerBlockEntity cbe = (be instanceof CornerBlockEntity entity) ? entity : null;

        for (int i = 0; i < 8; i++) {
            if (isCornerBitSet(state, i)) {
                Identifier materialId = (cbe != null) ? cbe.getCornerMaterial(i) : null;
                if (materialId == null) {
                    if (state.getBlock() instanceof CornerBlock) materialId = Registries.BLOCK.getId(state.getBlock());
                    else continue;
                }

                String path = cleanPath(materialId.getPath(), "_corner");
                String mask = getCornerBitMask(i);
                Identifier segmentModelId = new Identifier(Reshaped.MOD_ID, "block/" + path + "_corner_" + mask);
                renderModel(segmentModelId, blockView, state, pos, randomSupplier, context);
            }
        }
    }

    private void renderVerticalStepBlock(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context, BlockEntity be) {
        VerticalStepBlockEntity verticalStepBlockEntity = (be instanceof VerticalStepBlockEntity entity) ? entity : null;

        // NW=8, NE=4, SW=2, SE=1 (matching bitmask logic in Plugin)
        // Indices: 0-NW, 1-NE, 2-SW, 3-SE (arbitrary mapping, must match BE)
        // Plugin used: NW=8, NE=4, SW=2, SE=1.
        // Let's use BE index: 0=NW, 1=NE, 2=SW, 3=SE

        for (int i = 0; i < 4; i++) {
            if (isVerticalStepBitSet(state, i)) {
                Identifier materialId = (verticalStepBlockEntity != null) ? verticalStepBlockEntity.getMaterial(i) : null;
                if (materialId == null) {
                    if (state.getBlock() instanceof VerticalStepBlock)
                        materialId = Registries.BLOCK.getId(state.getBlock());
                    else continue;
                }

                String path = cleanPath(materialId.getPath(), "_vertical_step");
                String mask = getVerticalStepBitMask(i); // 1000, 0100, 0010, 0001
                Identifier segmentModelId = new Identifier(Reshaped.MOD_ID, "block/" + path + "_vertical_step_" + mask);
                renderModel(segmentModelId, blockView, state, pos, randomSupplier, context);
            }
        }
    }

    private void renderStepBlock(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context, BlockEntity be) {
        StepBlockEntity stepBlockEntity = (be instanceof StepBlockEntity entity) ? entity : null;
        // DF, DB, UF, UB

        for (int i = 0; i < 4; i++) {
            if (isStepBitSet(state, i)) {
                Identifier materialId = (stepBlockEntity != null) ? stepBlockEntity.getMaterial(i) : null;
                if (materialId == null) {
                    if (state.getBlock() instanceof StepBlock) materialId = Registries.BLOCK.getId(state.getBlock());
                    else continue;
                }

                String path = cleanPath(materialId.getPath(), "_step");
                String mask = getStepBitMask(i);
                Identifier segmentModelId = new Identifier(Reshaped.MOD_ID, "block/" + path + "_step_" + mask);

                net.f3rr3.reshaped.block.StepBlock.StepAxis axis = state.get(StepBlock.AXIS);
                boolean rotate180 = axis == StepBlock.StepAxis.EAST_WEST;
                boolean rotate270 = axis == StepBlock.StepAxis.NORTH_SOUTH;

                context.pushTransform(quad -> {
                    for (int idx = 0; idx < 4; idx++) {
                        float x = quad.x(idx);
                        float z = quad.z(idx);
                        float newX = x;
                        float newZ = z;

                        if (rotate180) {
                            newX = 1.0f - x;
                            newZ = 1.0f - z;
                        } else if (rotate270) {
                            newX = z;
                            newZ = 1.0f - x;
                        }

                        quad.pos(idx, newX, quad.y(idx), newZ);
                    }

                    // Rotate Normal (Light Face)
                    Direction rotatedCull = rotateY(quad.cullFace(), rotate180, rotate270);
                    if (rotatedCull != quad.cullFace()) {
                        quad.cullFace(rotatedCull);
                    }
                    Direction rotatedNominal = rotateY(quad.nominalFace(), rotate180, rotate270);
                    if (rotatedNominal != quad.nominalFace()) {
                        quad.nominalFace(rotatedNominal);
                    }
                    return true;
                });

                renderModel(segmentModelId, blockView, state, pos, randomSupplier, context);
                context.popTransform();
            }
        }
    }

    private void renderVerticalSlabBlock(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context, BlockEntity be) {
        VerticalSlabBlockEntity verticalSlabBlockEntity = (be instanceof VerticalSlabBlockEntity entity) ? entity : null;

        net.minecraft.util.math.Direction.Axis axis = state.get(MixedVerticalSlabBlock.AXIS);

        // 0=Negative (North/West), 1=Positive (South/East)
        for (int i = 0; i < 2; i++) {
            if (isVerticalSlabBitSet(state, i)) {
                Identifier materialId = (verticalSlabBlockEntity != null) ? verticalSlabBlockEntity.getMaterial(i) : null;
                if (materialId == null) {
                    if (state.getBlock() instanceof VerticalSlabBlock)
                        materialId = Registries.BLOCK.getId(state.getBlock());
                    else continue;
                }

                Block materialBlock = Registries.BLOCK.get(materialId);
                if (materialBlock instanceof VerticalSlabBlock) {
                    net.minecraft.util.math.Direction targetFacing;
                    if (axis == net.minecraft.util.math.Direction.Axis.Z) {
                        targetFacing = (i == 0) ? net.minecraft.util.math.Direction.NORTH : net.minecraft.util.math.Direction.SOUTH;
                    } else {
                        targetFacing = (i == 0) ? net.minecraft.util.math.Direction.WEST : net.minecraft.util.math.Direction.EAST;
                    }

                    BlockState targetState = materialBlock.getDefaultState().with(VerticalSlabBlock.FACING, targetFacing);
                    BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(targetState);
                    renderModel(model, blockView, state, pos, randomSupplier, context);
                }
            }
        }
    }

    private void renderSlabBlock(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context, BlockEntity be) {
        SlabBlockEntity slabBlockEntity = (be instanceof SlabBlockEntity entity) ? entity : null;

        // 0=Bottom, 1=Top
        for (int i = 0; i < 2; i++) {
            if (isSlabBitSet(state, i)) {
                Identifier materialId = (slabBlockEntity != null) ? slabBlockEntity.getMaterial(i) : null;
                if (materialId == null) {
                    if (state.getBlock() instanceof SlabBlock) materialId = Registries.BLOCK.getId(state.getBlock());
                    else continue;
                }

                Block materialBlock = Registries.BLOCK.get(materialId);
                if (materialBlock instanceof SlabBlock) {
                    net.minecraft.block.enums.SlabType type = (i == 0) ? net.minecraft.block.enums.SlabType.BOTTOM : net.minecraft.block.enums.SlabType.TOP;
                    BlockState targetState = materialBlock.getDefaultState().with(SlabBlock.TYPE, type);
                    BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(targetState);
                    renderModel(model, blockView, state, pos, randomSupplier, context);
                }
            }
        }
    }

    private void renderModel(Identifier modelId, BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        BakedModel model = MinecraftClient.getInstance().getBakedModelManager().getModel(modelId);
        renderModel(model, blockView, state, pos, randomSupplier, context);
    }

    private void renderModel(BakedModel model, BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        if (model != null && model != MinecraftClient.getInstance().getBakedModelManager().getMissingModel()) {
            model.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        }
    }

    private String cleanPath(String path, String suffixToRemove) {
        if (path.endsWith(suffixToRemove)) {
            return path.substring(0, path.length() - suffixToRemove.length());
        }
        return path;
    }

    // --- Bit Helpers ---

    private boolean isCornerBitSet(BlockState state, int index) {
        if (index < 0 || index >= net.f3rr3.reshaped.util.BlockSegmentUtils.CORNER_PROPERTIES.length) {
            return false;
        }
        return state.get(net.f3rr3.reshaped.util.BlockSegmentUtils.CORNER_PROPERTIES[index]);
    }

    private String getCornerBitMask(int index) {
        char[] bits = "00000000".toCharArray();
        bits[index] = '1';
        return new String(bits);
    }

    private boolean isVerticalStepBitSet(BlockState state, int index) {
        // 0=NW, 1=NE, 2=SW, 3=SE
        return switch (index) {
            case 0 -> state.get(VerticalStepBlock.NORTH_WEST);
            case 1 -> state.get(VerticalStepBlock.NORTH_EAST);
            case 2 -> state.get(VerticalStepBlock.SOUTH_WEST);
            case 3 -> state.get(VerticalStepBlock.SOUTH_EAST);
            default -> false;
        };
    }

    private String getVerticalStepBitMask(int index) {
        // Bitmask logic form Plugin: NW=8 (1000), NE=4 (0100), SW=2 (0010), SE=1 (0001)
        // Index 0 (NW) -> 1000
        return switch (index) {
            case 0 -> "1000";
            case 1 -> "0100";
            case 2 -> "0010";
            case 3 -> "0001";
            default -> "0000";
        };
    }

    private boolean isStepBitSet(BlockState state, int index) {
        // 0=DF, 1=DB, 2=UF, 3=UB
        return switch (index) {
            case 0 -> state.get(StepBlock.DOWN_FRONT);
            case 1 -> state.get(StepBlock.DOWN_BACK);
            case 2 -> state.get(StepBlock.UP_FRONT);
            case 3 -> state.get(StepBlock.UP_BACK);
            default -> false;
        };
    }

    private String getStepBitMask(int index) {
        // Plugin: DF=8, DB=4, UF=2, UB=1
        return switch (index) {
            case 0 -> "1000";
            case 1 -> "0100";
            case 2 -> "0010";
            case 3 -> "0001";
            default -> "0000";
        };
    }

    private boolean isVerticalSlabBitSet(BlockState state, int index) {
        // 0=Negative, 1=Positive
        if (state.getBlock() instanceof MixedVerticalSlabBlock) {
            return index == 0 ? state.get(MixedVerticalSlabBlock.NEGATIVE) : state.get(MixedVerticalSlabBlock.POSITIVE);
        }
        // Vanilla/Base adaptation?
        return false;
    }

    private boolean isSlabBitSet(BlockState state, int index) {
        if (state.getBlock() instanceof MixedSlabBlock) {
            return index == 0 ? state.get(MixedSlabBlock.BOTTOM) : state.get(MixedSlabBlock.TOP);
        }
        return false;
    }

    private Direction rotateY(Direction dir, boolean rotate180, boolean rotate270) {
        if (dir == null) {
            return null;
        }
        if (dir.getAxis().isVertical() || (!rotate180 && !rotate270)) {
            return dir;
        }
        if (rotate180) {
            return dir.getOpposite();
        }
        // rotate270 is always true if it gets here
        return dir.rotateYCounterclockwise();
    }
}
