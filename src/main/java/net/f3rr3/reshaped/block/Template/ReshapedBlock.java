package net.f3rr3.reshaped.block.Template;

import net.f3rr3.reshaped.block.Corner.CornerBlock;
import net.f3rr3.reshaped.block.Corner.MixedCornerBlock;
import net.f3rr3.reshaped.block.Slab.MixedSlabBlock;
import net.f3rr3.reshaped.block.Step.MixedStepBlock;
import net.f3rr3.reshaped.block.Step.StepBlock;
import net.f3rr3.reshaped.block.VerticalSlab.MixedVerticalSlabBlock;
import net.f3rr3.reshaped.block.VerticalStep.MixedVerticalStepBlock;
import net.f3rr3.reshaped.block.VerticalStep.VerticalStepBlock;
import net.f3rr3.reshaped.util.BlockSegmentUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

import java.util.List;

@SuppressWarnings("deprecation")
public abstract class ReshapedBlock extends Block implements Waterloggable {
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    public ReshapedBlock(Settings settings) {
        super(settings);
    }

    protected static Vec3d getLocalHit(ItemPlacementContext context) {
        BlockPos pos = context.getBlockPos();
        return context.getHitPos().subtract(pos.getX(), pos.getY(), pos.getZ());
    }

    private static int countSegments(BlockState state, BooleanProperty[] properties) {
        int count = 0;
        for (BooleanProperty property : properties) {
            if (state.get(property)) {
                count++;
            }
        }
        return Math.max(count, 1);
    }

    private static List<ItemStack> buildMixedDrops(BlockState state, BooleanProperty[] properties, MixedBlockEntity blockEntity) {
        java.util.ArrayList<ItemStack> drops = new java.util.ArrayList<>();
        for (int i = 0; i < properties.length; i++) {
            if (!state.get(properties[i])) {
                continue;
            }
            net.minecraft.util.Identifier materialId = blockEntity.getMaterial(i);
            if (materialId == null) {
                continue;
            }
            Block block = Registries.BLOCK.get(materialId);
            if (block != Blocks.AIR) {
                drops.add(new ItemStack(block.asItem()));
            }
        }
        return drops;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type) {
        return false;
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

        net.minecraft.block.entity.BlockEntity be = context.getOptional(LootContextParameters.BLOCK_ENTITY);
        if (be instanceof MixedBlockEntity mixedBlockEntity) {
            BooleanProperty[] props = null;
            if (state.getBlock() instanceof MixedCornerBlock) {
                props = BlockSegmentUtils.CORNER_PROPERTIES;
            } else if (state.getBlock() instanceof MixedStepBlock) {
                props = BlockSegmentUtils.STEP_PROPERTIES;
            } else if (state.getBlock() instanceof MixedVerticalStepBlock) {
                props = BlockSegmentUtils.VERTICAL_STEP_PROPERTIES;
            } else if (state.getBlock() instanceof MixedSlabBlock) {
                props = new BooleanProperty[]{MixedSlabBlock.BOTTOM, MixedSlabBlock.TOP};
            } else if (state.getBlock() instanceof MixedVerticalSlabBlock) {
                props = new BooleanProperty[]{MixedVerticalSlabBlock.NEGATIVE, MixedVerticalSlabBlock.POSITIVE};
            }

            if (props != null) {
                List<ItemStack> drops = buildMixedDrops(state, props, mixedBlockEntity);
                if (!drops.isEmpty()) {
                    return drops;
                }
            }
        }

        int count = 1;
        if (state.getBlock() instanceof CornerBlock || state.getBlock() instanceof MixedCornerBlock) {
            count = countSegments(state, BlockSegmentUtils.CORNER_PROPERTIES);
        } else if (state.getBlock() instanceof StepBlock || state.getBlock() instanceof MixedStepBlock) {
            count = countSegments(state, BlockSegmentUtils.STEP_PROPERTIES);
        } else if (state.getBlock() instanceof VerticalStepBlock || state.getBlock() instanceof MixedVerticalStepBlock) {
            count = countSegments(state, BlockSegmentUtils.VERTICAL_STEP_PROPERTIES);
        } else if (state.getBlock() instanceof MixedSlabBlock) {
            count = countSegments(state, new BooleanProperty[]{MixedSlabBlock.BOTTOM, MixedSlabBlock.TOP});
        } else if (state.getBlock() instanceof MixedVerticalSlabBlock) {
            count = countSegments(state, new BooleanProperty[]{MixedVerticalSlabBlock.NEGATIVE, MixedVerticalSlabBlock.POSITIVE});
        }

        ItemStack drop = new ItemStack(this);
        if (count <= 1) {
            return List.of(drop);
        }

        java.util.ArrayList<ItemStack> drops = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drops.add(drop.copy());
        }
        return drops;
    }

    @Override
    public boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction) {
        return super.isSideInvisible(state, stateFrom, direction); // returns false
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }
}
