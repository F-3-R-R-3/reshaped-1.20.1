package net.f3rr3.reshaped.block.VerticalSlab;

import net.f3rr3.reshaped.block.Template.MixedBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for VerticalSlabBlock storing 2 segment materials.
 * Order: NEGATIVE, POSITIVE (referring to position along axis)
 */
public class VerticalSlabBlockEntity extends MixedBlockEntity {
    public static BlockEntityType<VerticalSlabBlockEntity> TYPE;

    public VerticalSlabBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state, 2);
    }
}
