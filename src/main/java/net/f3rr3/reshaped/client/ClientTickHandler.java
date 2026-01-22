package net.f3rr3.reshaped.client;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.gui.RadialMenuScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

import java.util.List;

public class ClientTickHandler {
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // If a screen is already open, we don't need to do anything here
            // The RadialMenuScreen itself handles closing if the key is released
            if (client.currentScreen != null) return;

            // Check if the trigger key is being held down
            boolean isHeld = ModKeybindings.OPEN_RADIAL_MENU.isPressed();

            if (isHeld) {
                // Key is held, but no screen is open.
                // Check if the current item is valid for the radial menu.
                ItemStack stack = client.player.getMainHandStack();
                if (stack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();

                    if (Reshaped.MATRIX != null && Reshaped.MATRIX.hasBlock(block)) {
                        List<Block> column = Reshaped.MATRIX.getColumn(block);
                        if (!column.isEmpty()) {
                            int slot = client.player.getInventory().selectedSlot;
                            Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
                            // Open the menu!
                            client.setScreen(new RadialMenuScreen(column, slot, block, baseBlock));
                        }
                    }
                }
            }
        });
    }
}
