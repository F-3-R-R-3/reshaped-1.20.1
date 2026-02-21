package net.f3rr3.reshaped.block.Slab;

import net.f3rr3.reshaped.block.Template.MixedBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for SlabBlock storing 2 segment materials.
 * Order: BOTTOM, TOP
 */
public class SlabBlockEntity extends MixedBlockEntity {
    public static BlockEntityType<SlabBlockEntity> TYPE;

    public SlabBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state, 2);
    }
}
