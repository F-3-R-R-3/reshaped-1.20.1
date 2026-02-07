package net.f3rr3.reshaped.block.Template;

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

/**
 * Base class for block entities that store material identifiers for mixed blocks.
 * Consolidates common NBT serialization and network sync logic.
 */
public abstract class MixedBlockEntity extends BlockEntity {
    private final Identifier[] materials;

    protected MixedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int materialCount) {
        super(type, pos, state);
        this.materials = new Identifier[materialCount];
    }

    public void setMaterial(int index, Identifier materialId) {
        if (index >= 0 && index < materials.length) {
            materials[index] = materialId;
            markDirty();
            if (world != null && !world.isClient) {
                world.updateListeners(pos, getCachedState(), getCachedState(), 3);
            }
        }
    }

    public Identifier getMaterial(int index) {
        if (index >= 0 && index < materials.length) {
            return materials[index];
        }
        return null;
    }

    public int getMaterialCount() {
        return materials.length;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("Materials", 9)) {
            NbtList list = nbt.getList("Materials", 8);
            for (int i = 0; i < Math.min(list.size(), materials.length); i++) {
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
        for (Identifier material : materials) {
            list.add(NbtString.of(material == null ? "" : material.toString()));
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
