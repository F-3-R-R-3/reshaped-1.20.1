package net.f3rr3.reshaped.mixin.client;

import net.f3rr3.reshaped.client.gui.RadialMenuScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(
            method = "render",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hideHudWhenUIActive(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (RadialMenuScreen.isOpen()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderHotbar",
            at = @At("HEAD")
    )
    private void allowHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
    }
}
