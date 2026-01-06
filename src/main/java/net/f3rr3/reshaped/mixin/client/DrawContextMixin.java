package net.f3rr3.reshaped.mixin.client;

import net.f3rr3.reshaped.Reshaped;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Unique
    private static final Identifier OVERLAY = new Identifier(Reshaped.MOD_ID, "textures/gui/item_overlay.png");

    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;III)V", at = @At("TAIL"))
    private void onDrawItem(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (Reshaped.MATRIX != null && Reshaped.MATRIX.hasBlock(block)) {
                DrawContext context = (DrawContext) (Object) this;
                
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 250); 
                
                // Draw the overlay
                // We use 0, 0 for UV and 16, 16 for size, assuming the texture is a 16x16 icon.
                context.drawTexture(OVERLAY, x, y, 0, 0, 16, 16, 16, 16);
                
                context.getMatrices().pop();
            }
        }
    }
}
