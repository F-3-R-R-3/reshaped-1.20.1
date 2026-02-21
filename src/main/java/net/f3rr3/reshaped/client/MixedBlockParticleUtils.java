package net.f3rr3.reshaped.client;

import net.f3rr3.reshaped.block.Corner.MixedCornerBlock;
import net.f3rr3.reshaped.block.Slab.MixedSlabBlock;
import net.f3rr3.reshaped.block.Step.MixedStepBlock;
import net.f3rr3.reshaped.block.VerticalStep.MixedVerticalStepBlock;
import net.f3rr3.reshaped.block.VerticalSlab.MixedVerticalSlabBlock;
import net.f3rr3.reshaped.block.Template.MixedBlockEntity;
import net.f3rr3.reshaped.util.BlockSegmentUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;

public final class MixedBlockParticleUtils {
    private static final BooleanProperty[] SLAB_PROPERTIES = {MixedSlabBlock.BOTTOM, MixedSlabBlock.TOP};
    private static final BooleanProperty[] VERTICAL_SLAB_PROPERTIES = {MixedVerticalSlabBlock.NEGATIVE, MixedVerticalSlabBlock.POSITIVE};

    private MixedBlockParticleUtils() {
    }

    public static Identifier resolveMaterialForHit(BlockRenderView world, BlockPos pos, BlockState state, BlockHitResult hit) {
        BooleanProperty property = null;
        BooleanProperty[] properties = null;

        Vec3d localHit = hit.getPos().subtract(pos.getX(), pos.getY(), pos.getZ());

        if (state.getBlock() instanceof MixedCornerBlock mixedCornerBlock) {
            property = mixedCornerBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hit.getSide(), false);
            properties = BlockSegmentUtils.CORNER_PROPERTIES;
        } else if (state.getBlock() instanceof MixedStepBlock mixedStepBlock) {
            property = mixedStepBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hit.getSide(), false, state);
            properties = BlockSegmentUtils.STEP_PROPERTIES;
        } else if (state.getBlock() instanceof MixedVerticalStepBlock mixedVerticalStepBlock) {
            property = mixedVerticalStepBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hit.getSide(), false);
            properties = BlockSegmentUtils.VERTICAL_STEP_PROPERTIES;
        } else if (state.getBlock() instanceof MixedSlabBlock mixedSlabBlock) {
            property = mixedSlabBlock.getPropertyFromHit(localHit.y, hit.getSide(), false);
            properties = SLAB_PROPERTIES;
        } else if (state.getBlock() instanceof MixedVerticalSlabBlock mixedVerticalSlabBlock) {
            property = mixedVerticalSlabBlock.getPropertyFromHit(localHit.x, localHit.y, localHit.z, hit.getSide(), false, state);
            properties = VERTICAL_SLAB_PROPERTIES;
        }

        if (property == null || !state.get(property)) {
            return null;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MixedBlockEntity mixedBlockEntity) {
            for (int i = 0; i < properties.length; i++) {
                if (properties[i] == property) {
                    return mixedBlockEntity.getMaterial(i);
                }
            }
        }

        return null;
    }
}
