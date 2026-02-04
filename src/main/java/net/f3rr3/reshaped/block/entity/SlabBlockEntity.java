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

public class SlabBlockEntity extends BlockEntity {
    public static BlockEntityType<SlabBlockEntity> TYPE;

    // Order: BOTTOM, TOP
    private final Identifier[] materials = new Identifier[2];

    public SlabBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    public void setMaterial(int index, Identifier materialId) {
        if (index >= 0 && index < 2) {
            materials[index] = materialId;
            markDirty();
            if (world != null && !world.isClient) {
                world.updateListeners(pos, getCachedState(), getCachedState(), 3);
            }
        }
    }

    public Identifier getMaterial(int index) {
        if (index >= 0 && index < 2) {
            return materials[index];
        }
        return null;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("Materials", 9)) {
            NbtList list = nbt.getList("Materials", 8);
            for (int i = 0; i < Math.min(list.size(), 2); i++) {
                String s = list.getString(i);
                materials[i] = s.isEmpty() ? null : new Identifier(s);
            }
        }
        
        if (world != null && world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtList list = new NbtList();
        for (int i = 0; i < 2; i++) {
            list.add(NbtString.of(materials[i] == null ? "" : materials[i].toString()));
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
}
