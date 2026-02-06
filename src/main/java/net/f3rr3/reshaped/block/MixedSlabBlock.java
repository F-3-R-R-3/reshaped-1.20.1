package net.f3rr3.reshaped.block;

import net.f3rr3.reshaped.block.entity.SlabBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class MixedSlabBlock extends ReshapedBlock implements BlockEntityProvider {
    public static final BooleanProperty BOTTOM = BooleanProperty.of("bottom");
    public static final BooleanProperty TOP = BooleanProperty.of("top");

    public MixedSlabBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(BOTTOM, false).with(TOP, false)
                .with(WATERLOGGED, false));
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SlabBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape shape = VoxelShapes.empty();
        if (state.get(BOTTOM)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 0, 0, 16, 8, 16));
        if (state.get(TOP)) shape = VoxelShapes.union(shape, Block.createCuboidShape(0, 8, 0, 16, 16, 16));
        return shape.isEmpty() ? VoxelShapes.fullCube() : shape;
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        if (itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock) {
             Vec3d localHit = getLocalHit(context);
             BooleanProperty property = getPropertyFromHit(localHit.y,
                     context.getSide(),
                     true);
             return property != null && !state.get(property);
        }
        return super.canReplace(state, context);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
          // This block usually formed solely by transformation
          BlockPos pos = ctx.getBlockPos();
          BlockState existingState = ctx.getWorld().getBlockState(pos);
          if (existingState.isOf(this)) {
              Vec3d localHit = getLocalHit(ctx);
              BooleanProperty property = getPropertyFromHit(localHit.y,
                      ctx.getSide(),
                      true);
               if (property != null && !existingState.get(property)) return existingState.with(property, true);
          }
          return null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SlabBlockEntity sbe) {
                 if (itemStack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    
                    if (state.get(BOTTOM) && sbe.getMaterial(0) == null) sbe.setMaterial(0, Registries.BLOCK.getId(block));
                    if (state.get(TOP) && sbe.getMaterial(1) == null) sbe.setMaterial(1, Registries.BLOCK.getId(block));
                }
            }
        }
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.mixed_slab";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(BOTTOM, TOP);
    }
    
    public BooleanProperty getPropertyFromHit(double hitY, net.minecraft.util.math.Direction side, boolean isPlacement) {
        double offset = isPlacement ? 0.001 : -0.001;
        double y = hitY + side.getOffsetY() * offset;
        return y < 0.5 ? BOTTOM : TOP;
    }
}
