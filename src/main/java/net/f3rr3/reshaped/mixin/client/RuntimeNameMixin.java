package net.f3rr3.reshaped.mixin.client;

import net.f3rr3.reshaped.Reshaped;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.resource.language.TranslationStorage;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(TranslationStorage.class)
public class RuntimeNameMixin {

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void onGet(String key, String fallback, CallbackInfoReturnable<String> cir) {
        if (key.startsWith("block.reshaped.") || key.startsWith("item.reshaped.")) {
            String path = key.substring(key.lastIndexOf('.') + 1);
            String result = reshaped$generateName(path);
            if (result != null) {
                cir.setReturnValue(result);
            }
        }
    }

    @Unique
    private String reshaped$generateName(String path) {
        if (Reshaped.MATRIX == null) return null;

        // Try to find the block in the registry using the path
        Identifier id = new Identifier(Reshaped.MOD_ID, path);
        Block block = Registries.BLOCK.get(id);
        
        // If not found as a block, it might be an item-only registration (unlikely for us)
        // or we just can't find it.
        if (block == Blocks.AIR) return null;

        // Use the matrix to find the base block
        Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
        if (baseBlock == null) return null;

        String baseName = baseBlock.getName().getString();
        String cleanedBase = baseName
            .replace(" Planks", "")
            .replace(" Block", "")
            .replace(" Bricks", " Brick");

        if (path.endsWith("_vertical_slab")) return cleanedBase + " Vertical Slab";
        if (path.endsWith("_slab")) return cleanedBase + " Slab";
        if (path.endsWith("_stairs")) return cleanedBase + " Stairs";
        
        return null;
    }
}
