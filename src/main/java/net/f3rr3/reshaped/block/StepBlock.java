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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;
import net.f3rr3.reshaped.block.entity.StepBlockEntity;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
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
        return net.f3rr3.reshaped.util.BlockSegmentUtils.buildStepShape(state);
    }



    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        BlockState existingState = ctx.getWorld().getBlockState(pos);
        Vec3d localHit = getLocalHit(ctx);
        
        // If merging into existing StepBlock
        if (existingState.isOf(this)) {
            BooleanProperty targetProp = getPropertyFromHit(localHit.x, localHit.y, localHit.z, ctx.getSide(), true, existingState);
            if (targetProp != null && !existingState.get(targetProp)) {
                return existingState.with(targetProp, true);
            }
            return existingState;
        } else if (existingState.getBlock() instanceof MixedStepBlock mixedStepBlock) {
            // Merging into MixedStepBlock
            // We adopt the existing block's axis.
            BooleanProperty targetProp = mixedStepBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, ctx.getSide(), true, existingState);
            if (targetProp != null && !existingState.get(targetProp)) {
                 return existingState.with(targetProp, true);
            }
            return existingState;
        }

        // New Placement
        Direction playerFacing = ctx.getHorizontalPlayerFacing();
        StepAxis axis = (playerFacing == Direction.NORTH || playerFacing == Direction.SOUTH) ? StepAxis.NORTH_SOUTH : StepAxis.EAST_WEST;

        FluidState fluidState = ctx.getWorld().getFluidState(pos);
        BlockState defaultState = this.getDefaultState()
                .with(AXIS, axis)
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);

        BooleanProperty targetProp = getPropertyFromHit(localHit.x, localHit.y, localHit.z, ctx.getSide(), true, defaultState);
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
             Vec3d localHit = getLocalHit(context);
             BooleanProperty targetProp = getPropertyFromHit(localHit.x, localHit.y, localHit.z, context.getSide(), true, state);
             return targetProp != null && !state.get(targetProp);
        }
        
        return true;
    }
    
    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement, BlockState state) {
        var quadrant = net.f3rr3.reshaped.util.BlockSegmentUtils.getQuadrantFromHit(hitX, hitY, hitZ, side, isPlacement);
        StepAxis axis = state.get(AXIS);
        return net.f3rr3.reshaped.util.BlockSegmentUtils.getStepProperty(quadrant, axis);
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

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            // Check if we merged into a MixedStepBlock
            if (state.getBlock() instanceof MixedStepBlock) {
                 // We need to identify WHICH segment was just added.
                 // Since onPlaced doesn't give us the hit result easily, we iterate.
                 // The segment that is TRUE in state but NULL in BE is the new one.
                 // (Assuming existing segments have materials).
                 net.f3rr3.reshaped.util.BlockSegmentUtils.fillMissingMaterialsFromItem(
                         world,
                         pos,
                         state,
                         itemStack,
                         net.f3rr3.reshaped.util.BlockSegmentUtils.STEP_PROPERTIES,
                         StepBlockEntity.class
                 );
             }
         }
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
