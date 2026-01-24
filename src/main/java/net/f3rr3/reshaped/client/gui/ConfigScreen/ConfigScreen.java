package net.f3rr3.reshaped.client.gui.ConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {

    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Configuration screen"));
        this.parent = parent;
    }
    @Override
    protected void init() {
        // Voeg een "Terug"-knop toe in het midden onderaan
        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.literal("exit"),
                        button -> this.close() // sluit scherm en gaat terug naar parent
                ).dimensions(
                        this.width / 2 - 150, // x-position
                        this.height - 40,    // y-position
                        100,                 // breedte
                        20                   // hoogte
                ).build()
        );
        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.literal("Save and exit"),
                        button -> this.close() // sluit scherm en gaat terug naar parent
                ).dimensions(
                        this.width / 2 + 50, // x-position
                        this.height - 40,    // y-position
                        100,                 // breedte
                        20                   // hoogte
                ).build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Teken de standaard Minecraft achtergrond
        this.renderBackground(context);

        // Teken de title bovenaan
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Teken de knoppen en andere children
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
