package net.f3rr3.reshaped.block.Corner;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class MixedCornerBlock extends ReshapedBlock implements BlockEntityProvider {
    public static final BooleanProperty DOWN_NW = CornerBlock.DOWN_NW;
    public static final BooleanProperty DOWN_NE = CornerBlock.DOWN_NE;
    public static final BooleanProperty DOWN_SW = CornerBlock.DOWN_SW;
    public static final BooleanProperty DOWN_SE = CornerBlock.DOWN_SE;
    public static final BooleanProperty UP_NW = CornerBlock.UP_NW;
    public static final BooleanProperty UP_NE = CornerBlock.UP_NE;
    public static final BooleanProperty UP_SW = CornerBlock.UP_SW;
    public static final BooleanProperty UP_SE = CornerBlock.UP_SE;

    public MixedCornerBlock(Settings settings) {
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
        return net.f3rr3.reshaped.util.BlockSegmentUtils.buildCornerShape(state);
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        if (itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CornerBlock) {
            Vec3d localHit = getLocalHit(context);
            BooleanProperty property = getPropertyFromHit(localHit.x, localHit.y, localHit.z, context.getSide(), true);
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
        BooleanProperty property = getPropertyFromHit(localHit.x, localHit.y, localHit.z, ctx.getSide(), true);
        if (property == null) return null;

        if (existingState.isOf(this)) {
            return existingState.with(property, true);
        }

        return null; // Should not happen given intended usage
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            // When we add a piece to a mixed block, we update the BE
            net.f3rr3.reshaped.util.BlockSegmentUtils.fillMissingMaterialsFromItem(
                    world,
                    pos,
                    state,
                    itemStack,
                    net.f3rr3.reshaped.util.BlockSegmentUtils.CORNER_PROPERTIES,
                    CornerBlockEntity.class
            );
        }
    }


    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement) {
        var quadrant = net.f3rr3.reshaped.util.BlockSegmentUtils.getQuadrantFromHit(hitX, hitY, hitZ, side, isPlacement);
        return net.f3rr3.reshaped.util.BlockSegmentUtils.getCornerProperty(quadrant);
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.mixed_corner";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(DOWN_NW, DOWN_NE, DOWN_SW, DOWN_SE, UP_NW, UP_NE, UP_SW, UP_SE);
    }
}
