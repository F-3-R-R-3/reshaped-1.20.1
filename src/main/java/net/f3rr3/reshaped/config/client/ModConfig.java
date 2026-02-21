package net.f3rr3.reshaped.config.client;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "reshaped")
public class ModConfig implements ConfigData {


    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public RadialMenu radial = new RadialMenu();

    @ConfigEntry.Gui.CollapsibleObject(startExpanded = true)
    public ItemBadges ItemBadges = new ItemBadges();

    public boolean enableTooltip = true;

    public boolean enableDevMode = false;

    public boolean hideAlternateBlocks = true;

    public static class RadialMenu {
        @ConfigEntry.BoundedDiscrete(min = 1, max = 16)
        public float ImageResolution = 4.0f;

        @ConfigEntry.ColorPicker(allowAlpha = true)
        public int ColorSelectedSlice = 0x40666666;

        @ConfigEntry.ColorPicker(allowAlpha = true)
        public int ColorUnselectedSlice = 0x7F333333;
    }

    public static class ItemBadges {
        public boolean enableRadial = true;

        public boolean enableWaxed = true;
    }
}
