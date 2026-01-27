package net.f3rr3.reshaped.block;

import net.f3rr3.reshaped.block.entity.CornerBlockEntity;
import net.minecraft.block.*;
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

        if (existingState.isOf(this)) {
            return existingState.with(property, true);
        }

        return null; // Should not happen given intended usage
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof CornerBlockEntity cbe) {
                // When we add a piece to a mixed block, we update the BE
                if (itemStack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    
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
