package net.f3rr3.reshaped.block.VerticalSlab;

import net.f3rr3.reshaped.block.Template.ReshapedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class VerticalSlabBlock extends ReshapedBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<SlabType> TYPE = Properties.SLAB_TYPE;

    protected static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
    protected static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);
    protected static final VoxelShape WEST_SHAPE = Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public VerticalSlabBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(TYPE, SlabType.BOTTOM) // We reuse SlabType: BOTTOM=Single, DOUBLE=Double. TOP is unused but required by enum.
                .with(WATERLOGGED, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (state.get(TYPE) == SlabType.DOUBLE) {
            return VoxelShapes.fullCube();
        }
        return switch (state.get(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos blockPos = ctx.getBlockPos();
        BlockState blockState = ctx.getWorld().getBlockState(blockPos);

        if (blockState.isOf(this)) {
            return blockState.with(TYPE, SlabType.DOUBLE).with(WATERLOGGED, false);
        }

        FluidState fluidState = ctx.getWorld().getFluidState(blockPos);
        BlockState defaultState = this.getDefaultState().with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);

        // Simple logic:
        return defaultState.with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext context) {
        ItemStack itemStack = context.getStack();
        SlabType slabType = state.get(TYPE);

        if (slabType == SlabType.DOUBLE || !itemStack.isOf(this.asItem())) {
            return false;
        }

        if (!context.canReplaceExisting()) {
            return true;
        }

        // Allow merging if we click on the internal (exposed) face of the existing vertical slab
        // A NORTH-facing slab has its exposed face at z=8, pointing NORTH
        // So clicking on the NORTH face of a NORTH-facing slab should merge
        return state.get(FACING) == context.getSide();
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.vertical_slab";
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        builder.add(LootContextParameters.BLOCK_STATE, state);
        LootContextParameterSet context = builder.build(LootContextTypes.BLOCK);
        ItemStack tool = context.getOptional(LootContextParameters.TOOL);
        if (tool == null) {
            tool = ItemStack.EMPTY;
        }
        if (state.isToolRequired() && !tool.isEmpty() && !tool.isSuitableFor(state)) {
            return List.of();
        }

        int count = state.get(TYPE) == SlabType.DOUBLE ? 2 : 1;
        List<ItemStack> drops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drops.add(new ItemStack(this));
        }
        return drops;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(FACING, TYPE);
    }
}
