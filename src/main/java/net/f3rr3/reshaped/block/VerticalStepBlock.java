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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.f3rr3.reshaped.block.entity.VerticalStepBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A block that allows combining up to four 8x16x8 vertical pillars (quadrants).
 * Each pillar is represented by a BooleanProperty.
 */
public class VerticalStepBlock extends ReshapedBlock {
    public static final BooleanProperty NORTH_WEST = BooleanProperty.of("north_west");
    public static final BooleanProperty NORTH_EAST = BooleanProperty.of("north_east");
    public static final BooleanProperty SOUTH_WEST = BooleanProperty.of("south_west");
    public static final BooleanProperty SOUTH_EAST = BooleanProperty.of("south_east");

    public VerticalStepBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(NORTH_WEST, false).with(NORTH_EAST, false)
                .with(SOUTH_WEST, false).with(SOUTH_EAST, false)
                .with(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = VoxelShapes.empty();

        if (state.get(NORTH_WEST)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 0, 0, 8, 16, 8));
        if (state.get(NORTH_EAST)) shape = VoxelShapes.union(shape, Block.createCuboidShape(8, 0, 0, 16, 16, 8));
        if (state.get(SOUTH_WEST)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 0, 8, 8, 16, 16));
        if (state.get(SOUTH_EAST)) shape = VoxelShapes.union(shape, Block.createCuboidShape(8, 0, 8, 16, 16, 16));

        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        BlockState existingState = ctx.getWorld().getBlockState(pos);
        
        // If merging into existing VerticalStepBlock
        if (existingState.isOf(this)) {
            BooleanProperty targetProp = getPropertyFromHit(ctx.getHitPos().x - pos.getX(), ctx.getHitPos().y - pos.getY(), ctx.getHitPos().z - pos.getZ(), ctx.getSide(), true);
            if (targetProp != null && !existingState.get(targetProp)) {
                return existingState.with(targetProp, true);
            }
            return existingState;
        } else if (existingState.getBlock() instanceof MixedVerticalStepBlock mvsb) {
            // Merging into MixedVerticalStepBlock
            BooleanProperty targetProp = mvsb.getPropertyFromHit(ctx.getHitPos().x - pos.getX(), ctx.getHitPos().y - pos.getY(), ctx.getHitPos().z - pos.getZ(), ctx.getSide(), true);
             if (targetProp != null && !existingState.get(targetProp)) {
                 return existingState.with(targetProp, true);
            }
            return existingState;
        }

        // New Placement
        FluidState fluidState = ctx.getWorld().getFluidState(pos);
        BlockState defaultState = this.getDefaultState()
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);

        BooleanProperty targetProp = getPropertyFromHit(ctx.getHitPos().x - pos.getX(), ctx.getHitPos().y - pos.getY(), ctx.getHitPos().z - pos.getZ(), ctx.getSide(), true);
        if (targetProp != null) {
            return defaultState.with(targetProp, true);
        }

        return defaultState.with(NORTH_WEST, true);
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        if (!itemStack.isOf(this.asItem())) {
            return false;
        }

        if (context.canReplaceExisting()) {
             BooleanProperty targetProp = getPropertyFromHit(context.getHitPos().x - context.getBlockPos().getX(), context.getHitPos().y - context.getBlockPos().getY(), context.getHitPos().z - context.getBlockPos().getZ(), context.getSide(), true);
             return targetProp != null && !state.get(targetProp);
        }
        
        return true;
    }
    
    /**
     * Determines which quadrant property corresponds to a hit position on the block.
     * 
     * @param hitX Local X coordinate (0-1)
     * @param hitY Local Y coordinate (0-1)
     * @param hitZ Local Z coordinate (0-1)
     * @param side The side of the block hit
     * @param isPlacement True if calculating for placement/preview, False for mining/interaction
     * @return The BooleanProperty for the hit quadrant
     */
    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement) {
        var quadrant = net.f3rr3.reshaped.util.BlockSegmentUtils.getQuadrantFromHit(hitX, hitY, hitZ, side, isPlacement, false);

        if (quadrant.isNorth()) {
            return quadrant.isWest() ? NORTH_WEST : NORTH_EAST;
        } else {
            return quadrant.isWest() ? SOUTH_WEST : SOUTH_EAST;
        }
    }



    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            // Check if we merged into a MixedVerticalStepBlock
            if (state.getBlock() instanceof MixedVerticalStepBlock) {
                 BlockEntity be = world.getBlockEntity(pos);
                 if (be instanceof VerticalStepBlockEntity vsbe) {
                     Identifier newMaterial = Registries.BLOCK.getId(this);
                     BooleanProperty[] allProps = {NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST};
                     
                     for (int i = 0; i < 4; i++) {
                         if (state.get(allProps[i]) && vsbe.getMaterial(i) == null) {
                             vsbe.setMaterial(i, newMaterial);
                         }
                     }
                 }
            }
        }
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.vertical_step";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST);
    }
}
