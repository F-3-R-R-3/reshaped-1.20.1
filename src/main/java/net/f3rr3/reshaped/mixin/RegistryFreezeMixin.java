package net.f3rr3.reshaped.mixin;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.matrix.MatrixRebuilder;
import net.minecraft.registry.Registries;
import net.minecraft.registry.SimpleRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleRegistry.class)
public class RegistryFreezeMixin {
    @Inject(method = "freeze", at = @At("HEAD"))
    private void reshaped$prepareMatrixBeforeBlockRegistryFreeze(CallbackInfoReturnable<SimpleRegistry<?>> cir) {
        if ((Object) this == Registries.BLOCK) {
            Reshaped.LOGGER.info("[RegistryFreezeMixin] Block registry freeze intercepted. Preparing matrix...");
            MatrixRebuilder.prepareForRegistryFreeze(Reshaped.MATRIX);
        }
    }
}
