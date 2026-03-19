package net.f3rr3.reshaped.mixin.client;

import net.f3rr3.reshaped.Reshaped;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderLayers.class)
public class RenderLayersMixin {
	@Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
	private static void reshaped$useBaseLayerForVariants(BlockState state, CallbackInfoReturnable<RenderLayer> cir) {
		if (state == null || Reshaped.MATRIX == null) return;

		Block baseBlock = Reshaped.MATRIX.getBaseBlock(state.getBlock());
		if (baseBlock == null) return;

		cir.setReturnValue(RenderLayers.getBlockLayer(baseBlock.getDefaultState()));
	}
}
