package net.f3rr3.reshaped.network;

import net.f3rr3.reshaped.Reshaped;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record ConvertBlockPacket(Identifier targetBlockId, int slot) {
    public static final Identifier ID = new Identifier(Reshaped.MOD_ID, "convert_block");

    public PacketByteBuf toBuffer() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeIdentifier(targetBlockId);
        buf.writeInt(slot);
        return buf;
    }

    public static ConvertBlockPacket fromBuffer(PacketByteBuf buf) {
        Identifier targetBlockId = buf.readIdentifier();
        int slot = buf.readInt();
        return new ConvertBlockPacket(targetBlockId, slot);
    }
}
