package net.f3rr3.reshaped.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.f3rr3.reshaped.Reshaped;
import net.f3rr3.reshaped.client.ModKeybindings;
import net.f3rr3.reshaped.network.NetworkHandler;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class RadialMenuScreen extends Screen {
    private static final Identifier HOTBAR_TEXTURE = new Identifier("minecraft", "textures/gui/widgets.png");
    private static final float BACKGROUND_RESOLUTION_MULTIPLIER = 4.0f; // Increase for smoother circles, decrease for performance
    private static final int RADIAL_RADIUS = 80;
    private static final int RADIAL_RING_THICKNESS = 20;
    private static final int MIN_HOVER_DIST_SQ = 400;   // (radius 20)
    private static final int MAX_HOVER_DIST_SQ = 15000; // (radius ~122.5)

    // Data
    private final List<Block> blocks;
    private final int slot;
    private final Block currentBlock;
    private final Block baseBlock;

    // Animation & Selection State
    private int hoveredIndex = -1;
    private float centerBlockAngle = 0f; // in radians
    private float angularSpeed = 0.0f; // angular speed in rad/s
    private float lastRelativeAngle = 0f;
    private float relativeAngle = 0f;

    // Transient State
    private Runnable pendingAction;
    private int selectedSlotInSortedHotbar;

    public RadialMenuScreen(List<Block> blocks, int slot, Block currentBlock, Block baseBlock) {
        super(Text.literal("Radial Menu"));
        this.blocks = blocks;
        this.slot = slot;
        this.currentBlock = currentBlock;
        this.baseBlock = baseBlock;
    }

    private static void drawScaledItem(DrawContext context, ItemStack stack, int centerX, int centerY, float scale) {
        var matrices = context.getMatrices();
        matrices.push();
        matrices.scale(scale, scale, scale);

        int scaledX = Math.round((centerX / scale) - 8);
        int scaledY = Math.round((centerY / scale) - 8);

        context.drawItem(stack, scaledX, scaledY);
        matrices.pop();
    }

    /**
     * Renders an item with custom rotation and top-left lighting.
     * Used for the central rotating item display in the radial menu.
     */
    public static void drawRotatedItem(DrawContext context, ItemStack stack, int x, int y, float angleRad, float scale) {
        var matrices = context.getMatrices();
        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, null, null, 0);
        boolean is3D = model.hasDepth();

        // Adjust scale for 2D items to prevent them from looking too large
        if (!is3D) {
            scale *= 0.6f;
        }

        matrices.push();

        // Set static shader lights for top-left lighting direction.
        // These values are derived from Minecraft source code but adjusted for UI space.
        Vector3f lightDir1 = new Vector3f(0.2F, -1.0F, -0.7F).normalize();
        Vector3f lightDir2 = new Vector3f(-0.2F, -1.0F, 0.7F).normalize();
        RenderSystem.setShaderLights(lightDir1, lightDir2);

        // Position the item and scale it
        // -100 depth ensures it renders above the radial slices
        matrices.translate(x, y - 16, -100);
        // Flip Y axis: UI coordinates (top-down) -> render coordinates (bottom-up)
        matrices.scale(scale * 16f, scale * -16f, scale * 16f);

        // Apply animations: tilt and rotation
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(angleRad));

        // Use immediate vertex consumer to ensure custom lighting is applied immediately
        VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        itemRenderer.renderItem(
                stack,
                ModelTransformationMode.FIXED,
                0xF000F0,                    // Full light
                OverlayTexture.DEFAULT_UV,
                matrices,
                immediate,
                MinecraftClient.getInstance().world,
                0
        );

        // Draw the buffered content
        immediate.draw();

        matrices.pop();

        // Restore default GUI lighting
        DiffuseLighting.enableGuiDepthLighting();
    }

    private static void drawCircleSlice(DrawContext context, int centerX, int centerY, int outerRadius, int innerRadius, int slice, int NoOfSlices, int color) {
        resetRender();
        float sectionWidth = 360f / NoOfSlices;
        float startAngle = sectionWidth * (slice - 0.5f);
        float endAngle = sectionWidth * (slice + 0.5f);

        // Scale radii for higher resolution texture generation
        int resOuter = Math.round(outerRadius * BACKGROUND_RESOLUTION_MULTIPLIER);
        int resInner = Math.round(innerRadius * BACKGROUND_RESOLUTION_MULTIPLIER);

        CircleTexture.CachedCircle circle = CircleTexture.getOrCreateCircle(resOuter, resInner, color, 0.7f, BACKGROUND_RESOLUTION_MULTIPLIER, startAngle, endAngle);

        // Determine display size by scaling back down
        int displaySize = Math.round((float) circle.size / BACKGROUND_RESOLUTION_MULTIPLIER);
        int x0 = centerX - displaySize / 2;
        int y0 = centerY - displaySize / 2;

        // Draw the high-res texture scaled down to the original UI dimensions.
        // Parameters: texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight
        context.drawTexture(
                circle.textureId,
                x0, y0,
                displaySize, displaySize,
                0, 0,
                circle.size, circle.size,
                circle.size, circle.size
        );
    }

    private static float getRelativeAngleToMouse(int xOrigin, int yOrigin, int xTarget, int yTarget) {
        float yDiff = yTarget - yOrigin;
        float xDiff = xTarget - xOrigin;
        return (float) Math.atan2(yDiff, xDiff);
    }

    public static boolean isOpen() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen instanceof RadialMenuScreen;
    }

    /**
     * Draws the vanilla hotbar background texture for a specific slot.
     *
     * @param slot The slot index (1-9). Use -1 for the selection highlight.
     */
    private static void drawHotbarTexture(DrawContext context, int slot, int x, int y) {
        // Handle hotbar selector (selection highlight)
        if (slot == -1) {
            context.drawTexture(HOTBAR_TEXTURE, x - 2, y - 1, 0, 22, 24, 22);
            return;
        }

        // Calculate texture coordinates for normal hotbar slots
        int u = (slot == 1) ? 0 : ((slot - 1) * 20 + 1);
        int v = 0;
        int tileWidth = (slot == 1 || slot == 9) ? 21 : 20;
        int tileHeight = 22;

        if (slot == 1) x -= 1;
        context.drawTexture(HOTBAR_TEXTURE, x, y, u, v, tileWidth, tileHeight);
    }

    private static void resetRender() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram); // GUI shader
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void tick() {
        super.tick();

        // Calculate angular velocity based on mouse movement relative to the center
        float delta = relativeAngle - lastRelativeAngle;

        // Normalize delta to [-PI, PI] to handle wrap-around cases
        if (delta > Math.PI) {
            delta -= (float) (2 * Math.PI);
        } else if (delta < -Math.PI) {
            delta += (float) (2 * Math.PI);
        }

        angularSpeed += delta;
        lastRelativeAngle = relativeAngle;

        // Animation settings
        float targetAngularSpeed = 0.5f; // Constant rotation speed (rad/s)
        float springStrength = 2.0f;    // Speed at which it reaches the target velocity
        float dt = 0.05f;               // Fixed time step (50 ms for tick)

        // Apply "physics" to the rotation (simple proportional control to reach target speed)
        float torque = springStrength * (targetAngularSpeed - angularSpeed);
        angularSpeed += torque * dt;

        // Update the actual rotation angle
        centerBlockAngle += angularSpeed * dt;

        // Check if the trigger key/button is still held
        boolean isHeld = false;
        if (this.client != null) {
            long handle = this.client.getWindow().getHandle();
            InputUtil.Key boundKey = ModKeybindings.OPEN_RADIAL_MENU.getDefaultKey();

            if (boundKey.getCategory() == InputUtil.Type.KEYSYM) {
                isHeld = InputUtil.isKeyPressed(handle, boundKey.getCode());
            } else if (boundKey.getCategory() == InputUtil.Type.MOUSE) {
                isHeld = GLFW.glfwGetMouseButton(handle, boundKey.getCode()) == GLFW.GLFW_PRESS;
            }
        }

        if (!isHeld) {
            // Key was released, select hovered and close
            selectHovered(true);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (blocks.isEmpty()) return;

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        double angleStep = (2 * Math.PI) / blocks.size();

        // Update state
        relativeAngle = getRelativeAngleToMouse(centerX, centerY, mouseX, mouseY);
        updateHoveredIndex(centerX, centerY, mouseX, mouseY, angleStep);

        // Rendering order
        renderFilteredHotbar(context);
        renderBackgroundSlices(context, centerX, centerY);
        renderItems(context, centerX, centerY, angleStep);
        renderTooltips(context, centerX, centerY);

        if (isCtrlPressed()) {
            renderDebugInfo(context, centerX, centerY, relativeAngle);
        }

        super.render(context, mouseX, mouseY, delta);

        // Execute any screen transitions queued during tick or input
        if (this.pendingAction != null) {
            this.pendingAction.run();
            this.pendingAction = null;
        }
    }

    private void updateHoveredIndex(int centerX, int centerY, int mouseX, int mouseY, double angleStep) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distSq = dx * dx + dy * dy;

        hoveredIndex = -1;
        // Check if mouse is within the radial ring hit area
        if (distSq > MIN_HOVER_DIST_SQ && distSq < MAX_HOVER_DIST_SQ) {
            double mouseAngle = Math.atan2(dy, dx);
            if (mouseAngle < 0) mouseAngle += 2 * Math.PI;

            hoveredIndex = (int) Math.round(mouseAngle / angleStep) % blocks.size();
        }
    }

    private void renderBackgroundSlices(DrawContext context, int centerX, int centerY) {
        int halfThickness = RADIAL_RING_THICKNESS / 2;
        int innerDiam = RadialMenuScreen.RADIAL_RADIUS - halfThickness;
        int outerDiam = RadialMenuScreen.RADIAL_RADIUS + halfThickness;

        for (int i = 0; i < blocks.size(); i++) {
            if (i == hoveredIndex) {
                // Highlight hovered slice
                drawCircleSlice(context, centerX, centerY, outerDiam + 6, innerDiam - 4, i, blocks.size(), 0x40666666);
            } else {
                // Default slice
                drawCircleSlice(context, centerX, centerY, outerDiam, innerDiam, i, blocks.size(), 0x7F333333);
            }
        }
    }

    private void renderItems(DrawContext context, int centerX, int centerY, double angleStep) {
        for (int i = 0; i < blocks.size(); i++) {
            double angle = i * angleStep;
            Block block = blocks.get(i);
            ItemStack stack = new ItemStack(block);

            // Render the central rotating preview for the hovered (or currently active) block
            if ((i == hoveredIndex) || (block == currentBlock && hoveredIndex == -1)) {
                drawRotatedItem(context, stack, centerX, centerY + 16, -centerBlockAngle, 8f);
            }

            // Calculate position for the block icon in the radial menu
            int x = centerX + (int) (RadialMenuScreen.RADIAL_RADIUS * Math.cos(angle));
            int y = centerY + (int) (RadialMenuScreen.RADIAL_RADIUS * Math.sin(angle));

            // Highlight the base block with a larger scale
            float scale = (baseBlock == block && isCtrlPressed()) ? 1.5f : 1.0f;

            drawScaledItem(context, stack, x, y, scale);
        }
    }

    private void renderTooltips(DrawContext context, int centerX, int centerY) {
        if (hoveredIndex != -1) {
            Block block = blocks.get(hoveredIndex);

            // Primary tooltip (Block Name)
            List<Text> tooltip = new ArrayList<>(List.of(block.getName()));
            drawCenteredTooltip(context, tooltip, centerX, centerY / 6);

            // Debug tooltip (Block Class and Relationship source)
            if (isCtrlPressed()) {
                String reason = Reshaped.MATRIX != null ? Reshaped.MATRIX.getReason(block) : "Unknown reason";
                String blockInstance = block.getClass().getSimpleName();
                List<Text> debugTooltip = new ArrayList<>(List.of(
                        Text.literal(blockInstance).formatted(Formatting.GRAY, Formatting.ITALIC),
                        Text.literal(reason).formatted(Formatting.GRAY, Formatting.ITALIC)
                ));
                context.drawTooltip(this.textRenderer, debugTooltip, -8, 16);
            }
        }
    }

    private boolean isCtrlPressed() {
        if (this.client == null) return false;
        long handle = this.client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private void renderDebugInfo(DrawContext context, int centerX, int centerY, float relativeAngle) {
        String debugText = "Radial: Hover " + hoveredIndex + " | Items: " + blocks.size() + " | relativeAngle: " + relativeAngle;
        int textX = centerX - 150;
        int textY = centerY + RadialMenuScreen.RADIAL_RADIUS + 20;
        context.fill(textX - 2, textY - 2, textX + 150, textY + 12, 0xFF000000);
        context.drawText(this.textRenderer, debugText, textX, textY, 0xFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            selectHovered(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.client != null && this.client.player != null) {
            if (hoveredIndex != -1) {
                selectHovered(false);
            }
            List<ItemStack> filtered = generateFilteredHotbar();
            if (filtered.isEmpty()) return true;
            int i = this.client.player.getInventory().selectedSlot;
            int j = (int) Math.signum(amount);
            int newSlot = i;
            while (true) {
                // Change slot in the scrolling direction
                newSlot -= j;
                // Clamp slot index to valid hotbar range [0, 8]
                while (newSlot < 0) newSlot += 9;
                while (newSlot >= 9) newSlot -= 9;
                // Get item in the current slot
                ItemStack stack = this.client.player.getInventory().getStack(newSlot);
                Block block = stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
                // Check if the item belongs to a block matrix (isValid for radial menu)
                if (!stack.isEmpty() && Reshaped.MATRIX.hasBlock(block)) break;
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

    private void selectHovered(boolean close) {
        if (hoveredIndex != -1) {
            Block selectedBlock = blocks.get(hoveredIndex);
            NetworkHandler.sendConvertBlockPacket(Registries.BLOCK.getId(selectedBlock), slot);
        }
        if (close) this.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Renders a filtered version of the hotbar containing only blocks that belong to a matrix.
     */
    private void renderFilteredHotbar(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        assert client.player != null;

        List<ItemStack> filtered = generateFilteredHotbar();
        if (filtered.isEmpty()) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int slotSize = 20;
        int totalWidth = filtered.size() * slotSize;
        int xStart = (screenWidth - totalWidth) / 2;
        int y = screenHeight - 22;

        int slotWidth = 20;
        for (int i = 0; i < filtered.size(); i++) {
            int x = xStart + i * slotWidth;
            resetRender();
            int slot;
            // Map index to radial slot texture ID (1-9)
            if (i == filtered.size() - 1) slot = 9;
            else slot = i + 1;

            drawHotbarTexture(context, slot, x, y);

            // Render the item in the slot
            ItemStack stack = filtered.get(i);

            context.drawItem(stack, x + 2, y + 3);
            context.drawItemInSlot(this.textRenderer, stack, x + 2, y + 3);
        }

        // Draw the selection highlight
        int x = xStart + selectedSlotInSortedHotbar * slotWidth;
        y = screenHeight - 22;
        drawHotbarTexture(context, -1, x, y);
    }

    private List<ItemStack> generateFilteredHotbar() {
        MinecraftClient client = MinecraftClient.getInstance();
        assert client.player != null;
        PlayerInventory inv = client.player.getInventory();

        List<ItemStack> filtered = new ArrayList<>();
        int selectedSlot = client.player.getInventory().selectedSlot;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            Block block = stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
            if (!stack.isEmpty() && Reshaped.MATRIX.hasBlock(block)) {
                filtered.add(stack);
                if (i == selectedSlot) {
                    selectedSlotInSortedHotbar = filtered.size() - 1;
                }
            }
        }
        return filtered;
    }

    private void drawCenteredTooltip(
            DrawContext context,
            List<Text> lines,
            int centerX,
            int y
    ) {
        int maxWidth = 0;
        for (Text line : lines) {
            maxWidth = Math.max(maxWidth, this.textRenderer.getWidth(line));
        }

        int tooltipPadding = 12; // vanilla tooltip offset
        int x = centerX - maxWidth / 2 - tooltipPadding;

        context.drawTooltip(this.textRenderer, lines, x, y);
    }

}