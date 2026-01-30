package net.f3rr3.reshaped.block;

import net.minecraft.block.*;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

public class VerticalStairsBlock extends ReshapedBlock {
    public static final EnumProperty<VerticalStairOrientation> ORIENTATION = EnumProperty.of("orientation", VerticalStairOrientation.class);

    public VerticalStairsBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState()
                .with(ORIENTATION, VerticalStairOrientation.MINUS_X_MINUS_Z)
                .with(WATERLOGGED, false));
    }

    public enum VerticalStairOrientation implements StringIdentifiable {
        PLUS_X_PLUS_Z("plus_x_plus_z"),
        MINUS_X_PLUS_Z("minus_x_plus_z"),
        PLUS_X_MINUS_Z("plus_x_minus_z"),
        MINUS_X_MINUS_Z("minus_x_minus_z");

        private final String name;

        VerticalStairOrientation(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return this.name;
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VerticalStairOrientation orientation = state.get(ORIENTATION);

        // A Vertical Stair is a 6/8 pillar (L-shape). 
        // 2 sub-cubes removed on top of each other = one full-height quadrant pillar removed.
        
        // Quadrants (Pillars)
        VoxelShape q1 = Block.createCuboidShape(8, 0, 8, 16, 16, 16); // +X +Z (South-East)
        VoxelShape q2 = Block.createCuboidShape(0, 0, 8, 8, 16, 16);  // -X +Z (South-West)
        VoxelShape q3 = Block.createCuboidShape(8, 0, 0, 16, 16, 8);  // +X -Z (North-East)
        VoxelShape q4 = Block.createCuboidShape(0, 0, 0, 8, 16, 8);   // -X -Z (North-West)

        // Orientation defines the quadrant that is REMOVED.
        return switch (orientation) {
            case PLUS_X_PLUS_Z -> VoxelShapes.union(q2, q3, q4);
            case MINUS_X_PLUS_Z-> VoxelShapes.union(q1, q3, q4);
            case PLUS_X_MINUS_Z -> VoxelShapes.union(q1, q2, q4);
            case MINUS_X_MINUS_Z -> VoxelShapes.union(q1, q2, q3);
        };
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos blockPos = ctx.getBlockPos();
        FluidState fluidState = ctx.getWorld().getFluidState(blockPos);
        
        // Diagonal placement system:
        // Determined by the player's looking direction.
        // The quadrant the player is looking towards becomes the "missing" pillar.
        
        float yaw = ctx.getPlayerYaw() % 360;
        if (yaw < 0) yaw += 360;
        
        VerticalStairOrientation orientation;
        
        // Minecraft Yaw: 0=South(+Z), 90=West(-X), 180=North(-Z), 270=East(+X)
        // To make the gap face the player, we remove the quadrant CLOSEST to the player.
        if (yaw >= 0 && yaw < 90) { // Looking Towards South-West (-X, +Z)
            orientation = VerticalStairOrientation.PLUS_X_MINUS_Z;  // Closest is North-East (+X, -Z)
        } else if (yaw >= 90 && yaw < 180) { // Looking Towards North-West (-X, -Z)
            orientation = VerticalStairOrientation.PLUS_X_PLUS_Z;  // Closest is South-East (+X, +Z)
        } else if (yaw >= 180 && yaw < 270) { // Looking Towards North-East (+X, -Z)
            orientation = VerticalStairOrientation.MINUS_X_PLUS_Z; // Closest is South-West (-X, +Z)
        } else { // Looking Towards South-East (+X, +Z)
            orientation = VerticalStairOrientation.MINUS_X_MINUS_Z; // Closest is North-West (-X, -Z)
        }

        return this.getDefaultState()
                .with(ORIENTATION, orientation)
                .with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    @Override
    public String getTranslationKey() {
        return "block.reshaped.vertical_stairs";
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ORIENTATION);
    }
}
