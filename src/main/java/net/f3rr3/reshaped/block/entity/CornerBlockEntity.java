package net.f3rr3.reshaped.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class CornerBlockEntity extends BlockEntity {
    public static BlockEntityType<CornerBlockEntity> TYPE;

    // Stores the base block ID for each of the 8 corners.
    // Order matches the properties: DOWN_NW, DOWN_NE, DOWN_SW, DOWN_SE, UP_NW, UP_NE, UP_SW, UP_SE
    private final Identifier[] cornerMaterials = new Identifier[8];

    public CornerBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public void setCornerMaterial(int index, Identifier materialId) {
        if (index >= 0 && index < 8) {
            cornerMaterials[index] = materialId;
            markDirty();
            if (world != null && !world.isClient) {
                world.updateListeners(pos, getCachedState(), getCachedState(), 3);
            }
        }
    }

    public Identifier getCornerMaterial(int index) {
        if (index >= 0 && index < 8) {
            return cornerMaterials[index];
        }
        return null;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("Materials", 9)) {
            NbtList list = nbt.getList("Materials", 8);
            for (int i = 0; i < Math.min(list.size(), 8); i++) {
                String s = list.getString(i);
                cornerMaterials[i] = s.isEmpty() ? null : new Identifier(s);
            }
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtList list = new NbtList();
        for (int i = 0; i < 8; i++) {
            list.add(NbtString.of(cornerMaterials[i] == null ? "" : cornerMaterials[i].toString()));
        }
        nbt.put("Materials", list);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    public static void register() {
         // This will be called once all blocks are registered to ensure the type includes all corner blocks
    }
}
