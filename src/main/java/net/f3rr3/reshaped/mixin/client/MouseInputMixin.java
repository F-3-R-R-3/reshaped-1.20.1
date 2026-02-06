package net.f3rr3.reshaped.mixin.client;

import net.f3rr3.reshaped.client.ModKeybindings;
import net.f3rr3.reshaped.client.RadialMenuOpener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseInputMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        // Check if it's our keybind (default: Left Click) and it's a press action
        if (ModKeybindings.isRadialMenuButton(button) && action == 1) {
            if (RadialMenuOpener.tryOpen(client)) {
                ci.cancel();
            }
        }
    }
}
