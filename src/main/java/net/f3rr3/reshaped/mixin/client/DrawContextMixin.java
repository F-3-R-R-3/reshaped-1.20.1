package net.f3rr3.reshaped.mixin.client;

import me.shedaniel.autoconfig.AutoConfig;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.config.client.ModConfig;
import net.f3rr3.reshaped.client.gui.RadialMenuScreen;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Unique
    private static final ModConfig CONFIG = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
    @Unique
    private static final Identifier OVERLAY = new Identifier(Reshaped.MOD_ID, "textures/gui/plus_icon.png");
    @Unique
    private static final Identifier WAXED_OVERLAY = new Identifier(Reshaped.MOD_ID, "textures/gui/item_overlay_waxed.png");

    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;III)V", at = @At("TAIL"))
    private void onDrawItemSeeded(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        this.renderReshapedOverlays(stack, x, y);
    }

    @Inject(method = "drawItem(Lnet/minecraft/item/ItemStack;II)V", at = @At("TAIL"))
    private void onDrawItem(ItemStack stack, int x, int y, CallbackInfo ci) {
        this.renderReshapedOverlays(stack, x, y);
    }

    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;III)V", at = @At("TAIL"))
    private void onDrawItemWithEntity(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        this.renderReshapedOverlays(stack, x, y);
    }

    @Unique
    private void renderReshapedOverlays(ItemStack stack, int x, int y) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            DrawContext context = (DrawContext) (Object) this;
            MinecraftClient client = MinecraftClient.getInstance();
            String blockPath = Registries.BLOCK.getId(block).getPath();

            boolean isRadialMenu = client.currentScreen instanceof RadialMenuScreen;
            boolean isWaxed = blockPath.contains("waxed") && CONFIG.ItemBadges.enableWaxed;
            boolean inMatrix = Reshaped.MATRIX != null && Reshaped.MATRIX.hasBlock(block) && CONFIG.ItemBadges.enableRadial;

            if (isWaxed || (!isRadialMenu && inMatrix)) {
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 250);

                if (isWaxed) {
                    context.drawTexture(WAXED_OVERLAY, x, y, 0, 0, 16, 16, 16, 16);
                }
                
                if (!isRadialMenu && inMatrix) {
                    context.drawTexture(OVERLAY, x, y, 0, 0, 16, 16, 16, 16);
                }

                context.getMatrices().pop();
            }
        }
    }
}
