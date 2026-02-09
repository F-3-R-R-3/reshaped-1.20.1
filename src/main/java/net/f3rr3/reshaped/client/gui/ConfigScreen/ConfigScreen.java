package net.f3rr3.reshaped.client.gui.ConfigScreen;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ConfigScreen {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("ReShaped Config"));

        builder.setSavingRunnable(ModConfig::save);

        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        general.addEntry(
                entryBuilder.startBooleanToggle(
                                Text.literal("Enable developer info"),
                                ModConfig.enableDevMode
                        )
                        .setDefaultValue(false)
                        .setTooltip(Text.literal("Only recommended for mod developers"))
                        .setSaveConsumer(value -> ModConfig.enableDevMode = value)
                        .build()
        );
        general.addEntry(
                entryBuilder.startBooleanToggle(
                                Text.literal("Enable Radial Item Badges"),
                                ModConfig.enableRadialItemBadges
                        )
                        .setDefaultValue(true)
                        .setTooltip(Text.literal("renders an icon on items with a radial menu"))
                        .setSaveConsumer(value -> ModConfig.enableRadialItemBadges = value)
                        .build()
        );
        general.addEntry(
                entryBuilder.startBooleanToggle(
                                Text.literal("Enable waxed item badges"),
                                ModConfig.enableWaxedItemBadges
                        )
                        .setDefaultValue(true)
                        .setTooltip(Text.literal("renders a wax icon on items waxed blocks"))
                        .setSaveConsumer(value -> ModConfig.enableWaxedItemBadges = value)
                        .build()
        );
        return builder.build();
    }
}
