package net.f3rr3.reshaped.client;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.gui.RadialMenuScreen;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

import java.util.List;

public final class RadialMenuOpener {
    private RadialMenuOpener() {
    }

    public static boolean tryOpen(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) {
            return false;
        }

        ItemStack stack = client.player.getMainHandStack();
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();

            if (Reshaped.MATRIX != null && Reshaped.MATRIX.hasBlock(block)) {
                List<Block> column = Reshaped.MATRIX.getColumn(block);
                if (!column.isEmpty()) {
                    int slot = client.player.getInventory().selectedSlot;
                    Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
                    client.execute(() -> client.setScreen(new RadialMenuScreen(column, slot, block, baseBlock)));
                    return true;
                }
            }
        }

        return false;
    }
}
