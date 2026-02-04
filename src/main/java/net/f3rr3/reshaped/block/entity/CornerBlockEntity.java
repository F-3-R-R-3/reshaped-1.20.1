package net.f3rr3.reshaped.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Block entity for CornerBlock storing 8 corner materials.
 * Order: DOWN_NW, DOWN_NE, DOWN_SW, DOWN_SE, UP_NW, UP_NE, UP_SW, UP_SE
 */
public class CornerBlockEntity extends MixedBlockEntity {
    public static BlockEntityType<CornerBlockEntity> TYPE;

    public CornerBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state, 8);
    }

    // Alias methods for backward compatibility
    public void setCornerMaterial(int index, Identifier materialId) {
        setMaterial(index, materialId);
    }

    public Identifier getCornerMaterial(int index) {
        return getMaterial(index);
    }

    public static void register() {
        // Called once all blocks are registered
    }
}
