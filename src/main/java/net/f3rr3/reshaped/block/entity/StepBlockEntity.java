package net.f3rr3.reshaped.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for StepBlock storing 4 segment materials.
 * Order: DOWN_FRONT, DOWN_BACK, UP_FRONT, UP_BACK
 */
public class StepBlockEntity extends MixedBlockEntity {
    public static BlockEntityType<StepBlockEntity> TYPE;

    public StepBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state, 4);
    }
}
