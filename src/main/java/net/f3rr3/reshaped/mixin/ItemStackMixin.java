package net.f3rr3.reshaped.mixin;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.util.BlockSourceTracker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Inject(method = "isSuitableFor", at = @At("HEAD"), cancellable = true)
    private void reshaped$useBaseBlockTool(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        Block block = state.getBlock();
        if (!Reshaped.MOD_ID.equals(Registries.BLOCK.getId(block).getNamespace())) {
            return;
        }

        Block source = null;
        if (block instanceof BlockSourceTracker tracker) {
            source = tracker.reshaped$getSourceBlock();
        }
        if (source == null && Reshaped.MATRIX != null) {
            source = Reshaped.MATRIX.getBaseBlock(block);
        }

        if (source != null && source != block) {
            cir.setReturnValue(((ItemStack) (Object) this).isSuitableFor(source.getDefaultState()));
        }
    }

    @Inject(method = "getMiningSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void reshaped$useBaseBlockMiningSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        Block block = state.getBlock();
        if (!Reshaped.MOD_ID.equals(Registries.BLOCK.getId(block).getNamespace())) {
            return;
        }

        Block source = null;
        if (block instanceof BlockSourceTracker tracker) {
            source = tracker.reshaped$getSourceBlock();
        }
        if (source == null && Reshaped.MATRIX != null) {
            source = Reshaped.MATRIX.getBaseBlock(block);
        }

        if (source != null && source != block) {
            cir.setReturnValue(((ItemStack) (Object) this).getMiningSpeedMultiplier(source.getDefaultState()));
        }
    }
}
