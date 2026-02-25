package net.f3rr3.reshaped.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class BlockRegistryScanner {
    private static volatile MinecraftServer currentServer;

    public static void init(BlockMatrix matrix) {
        // Bootstrap immediately while registries are still writable.
        MatrixRebuilder.bootstrap(matrix);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
            MatrixRebuilder.rebuild(matrix, server);
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                currentServer = server;
                MatrixRebuilder.rebuild(matrix, server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
    }
}
