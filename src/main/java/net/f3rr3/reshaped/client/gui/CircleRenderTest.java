package net.f3rr3.reshaped.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;

/**
 * Test utility class for verifying circle rendering functionality.
 * This can be used for debugging circle rendering issues in the RadialMenuScreen.
 * 
 * Usage: Create a test circle on screen at known coordinates and verify visibility.
 */
public class CircleRenderTest {

    /**
     * Tests basic circle rendering with visual output.
     * Draws multiple circles to verify:
     * - Different segment counts (16, 32, 64)
     * - Different radii (20, 50, 100)
     * - Different colors with alpha blending
     * 
     * @param context The DrawContext for rendering
     * @param centerX Center X coordinate on screen
     * @param centerY Center Y coordinate on screen
     */
    public static void testCircleRendering(DrawContext context, int centerX, int centerY) {
        // Test 1: Different segment counts - small circles in a row
        drawTestCircle(context, centerX - 150, centerY - 150, 20, 16, 0xFF0000FF); // 16 segments - blue
        drawTestCircle(context, centerX, centerY - 150, 20, 32, 0xFF00FF00); // 32 segments - green
        drawTestCircle(context, centerX + 150, centerY - 150, 20, 64, 0xFFFF0000); // 64 segments - red

        // Test 2: Different radii - increasing size
        drawTestCircle(context, centerX - 150, centerY, 30, 32, 0xFFFFFF00); // yellow - medium
        drawTestCircle(context, centerX, centerY, 60, 32, 0xFFFF00FF); // magenta - large
        drawTestCircle(context, centerX + 150, centerY, 80, 32, 0xFF00FFFF); // cyan - very large

        // Test 3: Semi-transparent circles
        drawTestCircle(context, centerX - 150, centerY + 150, 40, 32, 0x80FF0000); // semi-transparent red
        drawTestCircle(context, centerX, centerY + 150, 40, 32, 0x8000FF00); // semi-transparent green
        drawTestCircle(context, centerX + 150, centerY + 150, 40, 32, 0x800000FF); // semi-transparent blue
    }

    /**
     * Draws a single test circle and validates its parameters.
     * 
     * @param context The DrawContext
     * @param centerX Center X coordinate
     * @param centerY Center Y coordinate
     * @param radius Circle radius
     * @param segments Number of segments
     * @param color ARGB color value
     */
    private static void drawTestCircle(DrawContext context, int centerX, int centerY, float radius, int segments, int color) {
        // Validate inputs
        if (radius <= 0) {
            throw new IllegalArgumentException("Radius must be positive, got: " + radius);
        }
        if (segments < 3) {
            throw new IllegalArgumentException("Segments must be at least 3, got: " + segments);
        }

        // Extract RGBA components
        float alpha = ((color >> 24) & 0xFF) / 255f;
        float red   = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8)  & 0xFF) / 255f;
        float blue  = (color & 0xFF) / 255f;

        // Save render state
        var matrices = context.getMatrices();
        matrices.push();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // Get the buffer and begin rendering
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);

        // Center vertex
        buffer.vertex(centerX, centerY, 0).color(red, green, blue, alpha).next();

        // Outer ring vertices
        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            float x = centerX + radius * (float) Math.cos(angle);
            float y = centerY + radius * (float) Math.sin(angle);
            buffer.vertex(x, y, 0).color(red, green, blue, alpha).next();
        }

        // Draw the circle
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        // Restore render state
        RenderSystem.enableDepthTest();
        matrices.pop();
    }

    /**
     * Validates circle rendering parameters and return debug info.
     * 
     * @param radius Circle radius
     * @param segments Number of segments
     * @param color ARGB color
     * @return Debug string with parameter information
     */
    public static String validateCircleParameters(float radius, int segments, int color) {
        StringBuilder sb = new StringBuilder();
        sb.append("Circle Parameters:\n");
        sb.append("  Radius: ").append(radius).append("\n");
        sb.append("  Segments: ").append(segments).append("\n");
        
        float alpha = ((color >> 24) & 0xFF) / 255f;
        float red   = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8)  & 0xFF) / 255f;
        float blue  = (color & 0xFF) / 255f;
        
        sb.append("  Color RGBA: [").append(red).append(", ").append(green)
          .append(", ").append(blue).append(", ").append(alpha).append("]\n");
        sb.append("  Circumference: ").append(2 * Math.PI * radius).append("\n");
        sb.append("  Valid: ").append(radius > 0 && segments >= 3).append("\n");
        
        return sb.toString();
    }

    /**
     * Tests edge cases and error conditions.
     */
    public static void testEdgeCases() {
        // Test with minimum valid values
        validateCircleParameters(1.0f, 3, 0xFF000000);
        
        // Test with large values
        validateCircleParameters(1000.0f, 256, 0xFFFFFFFF);
        
        // Test with fully transparent color
        validateCircleParameters(50.0f, 32, 0x00FF0000);
        
        // Test with fully opaque color
        validateCircleParameters(50.0f, 32, 0xFFFF0000);
    }
}
