package net.f3rr3.reshaped.util;

import net.f3rr3.reshaped.Reshaped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

public class BlockRegistryScanner {
    public static void init(BlockMatrix matrix) {
        Reshaped.LOGGER.info("[BlockRegistryScanner] init() called on thread: {}", Thread.currentThread().getName());
        
        // We no longer bootstrap unconditionally in init(), as mod load order
        // is non-deterministic. Instead, we register triggers for late-lifecycle
        // points where all mods have likely finished registering their blocks.

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Reshaped.LOGGER.info("[BlockRegistryScanner] SERVER_STARTING triggered. Ensuring matrix is bootstrapped...");
            MatrixRebuilder.bootstrap(matrix, true);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Reshaped.LOGGER.info("[BlockRegistryScanner] SERVER_STARTED triggered on thread: {}", Thread.currentThread().getName());
            MatrixRebuilder.rebuild(matrix, server);
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            Reshaped.LOGGER.info("[BlockRegistryScanner] END_DATA_PACK_RELOAD triggered (success={}) on thread: {}", 
                    success, Thread.currentThread().getName());
            if (success) {
                MatrixRebuilder.rebuild(matrix, server);
            }
        });

        RegistryEntryAddedCallback.event(Registries.BLOCK).register((rawId, id, block) -> {
            if (MatrixRebuilder.isSuppressed()) return;
            MatrixRebuilder.bootstrapAddedBlock(matrix, block);
        });

        RegistryEntryAddedCallback.event(Registries.ITEM).register((rawId, id, item) -> {
            if (MatrixRebuilder.isSuppressed()) return;
            
            // Check if this item registration completes a modded block candidate.
            // Many mods register Block then Item; we need both for BaseBlockFilter to pass.
            Block block = Registries.BLOCK.get(id);
            if (block != net.minecraft.block.Blocks.AIR) {
                MatrixRebuilder.bootstrapAddedBlock(matrix, block);
            }
        });
    }
}
