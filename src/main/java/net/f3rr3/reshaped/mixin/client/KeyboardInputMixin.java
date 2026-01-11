package net.f3rr3.reshaped.mixin.client;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.ModKeybindings;
import net.f3rr3.reshaped.client.gui.RadialMenuScreen;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Keyboard.class)
public abstract class KeyboardInputMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (client.player != null && client.currentScreen == null) {
            // Action 1 is press
            if (ModKeybindings.isRadialMenuKey(key) && action == 1) {
                ItemStack stack = client.player.getMainHandStack();
                
                if (stack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    
                    if (Reshaped.MATRIX != null && Reshaped.MATRIX.hasBlock(block)) {
                        List<Block> column = Reshaped.MATRIX.getColumn(block);
                        if (!column.isEmpty()) {
                            int slot = client.player.getInventory().selectedSlot;
                            client.execute(() -> {
                                client.setScreen(new RadialMenuScreen(column, slot, block));
                            });
                            ci.cancel();
                        }
                    }
                }
            }
        }
    }
}
