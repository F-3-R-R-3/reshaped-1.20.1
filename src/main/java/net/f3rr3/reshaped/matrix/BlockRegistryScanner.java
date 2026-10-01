package net.f3rr3.reshaped.matrix;

import net.f3rr3.reshaped.Reshaped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class BlockRegistryScanner {
    public static void init(BlockMatrix matrix) {
        Reshaped.LOGGER.info("[BlockRegistryScanner] init() called on thread: {}", Thread.currentThread().getName());

        // Registry writes are now centralized in a pre-freeze mixin pass.
        // Server lifecycle rebuilds remain for recipe-based enrichment and
        // datapack reload handling after startup.

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
    }
}
