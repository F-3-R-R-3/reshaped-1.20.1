package net.f3rr3.reshaped.network;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.block.Template.MixedBlockEntity;
import net.f3rr3.reshaped.config.server.ServerConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkHandler {
    private static final Map<UUID, BlockPos> PENDING_GETDATA_REQUESTS = new ConcurrentHashMap<>();

    /**
     * Register server-side packet receivers.
     * Call this from the main mod initializer.
     */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ConvertBlockPacket.ID, (server, player, handler, buf, responseSender) -> {
            ConvertBlockPacket packet = ConvertBlockPacket.fromBuffer(buf);
            server.execute(() -> handleConvertBlock(player, packet));
        });

        ServerPlayNetworking.registerGlobalReceiver(ClientBlockEntityDataPacket.ID, (server, player, handler, buf, responseSender) -> {
            ClientBlockEntityDataPacket packet = ClientBlockEntityDataPacket.fromBuffer(buf);
            server.execute(() -> handleClientBlockEntityData(player, packet));
        });
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(RequestBlockEntityDataPacket.ID, (client, handler, buf, responseSender) -> {
            RequestBlockEntityDataPacket packet = RequestBlockEntityDataPacket.fromBuffer(buf);
            client.execute(() -> handleBlockEntityDataRequestFromServer(packet));
        });
    }

    /**
     * Send a convert block packet from client to server.
     */
    public static void sendConvertBlockPacket(Identifier targetBlockId, int slot) {
        ConvertBlockPacket packet = new ConvertBlockPacket(targetBlockId, slot);
        ClientPlayNetworking.send(ConvertBlockPacket.ID, packet.toBuffer());
    }

    public static void requestClientBlockData(ServerPlayerEntity player, BlockPos pos) {
        PENDING_GETDATA_REQUESTS.put(player.getUuid(), pos);
        ServerPlayNetworking.send(player, RequestBlockEntityDataPacket.ID, new RequestBlockEntityDataPacket(pos).toBuffer());
    }

    private static void handleConvertBlock(ServerPlayerEntity player, ConvertBlockPacket packet) {
        if (!ServerConfig.get().allowInventoryBlockConversion) {
            Reshaped.LOGGER.debug("Blocked convert packet from {} (disabled in server config).", player.getName().getString());
            return;
        }

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
        if (!Reshaped.MATRIX.areInSameColumn(currentBlock, targetBlock)) {
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

    private static void handleBlockEntityDataRequestFromServer(RequestBlockEntityDataPacket packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockPos pos = packet.pos();
        BlockEntity be = client.world.getBlockEntity(pos);
        if (!(be instanceof MixedBlockEntity mixedBlockEntity)) {
            ClientPlayNetworking.send(
                    ClientBlockEntityDataPacket.ID,
                    new ClientBlockEntityDataPacket(pos, false, "", "", "", "Targeted block is not a reshaped block entity on client.")
                            .toBuffer()
            );
            return;
        }

        String blockId = Registries.BLOCK.getId(be.getCachedState().getBlock()).toString();
        String blockEntityTypeId = Objects.requireNonNull(Registries.BLOCK_ENTITY_TYPE.getId(be.getType())).toString();
        String nbt = mixedBlockEntity.createNbt().toString();

        ClientPlayNetworking.send(
                ClientBlockEntityDataPacket.ID,
                new ClientBlockEntityDataPacket(pos, true, blockId, blockEntityTypeId, nbt, "")
                        .toBuffer()
        );
    }

    private static void handleClientBlockEntityData(ServerPlayerEntity player, ClientBlockEntityDataPacket packet) {
        BlockPos expectedPos = PENDING_GETDATA_REQUESTS.remove(player.getUuid());
        if (expectedPos == null) {
            Reshaped.LOGGER.debug("Received unexpected client block entity data from {}", player.getName().getString());
            return;
        }

        if (!expectedPos.equals(packet.pos())) {
            Reshaped.LOGGER.warn("Mismatched client block entity data position from {}: expected {}, got {}",
                    player.getName().getString(), expectedPos.toShortString(), packet.pos().toShortString());
            return;
        }

        player.sendMessage(Text.literal("Client data for " + packet.pos().toShortString() + ":"), false);

        if (!packet.isReshapedBlockEntity()) {
            player.sendMessage(Text.literal("Client error: " + packet.error()), false);
            return;
        }

        player.sendMessage(Text.literal("Client Block: " + packet.blockId()), false);
        player.sendMessage(Text.literal("Client BlockEntityType: " + packet.blockEntityTypeId()), false);
        player.sendMessage(Text.literal("Client NBT: " + packet.nbt()), false);
    }
}
