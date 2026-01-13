package net.f3rr3.reshaped.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.f3rr3.reshaped.Reshaped;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates and caches pre-baked circle textures for efficient rendering.
 * Circles are rendered to textures with proper anti-aliasing and transparency.
 */
public class CircleTexture {
    
    private static final Map<String, CachedCircle> CACHE = new HashMap<>();
    
    public static class CachedCircle {
        public final Identifier textureId;
        public final int size;
        
        CachedCircle(Identifier textureId, int size) {
            this.textureId = textureId;
            this.size = size;
        }
    }
    
    /**
     * Gets or creates a cached circle texture
     * @param outerRadius outer radius of the circle in pixels
     * @param innerRadius inner radius for ring style (set to 0 for filled circle)
     * @param color RGB color (e.g., 0xFFFFFF for white)
     * @param alpha opacity (0.0 - 1.0)
     * @param outlineThickness outline thickness in pixels (0 for no outline)
     * @return cached circle texture
     */
    public static CachedCircle getOrCreateCircle(
            int outerRadius,
            int innerRadius,
            int color,
            float alpha,
            float outlineThickness
    ) {
        String key = outerRadius + "|" + innerRadius + "|" + color + "|" + 
                Math.round(alpha * 1000) + "|" + Math.round(outlineThickness * 10);
        
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }
        
        // Create texture
        int size = (outerRadius + Math.round(outlineThickness)) * 2 + 4;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        
        // Clear to transparent
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setColor(x, y, 0x00000000);
            }
        }
        
        float cx = size * 0.5f;
        float cy = size * 0.5f;
        float outer = outerRadius;
        float inner = Math.max(0, innerRadius);
        float aa = 1.5f; // anti-alias smoothness
        
        // Draw circle/ring
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f) - cx;
                float dy = (y + 0.5f) - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                
                float a = 0f;
                
                // Fill
                if (innerRadius > 0) {
                    // Ring
                    if (dist >= inner && dist <= outer) {
                        a = alpha * smoothstep(0f, aa, (outer + aa) - dist) 
                            * smoothstep(0f, aa, dist - (inner - aa));
                    }
                } else {
                    // Filled circle
                    if (dist <= outer) {
                        a = alpha * smoothstep(0f, aa, (outer + aa) - dist);
                    }
                }
                
                // Outline
                if (outlineThickness > 0) {
                    float outlineAlpha = smoothstep(outlineThickness + aa, outlineThickness - aa, Math.abs(dist - outer));
                    a = Math.max(a, outlineAlpha);
                }
                
                int alpha8bit = Math.min(255, Math.round(a * 255f));
                int argb = (alpha8bit << 24) | (color & 0xFFFFFF);
                image.setColor(x, y, argb);
            }
        }
        
        // Upload texture
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        texture.setFilter(true, false);
        Identifier textureId = new Identifier(Reshaped.MOD_ID, "circle_" + key.hashCode());
        MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
        
        CachedCircle cached = new CachedCircle(textureId, size);
        CACHE.put(key, cached);
        return cached;
    }
    
    /**
     * Smoothstep function for smooth transitions (used for anti-aliasing)
     */
    private static float smoothstep(float e0, float e1, float x) {
        float t = (x - e0) / (e1 - e0);
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
    
    /**
     * Clears the texture cache
     */
    public static void clearCache() {
        CACHE.clear();
    }
}
