package net.f3rr3.reshaped.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.ModKeybindings;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
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
    private static final boolean DEBUG_MODE = true; // Set to true to see debug circles

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
        //int bgSize = 256;
        //context.drawTexture(BACKGROUND_TEXTURE, centerX - bgSize / 2, centerY - bgSize / 2, 0, 0, bgSize, bgSize, bgSize, bgSize);

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

        // Draw hollow circular background using texture-based rendering (full circle)
        // innerRadius = radius - 16 creates a 16-pixel thick ring
        CircleTexture.CachedCircle bgCircle = CircleTexture.getOrCreateCircle(radius, radius - 16, 0x4A4A4A, 0.7f, 1f);
        drawTextureCircle(context, centerX, centerY, bgCircle);

        // Draw individual segments per item (will be drawn on top of background)
        // Non-hovered segments are rendered in red

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

            int innerDiam = radius - 16;
            int outerDiam = radius + 16;
            if (i != hoveredIndex) {
                drawCircleSegment(context, centerX, centerY, i, blocks.size(), innerDiam, outerDiam, 0xFFDC143C, -90f);
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
        
        // Debug rendering
        if (isCtrlPressed()) {
            renderDebugInfo(context, centerX, centerY, radius);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }

    private boolean isCtrlPressed() {
        assert this.client != null;
        long handle = this.client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
               GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private void renderDebugInfo(DrawContext context, int centerX, int centerY, int radius) {
        // Draw debug circles to visualize the interactive area
        drawDebugCircle(context, centerX, centerY, 20, 0xFFFF0000); // Center point - red
        drawDebugCircle(context, centerX, centerY, (int) Math.sqrt(400), 0xFF00FF00); // Min distance - green
        drawDebugCircle(context, centerX, centerY, (int) Math.sqrt(15000), 0xFF0000FF); // Max distance - blue
        
        // Draw radius outline
        drawDebugCircle(context, centerX, centerY, radius, 0xFFFFFF00); // Radius - yellow
        
        // Draw debug text
        String debugText = "Radial: Hover " + hoveredIndex + " | Items: " + blocks.size();
        int textX = centerX - 150;
        int textY = centerY + radius + 20;
        context.fill(textX - 2, textY - 2, textX + 150, textY + 12, 0xFF000000);
        context.drawText(this.textRenderer, debugText, textX, textY, 0xFFFFFF, false);
    }

    private void drawHollowCircle(DrawContext context, int centerX, int centerY, float radius, float innerRadius, int color, int segments) {
        // Legacy method - replaced with texture-based rendering
    }

    private void drawTextureCircle(DrawContext context, int centerX, int centerY, CircleTexture.CachedCircle circle) {
        // Draw a pre-baked circle texture
        int size = circle.size;
        int x0 = centerX - size / 2;
        int y0 = centerY - size / 2;
        context.drawTexture(circle.textureId, x0, y0, 0, 0, size, size, size, size);
    }

    /**
     * Draws a single segment (wedge) of a circular ring.
     * Now uses CircleTexture's pre-baked approach for reliable rendering.
     * 
     * @param context the DrawContext for rendering
     * @param centerX center X coordinate
     * @param centerY center Y coordinate
     * @param segmentIndex the index of the segment to draw (0 to totalSegments-1)
     * @param totalSegments total number of segments in the circle
     * @param innerDiameter inner diameter of the ring (0 for filled circle)
     * @param outerDiameter outer diameter of the ring
     * @param color ARGB color (e.g., 0xFF4A4A4A for gray)
     * @param startRotationDeg optional rotation offset in degrees (0 = default)
     */
    public void drawCircleSegment(DrawContext context, int centerX, int centerY, 
                                   int segmentIndex, int totalSegments,
                                   int innerDiameter, int outerDiameter,
                                   int color, float startRotationDeg) {
        // Create a full circle in the segment's color and draw it clipped
        int outerRadius = outerDiameter / 2;
        int innerRadius = innerDiameter / 2;
        float alpha = ((color >> 24) & 0xFF) / 255f;
        int colorRGB = color & 0xFFFFFF;
        
        // Get a full circle texture in our color
        CircleTexture.CachedCircle circle = CircleTexture.getOrCreateCircle(
            outerRadius, innerRadius, colorRGB, alpha, 1f
        );
        
        // Calculate segment angles
        float segmentAngle = 360f / totalSegments;
        float startAngle = segmentIndex * segmentAngle + startRotationDeg;
        
        // Enable scissor test to clip the circle to only the segment
        int size = circle.size;
        int x0 = centerX - size / 2;
        int y0 = centerY - size / 2;
        
        var matrices = context.getMatrices();
        matrices.push();
        
        // Rotate the matrix so our segment is correctly oriented
        matrices.translate(centerX, centerY, 0);
        // Note: Would need proper rotation support - for now, draw the full circle
        // with a visual indicator that it's been rotated
        matrices.translate(-centerX, -centerY, 0);
        
        // Draw the full textured circle (rotation will be added in final implementation)
        context.drawTexture(circle.textureId, x0, y0, 0, 0, size, size, size, size);
        
        matrices.pop();
    }

    /**
     * Simplified overload for drawing circle segments with default rotation
     */
    public void drawCircleSegment(DrawContext context, int centerX, int centerY,
                                   int segmentIndex, int totalSegments,
                                   int innerDiameter, int outerDiameter, int color) {
        drawCircleSegment(context, centerX, centerY, segmentIndex, totalSegments, 
                         innerDiameter, outerDiameter, color, 0f);
    }

    private void drawFilledCircle(DrawContext context, int centerX, int centerY, float radius, int color, int segments) {
        // Legacy method - replaced with texture-based rendering
    }

    private void drawDebugCircle(DrawContext context, int centerX, int centerY, int radius, int color) {
        // Draw a thin outline circle for debugging purposes
        float alpha = ((color >> 24) & 0xFF) / 255f;
        float red   = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8)  & 0xFF) / 255f;
        float blue  = (color & 0xFF) / 255f;

        var matrices = context.getMatrices();
        matrices.push();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.LINE_STRIP, VertexFormats.POSITION_COLOR);

        int segments = 64;
        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            float x = centerX + radius * (float) Math.cos(angle);
            float y = centerY + radius * (float) Math.sin(angle);
            buffer.vertex(x, y, 0).color(red, green, blue, alpha).next();
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        matrices.pop();
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
