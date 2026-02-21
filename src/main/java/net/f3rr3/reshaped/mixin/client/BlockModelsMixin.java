package net.f3rr3.reshaped.mixin.client;

import net.f3rr3.reshaped.block.Corner.MixedCornerBlock;
import net.f3rr3.reshaped.block.Slab.MixedSlabBlock;
import net.f3rr3.reshaped.block.Step.MixedStepBlock;
import net.f3rr3.reshaped.block.VerticalStep.MixedVerticalStepBlock;
import net.f3rr3.reshaped.block.VerticalSlab.MixedVerticalSlabBlock;
import net.f3rr3.reshaped.client.MixedBlockParticleTracker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModels.class)
public class BlockModelsMixin {
    @Inject(method = "getModelParticleSprite", at = @At("HEAD"), cancellable = true)
    private void reshaped$getModelParticleSprite(BlockState state, CallbackInfoReturnable<Sprite> cir) {
        Block block = state.getBlock();
        if (!(block instanceof MixedCornerBlock
                || block instanceof MixedStepBlock
                || block instanceof MixedVerticalStepBlock
                || block instanceof MixedSlabBlock
                || block instanceof MixedVerticalSlabBlock)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderView world = client.world;
        BlockPos pos = client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit ? hit.getBlockPos() : null;
        if (world == null || pos == null) {
            return;
        }

        Identifier override = MixedBlockParticleTracker.getOverride(world, pos);
        if (override == null) {
            return;
        }

        Block materialBlock = Registries.BLOCK.get(override);
        if (materialBlock == Blocks.AIR) {
            return;
        }

        BakedModel model = client.getBlockRenderManager().getModel(materialBlock.getDefaultState());
        if (model != null) {
            cir.setReturnValue(model.getParticleSprite());
        }
    }
}
