package net.f3rr3.reshaped.network;

import net.f3rr3.reshaped.Reshaped;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

public class NetworkHandler {

    /**
     * Register server-side packet receivers.
     * Call this from the main mod initializer.
     */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ConvertBlockPacket.ID, (server, player, handler, buf, responseSender) -> {
            ConvertBlockPacket packet = ConvertBlockPacket.fromBuffer(buf);
            server.execute(() -> handleConvertBlock(player, packet));
        });
    }

    /**
     * Send a convert block packet from client to server.
     */
    public static void sendConvertBlockPacket(Identifier targetBlockId, int slot) {
        ConvertBlockPacket packet = new ConvertBlockPacket(targetBlockId, slot);
        ClientPlayNetworking.send(ConvertBlockPacket.ID, packet.toBuffer());
    }

    private static void handleConvertBlock(ServerPlayerEntity player, ConvertBlockPacket packet) {
        int slot = packet.slot();
        Identifier targetId = packet.targetBlockId();

        // Validate slot
        if (slot < 0 || slot >= player.getInventory().size()) {
            Reshaped.LOGGER.warn("Invalid slot {} in convert block packet from {}", slot, player.getName().getString());
            return;
        }

        ItemStack currentStack = player.getInventory().getStack(slot);
        
        // Validate the current item is a BlockItem
        if (!(currentStack.getItem() instanceof BlockItem currentBlockItem)) {
            Reshaped.LOGGER.warn("Slot {} does not contain a BlockItem for {}", slot, player.getName().getString());
            return;
        }

        Block currentBlock = currentBlockItem.getBlock();
        
        // Check if the current block is in the matrix
        if (Reshaped.MATRIX == null || !Reshaped.MATRIX.hasBlock(currentBlock)) {
            Reshaped.LOGGER.warn("Block {} is not in the matrix for {}", currentBlock, player.getName().getString());
            return;
        }

        // Get the target block
        Block targetBlock = Registries.BLOCK.get(targetId);
        if (targetBlock == Blocks.AIR) {
            Reshaped.LOGGER.warn("Target block {} does not exist for {}", targetId, player.getName().getString());
            return;
        }

        // Check if target block is in the same column as the current block
        List<Block> column = Reshaped.MATRIX.getColumn(currentBlock);
        if (!column.contains(targetBlock)) {
            Reshaped.LOGGER.warn("Target block {} is not in the same column as {} for {}", 
                targetId, currentBlock, player.getName().getString());
            return;
        }

        // Perform the conversion - preserve count and NBT if any
        int count = currentStack.getCount();
        ItemStack newStack = new ItemStack(targetBlock.asItem(), count);
        
        // Copy NBT data if present (for blocks with special data)
        if (currentStack.hasNbt()) {
            net.minecraft.nbt.NbtCompound nbt = currentStack.getNbt();
            if (nbt != null) {
                newStack.setNbt(nbt.copy());
            }
        }

        // Replace the item in the slot
        player.getInventory().setStack(slot, newStack);
        
        Reshaped.LOGGER.debug("Converted {} x{} to {} for {}", 
            currentBlock, count, targetBlock, player.getName().getString());
    }
}
