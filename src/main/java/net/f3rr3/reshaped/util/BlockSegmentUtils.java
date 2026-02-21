package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.block.Corner.CornerBlock;
import net.f3rr3.reshaped.block.Step.StepBlock;
import net.f3rr3.reshaped.block.VerticalStep.VerticalStepBlock;
import net.f3rr3.reshaped.block.Template.MixedBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/**
 * Shared utility methods for segment-based blocks (Steps, Vertical Steps, Corners).
 */
public class BlockSegmentUtils {

    // ==================== Quadrant Detection ====================

    public static final BooleanProperty[] CORNER_PROPERTIES = {
            CornerBlock.DOWN_NW, CornerBlock.DOWN_NE, CornerBlock.DOWN_SW, CornerBlock.DOWN_SE,
            CornerBlock.UP_NW, CornerBlock.UP_NE, CornerBlock.UP_SW, CornerBlock.UP_SE
    };
    public static final BooleanProperty[] VERTICAL_STEP_PROPERTIES = {
            VerticalStepBlock.NORTH_WEST, VerticalStepBlock.NORTH_EAST, VerticalStepBlock.SOUTH_WEST, VerticalStepBlock.SOUTH_EAST
    };

    // ==================== Property Mapping ====================
    public static final BooleanProperty[] STEP_PROPERTIES = {
            StepBlock.DOWN_FRONT, StepBlock.DOWN_BACK, StepBlock.UP_FRONT, StepBlock.UP_BACK
    };
    // Corner shape constants
    public static final VoxelShape CORNER_DOWN_NW = Block.createCuboidShape(0, 0, 0, 8, 8, 8);
    public static final VoxelShape CORNER_DOWN_NE = Block.createCuboidShape(8, 0, 0, 16, 8, 8);

    // ==================== Shape Building ====================
    public static final VoxelShape CORNER_DOWN_SW = Block.createCuboidShape(0, 0, 8, 8, 8, 16);
    public static final VoxelShape CORNER_DOWN_SE = Block.createCuboidShape(8, 0, 8, 16, 8, 16);
    public static final VoxelShape CORNER_UP_NW = Block.createCuboidShape(0, 8, 0, 8, 16, 8);
    public static final VoxelShape CORNER_UP_NE = Block.createCuboidShape(8, 8, 0, 16, 16, 8);
    public static final VoxelShape CORNER_UP_SW = Block.createCuboidShape(0, 8, 8, 8, 16, 16);
    public static final VoxelShape CORNER_UP_SE = Block.createCuboidShape(8, 8, 8, 16, 16, 16);
    // Vertical step shape constants
    public static final VoxelShape VSTEP_NW = Block.createCuboidShape(0, 0, 0, 8, 16, 8);
    public static final VoxelShape VSTEP_NE = Block.createCuboidShape(8, 0, 0, 16, 16, 8);
    public static final VoxelShape VSTEP_SW = Block.createCuboidShape(0, 0, 8, 8, 16, 16);
    public static final VoxelShape VSTEP_SE = Block.createCuboidShape(8, 0, 8, 16, 16, 16);

