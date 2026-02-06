package net.f3rr3.reshaped.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongIterator;

public final class MixedBlockParticleTracker {
    private static final long TTL_TICKS = 10L;
    private static final Long2ObjectOpenHashMap<Entry> OVERRIDES = new Long2ObjectOpenHashMap<>();

    private MixedBlockParticleTracker() {
    }

    public static void captureFromCrosshair(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.crosshairTarget == null) {
            return;
        }

        if (client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult hit = (BlockHitResult) client.crosshairTarget;
        if (!hit.getBlockPos().equals(pos)) {
            return;
        }

        Identifier materialId = MixedBlockParticleUtils.resolveMaterialForHit(
                client.world,
                pos,
                client.world.getBlockState(pos),
                hit
        );

        if (materialId != null) {
            setOverride(pos, materialId, client.world.getTime());
        }
    }

    public static Identifier getOverride(BlockRenderView view, BlockPos pos) {
        long time = 0L;
        if (view instanceof World world) {
            time = world.getTime();
        }
        return getOverride(pos, time);
    }

    public static void tick(World world) {
        long time = world.getTime();
        if (OVERRIDES.isEmpty()) {
            return;
        }

        LongIterator iterator = OVERRIDES.keySet().iterator();
        while (iterator.hasNext()) {
            long key = iterator.nextLong();
            Entry entry = OVERRIDES.get(key);
            if (entry == null || time - entry.time > TTL_TICKS) {
                iterator.remove();
            }
        }
    }

    private static Identifier getOverride(BlockPos pos, long time) {
        long key = pos.asLong();
        Entry entry = OVERRIDES.get(key);
        if (entry == null) {
            return null;
        }

        if (time != 0L && time - entry.time > TTL_TICKS) {
            OVERRIDES.remove(key);
            return null;
        }

        return entry.materialId;
    }

    private static void setOverride(BlockPos pos, Identifier materialId, long time) {
        OVERRIDES.put(pos.asLong(), new Entry(materialId, time));
    }

    private record Entry(Identifier materialId, long time) {
    }
}
