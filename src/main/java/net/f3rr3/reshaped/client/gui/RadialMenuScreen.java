package net.f3rr3.reshaped.client.gui;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.ModKeybindings;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class RadialMenuScreen extends Screen {
    private static final Identifier BACKGROUND_TEXTURE = new Identifier(Reshaped.MOD_ID, "textures/gui/radial_menu_bg.png");
    private final List<Block> blocks;
    private final int slot;
    private int hoveredIndex = -1;
    private final Block currentBlock;
    private final Block baseBlock;

    public RadialMenuScreen(List<Block> blocks, int slot, Block currentBlock, Block baseBlock) {
        super(Text.literal("Radial Menu"));
        this.blocks = blocks;
        this.slot = slot;
        this.currentBlock = currentBlock;
        this.baseBlock = baseBlock;
    }

    @Override
    protected void init() {
        super.init();
    }

    private static void drawScaledItem(DrawContext context, ItemStack stack, int centerX, int centerY, float scale) {
        // draws a centered block at a scale
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, scale);

        int scaledX = Math.round((centerX / scale) - 8);
        int scaledY = Math.round((centerY / scale) - 8);

        context.drawItem(stack, scaledX, scaledY);
        context.getMatrices().pop();
    }

    @Override
    public void tick() {
        super.tick();
        
        // Check if the trigger key/button is still held
        boolean isHeld = false;
        assert this.client != null;
        long handle = this.client.getWindow().getHandle();
        InputUtil.Key boundKey = ModKeybindings.OPEN_RADIAL_MENU.getDefaultKey();
        
        if (boundKey.getCategory() == InputUtil.Type.KEYSYM) {
            isHeld = InputUtil.isKeyPressed(handle, boundKey.getCode());
        } else if (boundKey.getCategory() == InputUtil.Type.MOUSE) {
            isHeld = org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, boundKey.getCode()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }

        if (!isHeld) {
            // Key was released, select hovered and close
            selectHovered();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int radius = 80;
        
        // Draw background texture
        int bgSize = 256;
        context.drawTexture(BACKGROUND_TEXTURE, centerX - bgSize / 2, centerY - bgSize / 2, 0, 0, bgSize, bgSize, bgSize, bgSize);

        if (blocks.isEmpty()) return;

        double angleStep = (2 * Math.PI) / blocks.size();
        hoveredIndex = -1;

        // Calculate hovered index based on mouse angle
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distSq = dx * dx + dy * dy;
        
        if (distSq > 400 && distSq < 15000) { // Only if mouse is within a certain distance from center
            double mouseAngle = Math.atan2(dy, dx);
            if (mouseAngle < 0) mouseAngle += 2 * Math.PI;
            
            // Re-map angle so that the segments line up with visuals if needed
            // Currently using i * angleStep, so segment 0 is at 3 o'clock
            hoveredIndex = (int) Math.round(mouseAngle / angleStep) % blocks.size();
        }

        for (int i = 0; i < blocks.size(); i++) {
            double angle = i * angleStep;

            Block block = blocks.get(i);
            ItemStack stack = new ItemStack(block);

            int x;
            int y;
            float scale;

            if (i == hoveredIndex) {
                // draw center block at a scale
                x = centerX;
                y = centerY;
                scale = 6.0f;
                drawScaledItem(context, stack, centerX, centerY, 6.0f);
            } else {
                x = centerX + (int) (radius * Math.cos(angle));
                y = centerY + (int) (radius * Math.sin(angle));
                if (baseBlock == block) {
                    scale = 1.5f;
                } else {
                    scale = 1.0f;
                }
            }

            // Draw selection highlight
            if (block == currentBlock) {
                // Subtle highlight around the item
                context.fill(x - 12, y - 12, x + 12, y + 12, 0x40FFFFFF);
            }

            drawScaledItem(context, stack, x, y, scale);

            if (i == hoveredIndex) {
                String reason = Reshaped.MATRIX != null ? Reshaped.MATRIX.getReason(block) : "Unknown reason";
                context.drawTooltip(this.textRenderer, 
                    List.of(
                        block.getName(), 
                        Text.literal(reason).formatted(Formatting.GRAY, Formatting.ITALIC)
                    ), 
                    -8, 16);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Fallback: If user clicks while menu is open, select and close
        if (button == 0 || button == 1) {
            selectHovered();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Allow escape to close without selecting
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectHovered() {
        if (hoveredIndex != -1) {
            Block selectedBlock = blocks.get(hoveredIndex);
            NetworkHandler.sendConvertBlockPacket(Registries.BLOCK.getId(selectedBlock), slot);
        }
        this.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
