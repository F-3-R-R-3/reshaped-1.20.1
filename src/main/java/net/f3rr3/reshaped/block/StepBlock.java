package net.f3rr3.reshaped.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

public class StepBlock extends ReshapedBlock {
    public static final EnumProperty<StepAxis> AXIS = EnumProperty.of("axis", StepAxis.class);
    // Segments: Front/Back relative to AXIS (North/West are Front), Up/Down relative to Y.
    public static final BooleanProperty DOWN_FRONT = BooleanProperty.of("down_front");
    public static final BooleanProperty DOWN_BACK = BooleanProperty.of("down_back");
    public static final BooleanProperty UP_FRONT = BooleanProperty.of("up_front");
    public static final BooleanProperty UP_BACK = BooleanProperty.of("up_back");

    public StepBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(AXIS, StepAxis.NORTH_SOUTH)
                .with(DOWN_FRONT, false).with(DOWN_BACK, false)
                .with(UP_FRONT, false).with(UP_BACK, false)
                .with(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        StepAxis axis = state.get(AXIS);
        VoxelShape shape = VoxelShapes.empty();

        if (state.get(DOWN_FRONT)) shape = VoxelShapes.union(shape, getShape(axis, true, true));
        if (state.get(DOWN_BACK)) shape = VoxelShapes.union(shape, getShape(axis, false, true));
        if (state.get(UP_FRONT)) shape = VoxelShapes.union(shape, getShape(axis, true, false));
        if (state.get(UP_BACK)) shape = VoxelShapes.union(shape, getShape(axis, false, false));

        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    private VoxelShape getShape(StepAxis axis, boolean isFront, boolean isDown) {
        double yMin = isDown ? 0.0 : 8.0;
        double yMax = isDown ? 8.0 : 16.0;

        double xMin = 0.0, xMax = 16.0;
        double zMin = 0.0, zMax = 16.0;

        if (axis == StepAxis.NORTH_SOUTH) {
            // Front is North (Low Z), Back is South (High Z)
            zMin = isFront ? 0.0 : 8.0;
            zMax = isFront ? 8.0 : 16.0;
        } else {
            // Front is West (Low X), Back is East (High X)
            xMin = isFront ? 0.0 : 8.0;
            xMax = isFront ? 8.0 : 16.0;
        }

        return Block.createCuboidShape(xMin, yMin, zMin, xMax, yMax, zMax);
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        BlockState existingState = ctx.getWorld().getBlockState(pos);
        
        // If merging into existing StepBlock
        if (existingState.isOf(this)) {
            BooleanProperty targetProp = getPropertyFromHit(ctx.getHitPos().x - pos.getX(), ctx.getHitPos().y - pos.getY(), ctx.getHitPos().z - pos.getZ(), ctx.getSide(), true, existingState);
            if (targetProp != null && !existingState.get(targetProp)) {
                return existingState.with(targetProp, true);
            }
            return existingState.with(targetProp, true);
        }

        // New Placement
        Direction playerFacing = ctx.getHorizontalPlayerFacing();
        StepAxis axis = (playerFacing == Direction.NORTH || playerFacing == Direction.SOUTH) ? StepAxis.NORTH_SOUTH : StepAxis.EAST_WEST;

        FluidState fluidState = ctx.getWorld().getFluidState(pos);
        BlockState defaultState = this.getDefaultState()
                .with(AXIS, axis)
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);

        BooleanProperty targetProp = getPropertyFromHit(ctx.getHitPos().x - pos.getX(), ctx.getHitPos().y - pos.getY(), ctx.getHitPos().z - pos.getZ(), ctx.getSide(), true, defaultState);
        if (targetProp != null) {
            return defaultState.with(targetProp, true);
        }

        return defaultState.with(DOWN_FRONT, true);
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        if (!itemStack.isOf(this.asItem())) {
            return false;
        }

        if (context.canReplaceExisting()) {
             BooleanProperty targetProp = getPropertyFromHit(context.getHitPos().x - context.getBlockPos().getX(), context.getHitPos().y - context.getBlockPos().getY(), context.getHitPos().z - context.getBlockPos().getZ(), context.getSide(), true, state);
             return targetProp != null && !state.get(targetProp);
        }
        
        return true;
    }
    
    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement, BlockState state) {
        StepAxis axis = state.get(AXIS);

        double testX = hitX + side.getOffsetX() * (isPlacement ? 0.05 : -0.05);
        double testY = hitY + side.getOffsetY() * (isPlacement ? 0.05 : -0.05);
        double testZ = hitZ + side.getOffsetZ() * (isPlacement ? 0.05 : -0.05);

        testX = Math.max(0.01, Math.min(0.99, testX));
        testY = Math.max(0.01, Math.min(0.99, testY));
        testZ = Math.max(0.01, Math.min(0.99, testZ));

        boolean isUp = testY > 0.5;
        boolean isFront;

        if (axis == StepAxis.NORTH_SOUTH) {
            isFront = (testZ < 0.5); // North is Front
        } else {
            isFront = (testX < 0.5); // West is Front
        }

        if (isUp) {
            return isFront ? UP_FRONT : UP_BACK;
        } else {
            return isFront ? DOWN_FRONT : DOWN_BACK;
        }
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.step";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(AXIS, DOWN_FRONT, DOWN_BACK, UP_FRONT, UP_BACK);
    }

    public enum StepAxis implements net.minecraft.util.StringIdentifiable {
        NORTH_SOUTH("nortsouth"),
        EAST_WEST("eastwest");

        private final String name;

        StepAxis(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return this.name;
        }
    }
}
