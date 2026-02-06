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
                    if (state.getBlock() instanceof VerticalStepBlock) materialId = Registries.BLOCK.getId(state.getBlock());
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
                
                // Determine rotation:
                // If AXIS is NORTH_SOUTH, rotation is 270 (North).
                // If AXIS is EAST_WEST, rotation is 180 (West).
                // Wait. Default model for Steps is defined how?
                // Usually north-facing.
                // Standard block rotation: 0=South, 90=West, 180=North, 270=East ??? 
                // Minecraft rotations are CCW from South?
                // Let's copy from Plugin:
                // NORTH_SOUTH -> 270.
                // EAST_WEST -> 180.
                
                net.f3rr3.reshaped.block.StepBlock.StepAxis axis = state.get(StepBlock.AXIS);
                boolean rotate180 = axis == StepBlock.StepAxis.EAST_WEST;
                boolean rotate270 = axis == StepBlock.StepAxis.NORTH_SOUTH;
                
                // Apply rotation
                // Note: We need a MatrixStack or create a wrapped model that applies rotation.
                // Or use context.pushTransform().
                
                context.pushTransform(quad -> {
                     // Simplified rotation for Y-axis 90 degree increments
                     // We need to rotate the quad geometry and the lightFace.
                     
                     // BUT Fabric RenderContext transforms are powerful but low level.
                     // A safer bet is to rely on ModelVariant rotation if we could?
                     // We can't easily.
                     
                     // Let's use a QuadTransform that applies the rotation using Vector rotation.
                     // Just use a utility if available? No.
                     
                     // Rotate Position
                     // Center is 0.5, 0.5, 0.5
                     // x' = cos(theta) * (x-0.5) - sin(theta) * (z-0.5) + 0.5
                     // z' = sin(theta) * (x-0.5) + cos(theta) * (z-0.5) + 0.5
                     
                     // For 180: x' = -(x-0.5) + 0.5 = 1-x.  z' = -(z-0.5) + 0.5 = 1-z.
                     // For 270: x' = -(-) * (z-0.5) + 0.5 = z. z' = -1 * (x-0.5) + 0.5 = 1-x.
                     
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
                     // If we rotate geometry, we should probably update nominalFace?
                     // But strictly speaking, lightFace is important for lighting.
                     // rotate(Direction)
                     // 180: North->South, East->West
                     // 270: North->West, East->North
                     // Wait, 270 degrees CCW?
                     // Minecraft ModelRotation 270:
                     // 0: South
                     // 90: West
                     // 180: North
                     // 270: East?
                     // Let's verify standard ModelRotation.
                     // X0_Y0 = no rotation.
                     // X0_Y90 = 90 deg.
                     // If I have a model facing NORTH. (Z negative).
                     // To make it face EAST (X positive).
                     // Rotate 270? Or 90?
                     
                     // Let's stick to the geometry rotation logic.
                     // 180 flips X and Z.
                     // 270 swaps X and Z and flips X.
                     
                     // Re-eval 270 (x becomes z, z becomes 1-x).
                     // old(0.5, 0) -> new(0, 0.5) => North point becomes West point.
                     // So 270 rotates North to West.
                     
                     // If StepBlock Plugin said: "NORTH_SOUTH -> 270".
                     // And comment said: "North is Front".
                     // Default model is likely SOUTH facing? 
                     // If default is built as South...
                     // 270 makes it East?
                     // This is confusing. 
                     // Let's trust the geometry math:
                     // If I want North Face (z=0) to be at z=0.
                     // If model is defined at z=0 (North).
                     // And 270 rotates North to West.
                     // Then 270 is wrong for North?
                     
                     // Plugin code:
                     // case NORTH_SOUTH -> 270; // North is Front
                     // case EAST_WEST -> 180;   // West is Front
                     
                     // If I assume Plugin code was CORRECT visually, I should replicate it.
                     // So I apply 270 or 180.
                     
                     // Face rotation: 
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
                     if (state.getBlock() instanceof VerticalSlabBlock) materialId = Registries.BLOCK.getId(state.getBlock());
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
         return switch(index) {
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
        return switch(index) {
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
    
    private String getVerticalSlabSuffix(BlockState state, int index) {
         // Axis Z: Neg=North, Pos=South
         // Axis X: Neg=West, Pos=East
         net.minecraft.util.math.Direction.Axis axis = state.get(MixedVerticalSlabBlock.AXIS);
         if (axis == net.minecraft.util.math.Direction.Axis.Z) {
             return (index == 0) ? "north" : "south"; // Assuming models are named "block/name_north"
         } else {
             return (index == 0) ? "west" : "east";
         }
    }
    
    private boolean isSlabBitSet(BlockState state, int index) {
        if (state.getBlock() instanceof MixedSlabBlock) {
            return index == 0 ? state.get(MixedSlabBlock.BOTTOM) : state.get(MixedSlabBlock.TOP);
        }
        return false;
    }
}
