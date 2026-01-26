package net.f3rr3.reshaped.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.ai.pathing.NavigationType;
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
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class StepBlock extends Block implements Waterloggable {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final net.minecraft.state.property.IntProperty STEPS = net.minecraft.state.property.IntProperty.of("steps", 1, 4);
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    protected static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 8.0, 8.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 8.0, 16.0);
    protected static final VoxelShape EAST_SHAPE = Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 8.0, 16.0);
    protected static final VoxelShape WEST_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 8.0, 16.0);

    public StepBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(STEPS, 1)
                .with(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        int steps = state.get(STEPS);
        if (steps == 4) {
            return VoxelShapes.fullCube();
        }
        
        VoxelShape baseShape = switch (state.get(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };

        if (steps == 1) return baseShape;
        
        // For 2 or 3 steps, we currently just return a slab-like or larger shape based on facing.
        // If they are layers, they grow in height.
        // If they are additive horizontally, they grow in footprint.
        // Assuming they grow in height for now based on "Step" naming:
        // 1 step: 8px high, 1/2 depth
        // 2 steps: 16px high, 1/2 depth (Vertical Slab)
        // 3 steps: 16px high, 1/2 depth + ... ? 
        // Wait, if it's 4 pieces to make a full block, and 2 pieces make 1/2 block:
        // Maybe:
        // 1: 1/4 (e.g. bottom-north)
        // 2: 2/4 (e.g. bottom-full) -> Slab
        // 3: 3/4 (e.g. bottom-full + top-north)
        // 4: 4/4 (full block)
        
        if (steps == 2) {
            // Becomes a slab in the direction of the facing's axis?
            // Actually, if it's 2 steps, let's just make it a floor slab for simplicity if it was horizontal, 
            // or a vertical slab if it's vertical.
            // But let's look at what the shapes ARE:
            // NORTH_SHAPE is 8 high, 8 deep. Two of them could be 16 high, 8 deep.
            return switch (state.get(FACING)) {
                case SOUTH -> Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
                case WEST -> Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);
                case EAST -> Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);
                default -> Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
            };
        }
        
        if (steps == 3) {
            // Full bottom + half top? Or Full vertical + half other?
            // Let's go with Full Slab (Vertical) + half of the other side.
            return switch (state.get(FACING)) {
                case SOUTH -> VoxelShapes.union(Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 16.0, 16.0), NORTH_SHAPE);
                case WEST -> VoxelShapes.union(Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 16.0, 16.0), EAST_SHAPE);
                case EAST -> VoxelShapes.union(Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 16.0, 16.0), WEST_SHAPE);
                default -> VoxelShapes.union(Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 8.0), SOUTH_SHAPE);
            };
        }

        return VoxelShapes.fullCube();
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos blockPos = ctx.getBlockPos();
        BlockState blockState = ctx.getWorld().getBlockState(blockPos);

        if (blockState.isOf(this)) {
            int steps = blockState.get(STEPS);
            if (steps < 4) {
                return blockState.with(STEPS, steps + 1).with(WATERLOGGED, false);
            }
        }

        FluidState fluidState = ctx.getWorld().getFluidState(blockPos);
        BlockState defaultState = this.getDefaultState().with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);

        return defaultState.with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        int steps = state.get(STEPS);

        if (steps == 4 || !itemStack.isOf(this.asItem())) {
            return false;
        }

        if (context.canReplaceExisting()) {
            Direction direction = context.getSide();
            Direction existingFacing = state.get(FACING);

            // Logic to allow merging when clicking the corresponding face
            // For a NORTH-facing step (Z: 0-8), clicking it from the NORTH face (at Z=0) should merge?
            // Actually, if we want it to be easy to stack, we should allow merging if clicking on the block itself.
            return direction == existingFacing || direction == existingFacing.getOpposite() || direction == Direction.UP || direction == Direction.DOWN;
        } else {
            return true;
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type) {
        return false;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, STEPS, WATERLOGGED);
    }

}
