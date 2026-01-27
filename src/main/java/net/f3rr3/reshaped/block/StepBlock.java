package net.f3rr3.reshaped.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

public class StepBlock extends ReshapedBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final net.minecraft.state.property.IntProperty STEPS = net.minecraft.state.property.IntProperty.of("steps", 1, 4);

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
        
        if (steps == 2) {
            return switch (state.get(FACING)) {
                case SOUTH -> Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
                case WEST -> Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);
                case EAST -> Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);
                default -> Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
            };
        }
        
        if (steps == 3) {
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

            return direction == existingFacing || direction == existingFacing.getOpposite() || direction == Direction.UP || direction == Direction.DOWN;
        } else {
            return true;
        }
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(FACING, STEPS);
    }

}
