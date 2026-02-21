package net.f3rr3.reshaped.mixin;

import net.f3rr3.reshaped.util.BlockSourceTracker;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.Settings.class)
public class AbstractBlockSettingsMixin implements BlockSourceTracker {
    @Unique
    private Block reshaped$sourceBlock;

    @Override
    public void reshaped$setSourceBlock(Block block) {
        this.reshaped$sourceBlock = block;
    }

    @Override
    public Block reshaped$getSourceBlock() {
        return this.reshaped$sourceBlock;
    }

    @Inject(method = "copy", at = @At("RETURN"))
    private static void onCopy(AbstractBlock block, CallbackInfoReturnable<AbstractBlock.Settings> cir) {
        if (block instanceof Block b) {
            ((BlockSourceTracker) cir.getReturnValue()).reshaped$setSourceBlock(b);
        }
    }
}
