package net.f3rr3.reshaped.network;

import net.f3rr3.reshaped.Reshaped;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record RequestBlockEntityDataPacket(BlockPos pos) {
    public static final Identifier ID = new Identifier(Reshaped.MOD_ID, "request_block_entity_data");

    public PacketByteBuf toBuffer() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        return buf;
    }

    public static RequestBlockEntityDataPacket fromBuffer(PacketByteBuf buf) {
        return new RequestBlockEntityDataPacket(buf.readBlockPos());
    }
}
