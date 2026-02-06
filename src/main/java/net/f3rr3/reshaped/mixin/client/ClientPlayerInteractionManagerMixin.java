package net.f3rr3.reshaped.mixin.client;

import net.f3rr3.reshaped.client.MixedBlockParticleTracker;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void reshaped$attackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MixedBlockParticleTracker.captureFromCrosshair(pos);
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"))
    private void reshaped$updateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        MixedBlockParticleTracker.captureFromCrosshair(pos);
    }
}
