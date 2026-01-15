package net.f3rr3.reshaped.client.gui;

import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.ModKeybindings;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class RadialMenuScreen extends Screen {
    private final List<Block> blocks;
    private final int slot;
    private final Block currentBlock;
    private final Block baseBlock;
    private int hoveredIndex = -1;

    // Action to perform during the render cycle to ensure safe screen transitions
    private Runnable pendingAction;

    public RadialMenuScreen(List<Block> blocks, int slot, Block currentBlock, Block baseBlock) {
        super(Text.literal("Radial Menu"));
        this.blocks = blocks;
        this.slot = slot;
        this.currentBlock = currentBlock;
        this.baseBlock = baseBlock;
    }

    private static void drawScaledItem(DrawContext context, ItemStack stack, int centerX, int centerY, float scale) {
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, scale);

        int scaledX = Math.round((centerX / scale) - 8);
        int scaledY = Math.round((centerY / scale) - 8);

        context.drawItem(stack, scaledX, scaledY);
        context.getMatrices().pop();
    }

    private static void DrawCircleSlice(DrawContext context, int centerX, int centerY, int OuterRadius, int innerRadius, int slice, int NoOfSlices, int color) {
        float sectionWidth = 360f / NoOfSlices;
        float startAngle = sectionWidth * (slice - 0.5f);
        float endAngle = sectionWidth * (slice + 0.5f);
        CircleTexture.CachedCircle circle = CircleTexture.getOrCreateCircle(OuterRadius, innerRadius, color, 0.7f, 1f, startAngle, endAngle);

        int size = circle.size;
        int x0 = centerX - size / 2;
        int y0 = centerY - size / 2;
        context.drawTexture(circle.textureId, x0, y0, 0, 0, size, size, size, size);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void tick() {
        super.tick();

        // Check if the trigger key/button is still held
        boolean isHeld = false;
        if (this.client != null) {
            long handle = this.client.getWindow().getHandle();
            InputUtil.Key boundKey = ModKeybindings.OPEN_RADIAL_MENU.getDefaultKey();

            if (boundKey.getCategory() == InputUtil.Type.KEYSYM) {
                isHeld = InputUtil.isKeyPressed(handle, boundKey.getCode());
            } else if (boundKey.getCategory() == InputUtil.Type.MOUSE) {
                isHeld = org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, boundKey.getCode()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
        }

        if (!isHeld) {
            // Key was released, select hovered and close
            selectHovered();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int radius = 80;

        if (blocks.isEmpty()) return;

        double angleStep = (2 * Math.PI) / blocks.size();
        hoveredIndex = -1;

        // Calculate hovered index based on mouse angle
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distSq = dx * dx + dy * dy;

        if (distSq > 400 && distSq < 15000) {
            double mouseAngle = Math.atan2(dy, dx);
            if (mouseAngle < 0) mouseAngle += 2 * Math.PI;

            hoveredIndex = (int) Math.round(mouseAngle / angleStep) % blocks.size();
        }

        int innerDiam = radius - 10;
        int outerDiam = radius + 10;
        for (int i = 0; i < blocks.size(); i++) {
            double angle = i * angleStep;

            Block block = blocks.get(i);
            ItemStack stack = new ItemStack(block);

            if (i == hoveredIndex) {
                drawScaledItem(context, stack, centerX, centerY, 6.0f);
            } else if (block == currentBlock && hoveredIndex == -1) {
                drawScaledItem(context, stack, centerX, centerY, 6.0f);
            }

            int x = centerX + (int) (radius * Math.cos(angle));
            int y = centerY + (int) (radius * Math.sin(angle));
            float scale;

            if (baseBlock == block) {
                scale = 1.5f;
            } else {
                scale = 1.0f;
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
            } else {
                DrawCircleSlice(context, centerX, centerY, outerDiam, innerDiam, i, blocks.size(), 0x7F333333);
            }
        }

        if (hoveredIndex != -1) {
            DrawCircleSlice(context, centerX, centerY, outerDiam + 6, innerDiam - 4, hoveredIndex, blocks.size(), 0x40666666);
        }

        // Debug rendering
        if (isCtrlPressed()) {
            renderDebugInfo(context, centerX, centerY, radius);
        }

        super.render(context, mouseX, mouseY, delta);

        if (this.pendingAction != null) {
            this.pendingAction.run();
            this.pendingAction = null;
        }
    }

    private boolean isCtrlPressed() {
        if (this.client == null) return false;
        long handle = this.client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private void renderDebugInfo(DrawContext context, int centerX, int centerY, int radius) {
        String debugText = "Radial: Hover " + hoveredIndex + " | Items: " + blocks.size();
        int textX = centerX - 150;
        int textY = centerY + radius + 20;
        context.fill(textX - 2, textY - 2, textX + 150, textY + 12, 0xFF000000);
        context.drawText(this.textRenderer, debugText, textX, textY, 0xFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            selectHovered();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.client != null && this.client.player != null) {
            int i = this.client.player.getInventory().selectedSlot;
            int j = (int) Math.signum(amount);
            int newSlot = i - j;
            while (newSlot < 0) {
                newSlot += 9;
            }
            while (newSlot >= 9) {
                newSlot -= 9;
            }
            this.changeSlot(newSlot);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void changeSlot(int newSlot) {
        if (this.client != null && this.client.player != null) {
            this.client.player.getInventory().selectedSlot = newSlot;

            // Defer the screen change to the render loop to avoid crashing if called from an input event
            this.pendingAction = () -> {
                ItemStack stack = this.client.player.getMainHandStack();
                if (stack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    if (Reshaped.MATRIX != null && Reshaped.MATRIX.hasBlock(block)) {
                        List<Block> column = Reshaped.MATRIX.getColumn(block);
                        if (!column.isEmpty()) {
                            Block baseBlock = Reshaped.MATRIX.getBaseBlock(block);
                            this.client.setScreen(new RadialMenuScreen(column, newSlot, block, baseBlock));
                            return;
                        }
                    }
                }
                this.close();
            };
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }

        if (this.client != null && this.client.options != null) {
            for (int i = 0; i < 9; i++) {
                if (this.client.options.hotbarKeys[i].matchesKey(keyCode, scanCode)) {
                    this.changeSlot(i);
                    return true;
                }
            }
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
