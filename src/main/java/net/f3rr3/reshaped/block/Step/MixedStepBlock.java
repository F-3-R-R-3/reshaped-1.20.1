package net.f3rr3.reshaped.block.Step;

import net.f3rr3.reshaped.block.Template.ReshapedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
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
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class MixedStepBlock extends ReshapedBlock implements BlockEntityProvider {
    public static final EnumProperty<StepBlock.StepAxis> AXIS = StepBlock.AXIS;
    public static final BooleanProperty DOWN_FRONT = StepBlock.DOWN_FRONT;
    public static final BooleanProperty DOWN_BACK = StepBlock.DOWN_BACK;
    public static final BooleanProperty UP_FRONT = StepBlock.UP_FRONT;
    public static final BooleanProperty UP_BACK = StepBlock.UP_BACK;

    public MixedStepBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(AXIS, StepBlock.StepAxis.NORTH_SOUTH)
                .with(DOWN_FRONT, false).with(DOWN_BACK, false)
                .with(UP_FRONT, false).with(UP_BACK, false)
                .with(WATERLOGGED, false));
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StepBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return net.f3rr3.reshaped.util.BlockSegmentUtils.buildStepShape(state);
    }


    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        if (itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof StepBlock) {
            // Axis check? mixable blocks should probably allow mixing even if axis differs?
            // Ideally we shouldn't allow mixing specific axis blocks into a different axis configuration easily
            // But here we just assume the axis is already locked by the block state.

            Vec3d localHit = getLocalHit(context);
            BooleanProperty property = getPropertyFromHit(localHit.x, localHit.y, localHit.z, context.getSide(), true, state);
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

        Vec3d localHit = getLocalHit(ctx);
        BooleanProperty property = getPropertyFromHit(localHit.x, localHit.y, localHit.z, ctx.getSide(), true, existingState);
        if (property == null) return null;

        if (existingState.isOf(this)) {
            return existingState.with(property, true);
        }

        return null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
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

    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement, BlockState state) {
        var quadrant = net.f3rr3.reshaped.util.BlockSegmentUtils.getQuadrantFromHit(hitX, hitY, hitZ, side, isPlacement);
        StepBlock.StepAxis axis = state.get(AXIS);
        return net.f3rr3.reshaped.util.BlockSegmentUtils.getStepProperty(quadrant, axis);
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.mixed_step";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(AXIS, DOWN_FRONT, DOWN_BACK, UP_FRONT, UP_BACK);
    }
}
