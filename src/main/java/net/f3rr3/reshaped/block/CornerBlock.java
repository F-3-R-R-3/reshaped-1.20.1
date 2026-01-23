package net.f3rr3.reshaped.block;

import net.minecraft.block.*;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

public class CornerBlock extends Block implements Waterloggable {
    // 8 Corners: Up/Down, North/South, East/West
    // Using mapping: North=-Z, South=+Z, East=+X, West=-X
    public static final BooleanProperty DOWN_NW = BooleanProperty.of("down_nw"); // -X, -Z
    public static final BooleanProperty DOWN_NE = BooleanProperty.of("down_ne"); // +X, -Z
    public static final BooleanProperty DOWN_SW = BooleanProperty.of("down_sw"); // -X, +Z
    public static final BooleanProperty DOWN_SE = BooleanProperty.of("down_se"); // +X, +Z
    public static final BooleanProperty UP_NW = BooleanProperty.of("up_nw");
    public static final BooleanProperty UP_NE = BooleanProperty.of("up_ne");
    public static final BooleanProperty UP_SW = BooleanProperty.of("up_sw");
    public static final BooleanProperty UP_SE = BooleanProperty.of("up_se");
    
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    public CornerBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(DOWN_NW, false).with(DOWN_NE, false).with(DOWN_SW, false).with(DOWN_SE, false)
                .with(UP_NW, false).with(UP_NE, false).with(UP_SW, false).with(UP_SE, false)
                .with(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = VoxelShapes.empty();
        
        if (state.get(DOWN_NW)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 0, 0, 8, 8, 8));
        if (state.get(DOWN_NE)) shape = VoxelShapes.union(shape, Block.createCuboidShape(8, 0, 0, 16, 8, 8));
        if (state.get(DOWN_SW)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 0, 8, 8, 8, 16));
        if (state.get(DOWN_SE)) shape = VoxelShapes.union(shape, Block.createCuboidShape(8, 0, 8, 16, 8, 16));
        
        if (state.get(UP_NW)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 8, 0, 8, 16, 8));
        if (state.get(UP_NE)) shape = VoxelShapes.union(shape, Block.createCuboidShape(8, 8, 0, 16, 16, 8));
        if (state.get(UP_SW)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 8, 8, 8, 16, 16));
        if (state.get(UP_SE)) shape = VoxelShapes.union(shape, Block.createCuboidShape(8, 8, 8, 16, 16, 16));

        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        if (itemStack.getItem() == this.asItem()) {
            double hitX = context.getHitPos().x - (double)context.getBlockPos().getX();
            double hitY = context.getHitPos().y - (double)context.getBlockPos().getY();
            double hitZ = context.getHitPos().z - (double)context.getBlockPos().getZ();
            
            BooleanProperty property = getPropertyFromHit(hitX, hitY, hitZ, context.getSide(), true);
            if (property != null && !state.get(property)) {
                return true;
            }
        }
        return super.canReplace(state, context);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        World world = ctx.getWorld();
        BlockState existingState = world.getBlockState(pos);
        
        double hitX = ctx.getHitPos().x - (double)pos.getX();
        double hitY = ctx.getHitPos().y - (double)pos.getY();
        double hitZ = ctx.getHitPos().z - (double)pos.getZ();
        
        BooleanProperty property = getPropertyFromHit(hitX, hitY, hitZ, ctx.getSide(), true);
        if (property == null) return null;

        if (existingState.isOf(this)) {
            return existingState.with(property, true);
        }

        FluidState fluidState = world.getFluidState(pos);
        return this.getDefaultState()
                .with(property, true)
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement) {
        // When placing, we look 'outside' the hit box if it's already full, but when mining we want to look 'inside' the face
        double offset = isPlacement ? 0.1 : -0.1; 
        double testX = hitX + side.getOffsetX() * offset;
        double testY = hitY + side.getOffsetY() * offset;
        double testZ = hitZ + side.getOffsetZ() * offset;

        // Clamp to stay within block space
        testX = Math.max(0.001, Math.min(0.999, testX));
        testY = Math.max(0.001, Math.min(0.999, testY));
        testZ = Math.max(0.001, Math.min(0.999, testZ));

        boolean isUp = testY > 0.5;
        boolean isPlusX = testX > 0.5;
        boolean isPlusZ = testZ > 0.5;

        if (isUp) {
            if (isPlusX) return isPlusZ ? UP_SE : UP_NE;
            else return isPlusZ ? UP_SW : UP_NW;
        } else {
            if (isPlusX) return isPlusZ ? DOWN_SE : DOWN_NE;
            else return isPlusZ ? DOWN_SW : DOWN_NW;
        }
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.corner";
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
        builder.add(DOWN_NW, DOWN_NE, DOWN_SW, DOWN_SE, UP_NW, UP_NE, UP_SW, UP_SE, WATERLOGGED);
    }
}
