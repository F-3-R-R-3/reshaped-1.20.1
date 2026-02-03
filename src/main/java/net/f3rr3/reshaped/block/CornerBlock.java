package net.f3rr3.reshaped.block;

import net.f3rr3.reshaped.block.entity.CornerBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
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

public class CornerBlock extends ReshapedBlock {
    public static final BooleanProperty DOWN_NW = BooleanProperty.of("down_nw");
    public static final BooleanProperty DOWN_NE = BooleanProperty.of("down_ne");
    public static final BooleanProperty DOWN_SW = BooleanProperty.of("down_sw");
    public static final BooleanProperty DOWN_SE = BooleanProperty.of("down_se");
    public static final BooleanProperty UP_NW = BooleanProperty.of("up_nw");
    public static final BooleanProperty UP_NE = BooleanProperty.of("up_ne");
    public static final BooleanProperty UP_SW = BooleanProperty.of("up_sw");
    public static final BooleanProperty UP_SE = BooleanProperty.of("up_se");

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
        // Allow replacement if the item is ANY corner block and the target quadrant is empty
        if (itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CornerBlock) {
            double hitX = context.getHitPos().x - (double) context.getBlockPos().getX();
            double hitY = context.getHitPos().y - (double) context.getBlockPos().getY();
            double hitZ = context.getHitPos().z - (double) context.getBlockPos().getZ();

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

        double hitX = ctx.getHitPos().x - (double) pos.getX();
        double hitY = ctx.getHitPos().y - (double) pos.getY();
        double hitZ = ctx.getHitPos().z - (double) pos.getZ();

        BooleanProperty property = getPropertyFromHit(hitX, hitY, hitZ, ctx.getSide(), true);
        if (property == null) return null;

        if (existingState.getBlock() == this) {
            // Merging into self (same material)
            return existingState.with(property, true);
        } else if (existingState.getBlock() instanceof MixedCornerBlock) {
            // Merging into MixedCornerBlock
            // We return the Mixed state with the new property set.
            // This tells the game to "set" the block to what it already is (Mixed) but with updated bits.
            // This preserves the BE.
            return existingState.with(property, true);
        } else if (existingState.getBlock() instanceof CornerBlock) {
            // Transition case (Diff Material).
            // Since we have a UseBlockCallback in Reshaped.java handling this,
            // we return null here so standard placement doesn't run.
            // If standard placement ran, it would return defaultState (Step 191 logic below),
            // which would replace the block and lose data.
            return null;
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
            // If we placed into a MixedCornerBlock, we need to populate the BE for the new quadrant.
            // Note: 'state' passed here is the state *after* placement.
            // If we merged into Mixed, 'state' is MixedCornerBlock.
            if (state.getBlock() instanceof MixedCornerBlock) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof CornerBlockEntity cbe) {
                    if (itemStack.getItem() instanceof BlockItem blockItem) {
                        Block block = blockItem.getBlock();
                        // Find which quadrant matches the property we just added.
                        // Wait, we don't know which property we added easily here without re-calculating or iterating.
                        // Iterating is fine. If bit is set and BE is null, fill it.
                        // This logic works for both "Add to Mixed" and "Transition" (if Callback used standard logic, but it doesn't).

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
    }

    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement) {
        var quadrant = net.f3rr3.reshaped.util.BlockSegmentUtils.getQuadrantFromHit(hitX, hitY, hitZ, side, isPlacement, true);
        if (quadrant.isUp()) {
            if (!quadrant.isWest()) return quadrant.isPlusZ() ? UP_SE : UP_NE;
            else return quadrant.isPlusZ() ? UP_SW : UP_NW;
        } else {
            if (!quadrant.isWest()) return quadrant.isPlusZ() ? DOWN_SE : DOWN_NE;
            else return quadrant.isPlusZ() ? DOWN_SW : DOWN_NW;
        }
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.corner";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(DOWN_NW, DOWN_NE, DOWN_SW, DOWN_SE, UP_NW, UP_NE, UP_SW, UP_SE);
    }
}
