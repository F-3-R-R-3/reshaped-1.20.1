package net.f3rr3.reshaped.block;

import net.f3rr3.reshaped.block.entity.VerticalStepBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MixedVerticalStepBlock extends ReshapedBlock implements BlockEntityProvider {
    public static final BooleanProperty NORTH_WEST = VerticalStepBlock.NORTH_WEST;
    public static final BooleanProperty NORTH_EAST = VerticalStepBlock.NORTH_EAST;
    public static final BooleanProperty SOUTH_WEST = VerticalStepBlock.SOUTH_WEST;
    public static final BooleanProperty SOUTH_EAST = VerticalStepBlock.SOUTH_EAST;

    public MixedVerticalStepBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(NORTH_WEST, false).with(NORTH_EAST, false)
                .with(SOUTH_WEST, false).with(SOUTH_EAST, false)
                .with(WATERLOGGED, false));
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new VerticalStepBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = VoxelShapes.empty();

        if (state.get(NORTH_WEST)) shape = VoxelShapes.union(shape, net.f3rr3.reshaped.util.BlockSegmentUtils.VSTEP_NW);
        if (state.get(NORTH_EAST)) shape = VoxelShapes.union(shape, net.f3rr3.reshaped.util.BlockSegmentUtils.VSTEP_NE);
        if (state.get(SOUTH_WEST)) shape = VoxelShapes.union(shape, net.f3rr3.reshaped.util.BlockSegmentUtils.VSTEP_SW);
        if (state.get(SOUTH_EAST)) shape = VoxelShapes.union(shape, net.f3rr3.reshaped.util.BlockSegmentUtils.VSTEP_SE);

        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        if (itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof VerticalStepBlock) {
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

        return null; // Should be handled by callback or existing block logic
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof VerticalStepBlockEntity vsbe) {
                if (itemStack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    
                    BooleanProperty[] allProps = {NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST};
                    for (int i = 0; i < 4; i++) {
                        if (state.get(allProps[i]) && vsbe.getMaterial(i) == null) {
                            vsbe.setMaterial(i, Registries.BLOCK.getId(block));
                        }
                    }
                }
            }
        }
    }

    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement) {
        var quadrant = net.f3rr3.reshaped.util.BlockSegmentUtils.getQuadrantFromHit(hitX, hitY, hitZ, side, isPlacement, false);
        return net.f3rr3.reshaped.util.BlockSegmentUtils.getVerticalStepProperty(quadrant);
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.mixed_vertical_step";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST);
    }
}
