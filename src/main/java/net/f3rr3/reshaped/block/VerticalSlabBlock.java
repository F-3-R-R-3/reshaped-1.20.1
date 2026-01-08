package net.f3rr3.reshaped.block;

import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class VerticalSlabBlock extends Block implements Waterloggable {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<SlabType> TYPE = Properties.SLAB_TYPE;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    protected static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
    protected static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);
    protected static final VoxelShape WEST_SHAPE = Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public VerticalSlabBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(TYPE, SlabType.BOTTOM) // We reuse SlabType: BOTTOM=Single, DOUBLE=Double. TOP is unused but required by enum.
                .with(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (state.get(TYPE) == SlabType.DOUBLE) {
            return VoxelShapes.fullCube();
        }
        return switch (state.get(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos blockPos = ctx.getBlockPos();
        BlockState blockState = ctx.getWorld().getBlockState(blockPos);
        
        if (blockState.isOf(this)) {
            return blockState.with(TYPE, SlabType.DOUBLE).with(WATERLOGGED, false);
        }
        
        FluidState fluidState = ctx.getWorld().getFluidState(blockPos);
        BlockState defaultState = this.getDefaultState().with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
        
        Direction direction = ctx.getSide();
        // If placing on the side logic could be improved here to auto-orient based on hit-pos
        // Simple logic:
        return defaultState.with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        SlabType slabType = state.get(TYPE);
        
        if (slabType == SlabType.DOUBLE || !itemStack.isOf(this.asItem())) {
            return false;
        }

        if (context.canReplaceExisting()) {
            boolean isForward = context.getHitPos().y - (double)context.getBlockPos().getY() > 0.5; // Irrelevant for vertical?
            Direction direction = context.getSide();
            
            // Allow merging if we click on the internal (exposed) face of the existing vertical slab
            // A NORTH-facing slab has its exposed face at z=8, pointing NORTH
            // So clicking on the NORTH face of a NORTH-facing slab should merge
            Direction existingFacing = state.get(FACING);
            
            if (existingFacing == Direction.NORTH && direction == Direction.NORTH) return true;
            if (existingFacing == Direction.SOUTH && direction == Direction.SOUTH) return true;
            if (existingFacing == Direction.EAST && direction == Direction.EAST) return true;
            if (existingFacing == Direction.WEST && direction == Direction.WEST) return true;
            
            return false;
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
        builder.add(FACING, TYPE, WATERLOGGED);
    }
}
