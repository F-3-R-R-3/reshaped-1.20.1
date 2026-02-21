package net.f3rr3.reshaped.network;

import net.f3rr3.reshaped.Reshaped;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record ClientBlockEntityDataPacket(
        BlockPos pos,
        boolean isReshapedBlockEntity,
        String blockId,
        String blockEntityTypeId,
        String nbt,
        String error
) {
    public static final Identifier ID = new Identifier(Reshaped.MOD_ID, "client_block_entity_data");

    public PacketByteBuf toBuffer() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(isReshapedBlockEntity);
        buf.writeString(blockId);
        buf.writeString(blockEntityTypeId);
        buf.writeString(nbt);
        buf.writeString(error);
        return buf;
    }

    public static ClientBlockEntityDataPacket fromBuffer(PacketByteBuf buf) {
        return new ClientBlockEntityDataPacket(
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readString()
        );
    }
}
