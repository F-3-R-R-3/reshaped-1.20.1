package net.f3rr3.reshaped.mixin.client;

import me.shedaniel.autoconfig.AutoConfig;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.config.client.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.client.item.BundleTooltipData;
import net.minecraft.client.item.TooltipData;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(ItemStack.class)
public class ItemStackTooltipDataMixin {
    @Unique
    private static final String RESHAPED_SELECTED_BUNDLE_ENTRY = "reshaped_selected_bundle_entry";

    @Inject(method = "getTooltipData", at = @At("HEAD"), cancellable = true)
    private void reshaped$addMatrixBundleTooltipData(CallbackInfoReturnable<Optional<TooltipData>> cir) {
        ModConfig config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

        if (!config.enableTooltip) {
            return;
        }
        ItemStack stack = (ItemStack) (Object) this;
        if (!(stack.getItem() instanceof BlockItem blockItem) || Reshaped.MATRIX == null) {
            return;
        }

        Block block = blockItem.getBlock();
        List<Block> column = Reshaped.MATRIX.getColumn(block);
        if (column.isEmpty()) {
            return;
        }

        DefaultedList<ItemStack> groupedStacks = DefaultedList.of();
        for (Block groupedBlock : column) {
            ItemStack groupedStack = new ItemStack(groupedBlock);
            if (groupedBlock == block) {
                groupedStack.getOrCreateNbt().putBoolean(RESHAPED_SELECTED_BUNDLE_ENTRY, true);
            }
            groupedStacks.add(groupedStack);
        }

        // Bundle occupancy is just used for the fill bar; scale by displayed entries.
        int bundleOccupancy = Math.min(64, groupedStacks.size() * 8);
        cir.setReturnValue(Optional.of(new BundleTooltipData(groupedStacks, bundleOccupancy)));
    }
}
