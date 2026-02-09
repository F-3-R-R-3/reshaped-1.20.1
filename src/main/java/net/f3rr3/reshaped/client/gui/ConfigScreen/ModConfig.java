package net.f3rr3.reshaped.client.gui.ConfigScreen;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "reshaped")
public class ModConfig implements ConfigData {

    public boolean enableDevMode = false;
    public boolean enableRadialItemBadges = true;
    public boolean enableWaxedItemBadges = true;
    public int radialResolution = 5;
}