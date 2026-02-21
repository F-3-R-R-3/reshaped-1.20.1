package net.f3rr3.reshaped.config.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.f3rr3.reshaped.Reshaped;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("reshaped-server.json");

    private static ServerConfig INSTANCE = new ServerConfig();

    public boolean allowInventoryBlockConversion = true;
    public boolean enableMatrixCommand = true;

    private ServerConfig() {
    }

    public static ServerConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (Files.notExists(CONFIG_PATH)) {
            save();
            Reshaped.LOGGER.info("Created default server config at {}", CONFIG_PATH);
            return;
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            ServerConfig loaded = GSON.fromJson(json, ServerConfig.class);
            if (loaded != null) {
                INSTANCE = loaded;
            }
        } catch (Exception e) {
            Reshaped.LOGGER.error("Failed to load server config from {}. Using defaults.", CONFIG_PATH, e);
            INSTANCE = new ServerConfig();
            save();
        }
    }

    public static void save() {
        try {
            if (CONFIG_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            Reshaped.LOGGER.error("Failed to save server config to {}", CONFIG_PATH, e);
        }
    }
}
