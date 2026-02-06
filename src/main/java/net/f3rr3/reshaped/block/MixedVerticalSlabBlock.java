package net.f3rr3.reshaped.block;

import net.f3rr3.reshaped.block.entity.VerticalSlabBlockEntity;
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
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class MixedVerticalSlabBlock extends ReshapedBlock implements BlockEntityProvider {
    public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.of("axis", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);
    // Negative = Lower Coordinates (North or West)
    // Positive = Higher Coordinates (South or East)
    public static final BooleanProperty NEGATIVE = BooleanProperty.of("negative");
    public static final BooleanProperty POSITIVE = BooleanProperty.of("positive");

    public MixedVerticalSlabBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(AXIS, Direction.Axis.Z)
                .with(NEGATIVE, false).with(POSITIVE, false)
                .with(WATERLOGGED, false));
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new VerticalSlabBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction.Axis axis = state.get(AXIS);
        VoxelShape shape = VoxelShapes.empty();

        if (axis == Direction.Axis.Z) {
            // Negative = South (Z=8-16) [North Facing]
            // Positive = North (Z=0-8) [South Facing]
            if (state.get(NEGATIVE)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 0, 8, 16, 16, 16));
            if (state.get(POSITIVE)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 0, 0, 16, 16, 8));
        } else {
            // Negative = East (X=8-16) [West Facing]
            // Positive = West (X=0-8) [East Facing]
            if (state.get(NEGATIVE)) shape = VoxelShapes.union(shape, Block.createCuboidShape(8, 0, 0, 16, 16, 16));
            if (state.get(POSITIVE)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 0, 0, 8, 16, 16));
        }

        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        if (itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof VerticalSlabBlock) {
             BooleanProperty property = getPropertyFromHit(context.getHitPos().x - context.getBlockPos().getX(), 
                                                           context.getHitPos().y - context.getBlockPos().getY(), 
                                                           context.getHitPos().z - context.getBlockPos().getZ(), 
                                                           context.getSide(),
                                                           true,
                                                           state);
             if (property != null && !state.get(property)) {
                 return true;
             }
        }
        return super.canReplace(state, context);
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        BlockState existingState = ctx.getWorld().getBlockState(pos);
        
        BooleanProperty property = getPropertyFromHit(ctx.getHitPos().x - pos.getX(), 
                                                      ctx.getHitPos().y - pos.getY(), 
                                                      ctx.getHitPos().z - pos.getZ(), 
                                                      ctx.getSide(),
                                                      true,
                                                      existingState);
        
        if (property != null && existingState.isOf(this)) {
            return existingState.with(property, true);
        }
        return null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof VerticalSlabBlockEntity verticalSlabBlockEntity) {
                 if (itemStack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    
                    BooleanProperty[] allProps = {NEGATIVE, POSITIVE};
                    for (int i = 0; i < 2; i++) {
                        if (state.get(allProps[i]) && verticalSlabBlockEntity.getMaterial(i) == null) {
                            verticalSlabBlockEntity.setMaterial(i, Registries.BLOCK.getId(block));
                        }
                    }
                }
            }
        }
    }

    public BooleanProperty getPropertyFromHit(double hitX, double hitY, double hitZ, Direction side, boolean isPlacement, BlockState state) {
        Direction.Axis axis = state.get(AXIS);
        
        // Offset logic:
        // If placement, aim slightly in direction of normal (to find empty space adjacent).
        // If breaking, aim slightly opposite to normal (to find occupied space behind face).
        double offset = isPlacement ? 0.001 : -0.001;
        double x = hitX + side.getOffsetX() * offset;
        double z = hitZ + side.getOffsetZ() * offset;
        
        if (axis == Direction.Axis.Z) {
            // Z-Axis (North-South)
            // High Z (South Half) is NEGATIVE property now
            // Low Z (North Half) is POSITIVE property now
            return z > 0.5 ? NEGATIVE : POSITIVE;
        } else {
            // X-Axis (West-East)
            // High X (East Half) is NEGATIVE property now
            // Low X (West Half) is POSITIVE property now
            return x > 0.5 ? NEGATIVE : POSITIVE;
        }
    }
    
    // Helper to map Direction of VerticalSlab to Property
    public static BooleanProperty getPropertyForDirection(Direction facing, Direction.Axis axis) {
        if (axis == Direction.Axis.Z) {
            // NORTH (Negative Z Face) -> Negative Property (South Shape)
            // SOUTH (Positive Z Face) -> Positive Property (North Shape)
            if (facing == Direction.NORTH) return NEGATIVE;
            if (facing == Direction.SOUTH) return POSITIVE;
        } else {
            // WEST (Negative X Face) -> Negative Property (East Shape)
            // EAST (Positive X Face) -> Positive Property (West Shape)
            if (facing == Direction.WEST) return NEGATIVE;
            if (facing == Direction.EAST) return POSITIVE;
        }
        return null;
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.mixed_vertical_slab";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(AXIS, NEGATIVE, POSITIVE);
    }
}
