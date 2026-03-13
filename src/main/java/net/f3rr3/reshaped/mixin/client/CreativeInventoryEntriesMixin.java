package net.f3rr3.reshaped.mixin.client;

import me.shedaniel.autoconfig.AutoConfig;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.config.client.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.item.ItemGroup$EntriesImpl")
public class CreativeInventoryEntriesMixin {
    @Unique
    private static boolean reshaped$sanitizeCreativeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getCount() != 1) {
            stack.setCount(1);
        }
        return true;
    }

    @Inject(
            method = "add(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemGroup$StackVisibility;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void reshaped$hideAlternateMatrixBlocks(ItemStack stack, ItemGroup.StackVisibility visibility, CallbackInfo ci) {
        // Some mods feed empty/non-singleton stacks into creative entries, which crashes
        // in ItemGroup$EntriesImpl#add. Sanitize before vanilla validation.
        if (!reshaped$sanitizeCreativeStack(stack)) {
            ci.cancel();
            return;
        }

        if (Reshaped.MATRIX == null || !(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        ModConfig config;
        try {
            config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        } catch (Exception ignored) {
            return;
        }

        if (!config.hideAlternateBlocks) {
            return;
        }

        Block block = blockItem.getBlock();
        if (Reshaped.MATRIX.hasBlock(block) && Reshaped.MATRIX.getBaseBlock(block) != null) {
            ci.cancel();
        }
    }
}
