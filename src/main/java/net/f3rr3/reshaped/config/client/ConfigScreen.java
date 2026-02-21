package net.f3rr3.reshaped.config.client;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.screen.Screen;

public class ConfigScreen {

    public static Screen create(Screen parent) {
        return AutoConfig.getConfigScreen(ModConfig.class, parent).get();
    }
}
