package net.f3rr3.reshaped.mixin.client;

import net.f3rr3.reshaped.client.ModKeybindings;
import net.f3rr3.reshaped.client.RadialMenuOpener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardInputMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        // Action 1 is press
        if (ModKeybindings.isRadialMenuKey(key) && action == 1) {
            if (RadialMenuOpener.tryOpen(client)) {
                ci.cancel();
            }
        }
    }
}
