package net.f3rr3.reshaped.block;

import net.f3rr3.reshaped.block.entity.CornerBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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
import org.jetbrains.annotations.Nullable;

public class CornerBlock extends Block implements Waterloggable, BlockEntityProvider {
    public static final BooleanProperty DOWN_NW = BooleanProperty.of("down_nw");
    public static final BooleanProperty DOWN_NE = BooleanProperty.of("down_ne");
    public static final BooleanProperty DOWN_SW = BooleanProperty.of("down_sw");
    public static final BooleanProperty DOWN_SE = BooleanProperty.of("down_se");
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
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CornerBlockEntity(pos, state);
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
        // Allow replacement if the item is ANY corner block and the target quadrant is empty
        if (itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CornerBlock) {
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

        if (existingState.getBlock() instanceof CornerBlock) {
            return existingState.with(property, true);
        }

        FluidState fluidState = world.getFluidState(pos);
        return this.getDefaultState()
                .with(property, true)
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof CornerBlockEntity cbe) {
                // Find which corner was just added and set its material
                // We use the itemStack's block as the material
                if (itemStack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    
                    // We need to know which quadrant was just filled.
                    // This is slightly tricky since we don't have the hit pos here.
                    // However, we just added a property that was false and is now true.
                    
                    // Actually, let's pass the property through NBT or just check all 8.
                    // If a slot is empty in BE but true in state, fill it.
                    BooleanProperty[] allProps = {DOWN_NW, DOWN_NE, DOWN_SW, DOWN_SE, UP_NW, UP_NE, UP_SW, UP_SE};
                    for (int i = 0; i < 8; i++) {
                        if (state.get(allProps[i]) && cbe.getCornerMaterial(i) == null) {
                            cbe.setCornerMaterial(i, Registries.BLOCK.getId(block));
                        }
                    }
                }
            }
        }
    }

    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement) {
        double offset = isPlacement ? 0.1 : -0.1; 
        double testX = hitX + side.getOffsetX() * offset;
        double testY = hitY + side.getOffsetY() * offset;
        double testZ = hitZ + side.getOffsetZ() * offset;
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

    public int getIndexFromProperty(BooleanProperty property) {
        if (property == DOWN_NW) return 0;
        if (property == DOWN_NE) return 1;
        if (property == DOWN_SW) return 2;
        if (property == DOWN_SE) return 3;
        if (property == UP_NW) return 4;
        if (property == UP_NE) return 5;
        if (property == UP_SW) return 6;
        if (property == UP_SE) return 7;
        return -1;
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
        if (state.get(WATERLOGGED)) world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
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
