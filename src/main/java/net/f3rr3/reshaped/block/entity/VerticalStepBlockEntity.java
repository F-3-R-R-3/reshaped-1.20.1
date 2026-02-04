package net.f3rr3.reshaped.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for VerticalStepBlock storing 4 quadrant materials.
 * Order: NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST
 */
public class VerticalStepBlockEntity extends MixedBlockEntity {
    public static BlockEntityType<VerticalStepBlockEntity> TYPE;

    public VerticalStepBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state, 4);
    }
}
