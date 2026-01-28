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
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

public class StepBlock extends ReshapedBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    // Segments: Front/Back relative to FACING, Up/Down relative to Y.
    public static final BooleanProperty DOWN_FRONT = BooleanProperty.of("down_front");
    public static final BooleanProperty DOWN_BACK = BooleanProperty.of("down_back");
    public static final BooleanProperty UP_FRONT = BooleanProperty.of("up_front");
    public static final BooleanProperty UP_BACK = BooleanProperty.of("up_back");

    public StepBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(DOWN_FRONT, false).with(DOWN_BACK, false)
                .with(UP_FRONT, false).with(UP_BACK, false)
                .with(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction facing = state.get(FACING);
        VoxelShape shape = VoxelShapes.empty();

        if (state.get(DOWN_FRONT)) shape = VoxelShapes.union(shape, getShape(facing, true, true));
        if (state.get(DOWN_BACK)) shape = VoxelShapes.union(shape, getShape(facing, false, true));
        if (state.get(UP_FRONT)) shape = VoxelShapes.union(shape, getShape(facing, true, false));
        if (state.get(UP_BACK)) shape = VoxelShapes.union(shape, getShape(facing, false, false));

        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    private VoxelShape getShape(Direction facing, boolean isFront, boolean isDown) {
        // "Front" means the side the block is facing.
        // "Back" means the opposite side.
        // We define the partial cubes based entirely on orientation.
        
        // Base coordinate ranges for a generic "Front" segment (assuming North facing logic initially?)
        // Let's define strictly based on rotated boxes.
        
        // Let's calculate the bounding box for each segment.
        // Y is easy: Down=0-8, Up=8-16.
        double yMin = isDown ? 0.0 : 8.0;
        double yMax = isDown ? 8.0 : 16.0;

        // For X/Z, it depends on Facing.
        // North (-Z) -> Front is Z 0-8, Back is Z 8-16.
        // South (+Z) -> Front is Z 8-16, Back is Z 0-8.
        // West (-X) -> Front is X 0-8, Back is X 8-16.
        // East (+X) -> Front is X 8-16, Back is X 0-8.

        double xMin = 0.0, xMax = 16.0;
        double zMin = 0.0, zMax = 16.0;

        switch (facing) {
            case NORTH -> {
                zMin = isFront ? 0.0 : 8.0;
                zMax = isFront ? 8.0 : 16.0;
            }
            case SOUTH -> {
                zMin = isFront ? 8.0 : 0.0;
                zMax = isFront ? 16.0 : 8.0;
            }
            case WEST -> {
                xMin = isFront ? 0.0 : 8.0;
                xMax = isFront ? 8.0 : 16.0;
            }
            case EAST -> {
                xMin = isFront ? 8.0 : 0.0;
                xMax = isFront ? 16.0 : 8.0;
            }
            case UP, DOWN -> {
                // Should never happen for HORIZONTAL_FACING, but required for exhaustive switch
            }
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
            BooleanProperty targetProp = getPropertyFromHit(ctx.getHitPos(), pos, existingState.get(FACING), ctx.getSide(), true);
            if (targetProp != null && !existingState.get(targetProp)) {
                return existingState.with(targetProp, true);
            }
            // If we can't merge property, we shouldn't replace. But canReplace logic handles that. 
            // If we are here, standard placement logic called even if canReplace returned true?
            // Actually getPlacementState is called for the NEW placement.
            // If we are merging, we return the Merged state.
            return existingState.with(targetProp, true);
        }

        // New Placement
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        FluidState fluidState = ctx.getWorld().getFluidState(pos);
        BlockState defaultState = this.getDefaultState()
                .with(FACING, facing)
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);

        BooleanProperty targetProp = getPropertyFromHit(ctx.getHitPos(), pos, facing, ctx.getSide(), true);
        if (targetProp != null) {
            return defaultState.with(targetProp, true);
        }
        
        // Fallback (shouldn't really happen if hit is valid, but default to Bottom Front)
        return defaultState.with(DOWN_FRONT, true);
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        if (!itemStack.isOf(this.asItem())) {
            return false;
        }

        if (context.canReplaceExisting()) {
             BooleanProperty targetProp = getPropertyFromHit(context.getHitPos(), context.getBlockPos(), state.get(FACING), context.getSide(), true);
             return targetProp != null && !state.get(targetProp);
        }
        
        return true;
    }
    
    // Helper to determine which property intersects the hit.
    private BooleanProperty getPropertyFromHit(Vec3d hitPos, BlockPos blockPos, Direction facing, Direction side, boolean isPlacement) {
        double x = hitPos.x - blockPos.getX();
        double y = hitPos.y - blockPos.getY();
        double z = hitPos.z - blockPos.getZ();

        double dX = x + side.getOffsetX() * 0.05;
        double dY = y + side.getOffsetY() * 0.05;
        double dZ = z + side.getOffsetZ() * 0.05;
        
        // Clamp to be inside 0-1 (robustness)
        dX = Math.max(0.01, Math.min(0.99, dX));
        dY = Math.max(0.01, Math.min(0.99, dY));
        dZ = Math.max(0.01, Math.min(0.99, dZ));

        boolean isUp = dY > 0.5;
        boolean isFront = false;
        
        // Determine Front/Back based on facing and hit pos
        switch (facing) {
            case NORTH -> isFront = (dZ < 0.5); // Front is Low Z
            case SOUTH -> isFront = (dZ > 0.5); // Front is High Z
            case WEST -> isFront = (dX < 0.5); // Front is Low X
            case EAST -> isFront = (dX > 0.5); // Front is High X
            default -> {} // Should not happen for Horizontal Facing
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
        builder.add(FACING, DOWN_FRONT, DOWN_BACK, UP_FRONT, UP_BACK);
    }
}
