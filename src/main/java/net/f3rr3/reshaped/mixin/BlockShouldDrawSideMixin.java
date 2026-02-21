package net.f3rr3.reshaped.mixin;

import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.util.BlockMatrix;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockShouldDrawSideMixin {
    @Shadow
    @Final
    private static ThreadLocal<Object2ByteLinkedOpenHashMap<Block.NeighborGroup>> FACE_CULL_MAP;

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void reshaped$shouldDrawSideSameMatrixGroup(
            BlockState state,
            BlockView world,
            BlockPos pos,
            Direction side,
            BlockPos otherPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockState blockState = world.getBlockState(otherPos);
        if (state.isSideInvisible(blockState, side)) {
            cir.setReturnValue(false);
            return;
        }

        if (sameMatrixGroup(state.getBlock(), blockState.getBlock())) {
            Block.NeighborGroup neighborGroup = new Block.NeighborGroup(state, blockState, side);
            Object2ByteLinkedOpenHashMap<Block.NeighborGroup> cache =
                    FACE_CULL_MAP.get();

            byte cached = cache.getAndMoveToFirst(neighborGroup);
            if (cached != 127) {
                cir.setReturnValue(cached != 0);
                return;
            }

            VoxelShape selfFace = state.getCullingFace(world, pos, side);
            if (selfFace.isEmpty()) {
                cir.setReturnValue(true);
                return;
            }

            VoxelShape otherFace = blockState.getCullingFace(world, otherPos, side.getOpposite());
            boolean shouldDraw = VoxelShapes.matchesAnywhere(selfFace, otherFace, BooleanBiFunction.ONLY_FIRST);

            if (cache.size() == 2048) {
                cache.removeLastByte();
            }
            cache.putAndMoveToFirst(neighborGroup, (byte) (shouldDraw ? 1 : 0));

            cir.setReturnValue(shouldDraw);
        }

        // Not a reshaped matrix pair: keep vanilla culling behavior.
        // Returning here (without setting cir) lets the original method run.
    }

    @Unique
    private static boolean sameMatrixGroup(Block self, Block neighbor) {
        BlockMatrix matrix = Reshaped.MATRIX;
        if (matrix == null) {
            return false;
        }
        if (!matrix.hasBlock(self) && !matrix.hasBlock(neighbor)) {
            return false;
        }
        if (self == neighbor) {
            return true;
        }

        Block selfBase = matrix.getBaseBlock(self);
        Block neighborBase = matrix.getBaseBlock(neighbor);
        if (selfBase == null) {
            selfBase = self;
        }
        if (neighborBase == null) {
            neighborBase = neighbor;
        }

        return selfBase == neighborBase;
    }
}