    /**
     * Common hit detection logic for quadrant-based blocks.
     * Extracts logic used in VerticalStepBlock and CornerBlock.
     */
    public static Quadrant getQuadrantFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement) {
        double offset = isPlacement ? 0.05 : -0.05;
        double testX = hitX + side.getOffsetX() * offset;
        double testY = hitY + side.getOffsetY() * offset;
        double testZ = hitZ + side.getOffsetZ() * offset;

        testX = Math.max(0.001, Math.min(0.999, testX));
        testY = Math.max(0.001, Math.min(0.999, testY));
        testZ = Math.max(0.001, Math.min(0.999, testZ));

        boolean isUp = testY > 0.5;
        boolean isNorth = testZ < 0.5;
        boolean isWest = testX < 0.5;

        return new Quadrant(isUp, isNorth, isWest);
    }

    /**
     * Get the CornerBlock property for a given quadrant.
     */
    public static BooleanProperty getCornerProperty(Quadrant quadrant) {
        if (quadrant.isUp()) {
            if (!quadrant.isWest()) return quadrant.isPlusZ() ? CornerBlock.UP_SE : CornerBlock.UP_NE;
            else return quadrant.isPlusZ() ? CornerBlock.UP_SW : CornerBlock.UP_NW;
        } else {
            if (!quadrant.isWest()) return quadrant.isPlusZ() ? CornerBlock.DOWN_SE : CornerBlock.DOWN_NE;
            else return quadrant.isPlusZ() ? CornerBlock.DOWN_SW : CornerBlock.DOWN_NW;
        }
    }

    /**
     * Get the VerticalStepBlock property for a given quadrant.
     */
    public static BooleanProperty getVerticalStepProperty(Quadrant quadrant) {
        if (quadrant.isNorth()) {
            return quadrant.isWest() ? VerticalStepBlock.NORTH_WEST : VerticalStepBlock.NORTH_EAST;
        } else {
            return quadrant.isWest() ? VerticalStepBlock.SOUTH_WEST : VerticalStepBlock.SOUTH_EAST;
        }
    }

    /**
     * Get the StepBlock property for a given quadrant and axis.
     */
    public static BooleanProperty getStepProperty(Quadrant quadrant, StepBlock.StepAxis axis) {
        boolean isFront = (axis == StepBlock.StepAxis.NORTH_SOUTH) ? quadrant.isNorth() : quadrant.isWest();

        if (quadrant.isUp()) {
            return isFront ? StepBlock.UP_FRONT : StepBlock.UP_BACK;
        } else {
            return isFront ? StepBlock.DOWN_FRONT : StepBlock.DOWN_BACK;
        }
    }

    /**
     * Build the shape for a StepBlock or MixedStepBlock segment.
     */
    public static VoxelShape getStepShape(StepBlock.StepAxis axis, boolean isFront, boolean isDown) {
        double yMin = isDown ? 0.0 : 8.0;
        double yMax = isDown ? 8.0 : 16.0;

        double xMin = 0.0, xMax = 16.0;
        double zMin = 0.0, zMax = 16.0;

        if (axis == StepBlock.StepAxis.NORTH_SOUTH) {
            zMin = isFront ? 0.0 : 8.0;
            zMax = isFront ? 8.0 : 16.0;
        } else {
            xMin = isFront ? 0.0 : 8.0;
            xMax = isFront ? 8.0 : 16.0;
        }

        return Block.createCuboidShape(xMin, yMin, zMin, xMax, yMax, zMax);
    }

    public static VoxelShape buildStepShape(BlockState state) {
        StepBlock.StepAxis axis = state.get(StepBlock.AXIS);
        VoxelShape shape = VoxelShapes.empty();

        if (state.get(StepBlock.DOWN_FRONT)) shape = VoxelShapes.union(shape, getStepShape(axis, true, true));
        if (state.get(StepBlock.DOWN_BACK)) shape = VoxelShapes.union(shape, getStepShape(axis, false, true));
        if (state.get(StepBlock.UP_FRONT)) shape = VoxelShapes.union(shape, getStepShape(axis, true, false));
        if (state.get(StepBlock.UP_BACK)) shape = VoxelShapes.union(shape, getStepShape(axis, false, false));

        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    public static VoxelShape buildVerticalStepShape(BlockState state) {
        VoxelShape shape = VoxelShapes.empty();
        if (state.get(VerticalStepBlock.NORTH_WEST)) shape = VoxelShapes.union(shape, VSTEP_NW);
        if (state.get(VerticalStepBlock.NORTH_EAST)) shape = VoxelShapes.union(shape, VSTEP_NE);
        if (state.get(VerticalStepBlock.SOUTH_WEST)) shape = VoxelShapes.union(shape, VSTEP_SW);
        if (state.get(VerticalStepBlock.SOUTH_EAST)) shape = VoxelShapes.union(shape, VSTEP_SE);
        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    public static VoxelShape buildCornerShape(BlockState state) {
        VoxelShape shape = VoxelShapes.empty();
        if (state.get(CornerBlock.DOWN_NW)) shape = VoxelShapes.union(shape, CORNER_DOWN_NW);
        if (state.get(CornerBlock.DOWN_NE)) shape = VoxelShapes.union(shape, CORNER_DOWN_NE);
        if (state.get(CornerBlock.DOWN_SW)) shape = VoxelShapes.union(shape, CORNER_DOWN_SW);
        if (state.get(CornerBlock.DOWN_SE)) shape = VoxelShapes.union(shape, CORNER_DOWN_SE);
        if (state.get(CornerBlock.UP_NW)) shape = VoxelShapes.union(shape, CORNER_UP_NW);
        if (state.get(CornerBlock.UP_NE)) shape = VoxelShapes.union(shape, CORNER_UP_NE);
        if (state.get(CornerBlock.UP_SW)) shape = VoxelShapes.union(shape, CORNER_UP_SW);
        if (state.get(CornerBlock.UP_SE)) shape = VoxelShapes.union(shape, CORNER_UP_SE);
        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    public static void fillMissingMaterials(MixedBlockEntity blockEntity, BlockState state, BooleanProperty[] properties, Identifier materialId) {
        for (int i = 0; i < properties.length; i++) {
            if (state.get(properties[i]) && blockEntity.getMaterial(i) == null) {
                blockEntity.setMaterial(i, materialId);
            }
        }
    }

    public static void fillMissingMaterialsFromItem(ItemStack itemStack, MixedBlockEntity blockEntity, BlockState state, BooleanProperty[] properties) {
        if (itemStack.getItem() instanceof BlockItem blockItem) {
            fillMissingMaterials(blockEntity, state, properties, Registries.BLOCK.getId(blockItem.getBlock()));
        }
    }

    public static void fillMissingMaterialsFromItem(World world, BlockPos pos, BlockState state, ItemStack itemStack,
                                                    BooleanProperty[] properties, Class<? extends MixedBlockEntity> entityClass) {
        BlockEntity be = world.getBlockEntity(pos);
        if (entityClass.isInstance(be)) {
            fillMissingMaterialsFromItem(itemStack, entityClass.cast(be), state, properties);
        }
    }

    public record Quadrant(boolean isUp, boolean isNorth, boolean isWest) {
        public boolean isPlusX() {
            return !isWest;
        }

        public boolean isPlusZ() {
            return !isNorth;
        }
    }
}
