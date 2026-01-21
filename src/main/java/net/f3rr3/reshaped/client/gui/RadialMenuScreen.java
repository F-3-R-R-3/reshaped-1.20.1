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
    private final List<Block> blocks;
    private final int slot;
    private final Block currentBlock;
    private final Block baseBlock;
    private int hoveredIndex = -1;
    private float centerBlockAngle = 0f; // in rad
    private float angularSpeed = 0.0f; // angular speed (rad/s)
    private float lastRelativeAngle = 0f;
    private float relativeAngle = 0f;
    // Action to perform during the render cycle to ensure safe screen transitions
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

    public static void drawRotatedItem(DrawContext context, ItemStack stack, int x, int y, float angleRad, float scale) {
        var matrices = context.getMatrices();
        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, null, null, 0);
        boolean is3D = model.hasDepth();

        if (!is3D) {
            scale *= 0.6f;
        }

        matrices.push();

        // Set STATIC shader lights for top-left lighting direction
        // Testing negative X to move light to the Left.
        // Y is kept at -1.0f (Top).
        Vector3f lightDir1 = new Vector3f(0.2F, -1.0F, -0.7F).normalize();
        Vector3f lightDir2 = new Vector3f(-0.2F, -1.0F, 0.7F).normalize();  // Lighting taken from the minecraft source code wih reversed y
        RenderSystem.setShaderLights(lightDir1, lightDir2);

        // Places the item in the center of the position and scales it
        matrices.translate(x, y - 16, -100); // -100 = depth above UI
        matrices.scale(scale * 16f, scale * -16f, scale * 16f); // y-axis multiplied by -1 to translate between UI and render code.

        // Rotation around Y-as

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(30));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(angleRad));

        // Use immediate vertex consumer to ensure our shader lights are applied
        VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        // Render call met World en seed
        itemRenderer.renderItem(
                stack,
                ModelTransformationMode.FIXED,
                0xF000F0,                    // light
                OverlayTexture.DEFAULT_UV,    // overlay
                matrices,
                immediate,
                MinecraftClient.getInstance().world, // world
                0                               // seed
        );

        // Draw the buffered content with our custom lighting
        immediate.draw();

        matrices.pop();

        // Restore default GUI depth lighting after rendering
        DiffuseLighting.enableGuiDepthLighting();
    }

    private static void DrawCircleSlice(DrawContext context, int centerX, int centerY, int OuterRadius, int innerRadius, int slice, int NoOfSlices, int color) {
        resetRender();
        float sectionWidth = 360f / NoOfSlices;
        float startAngle = sectionWidth * (slice - 0.5f);
        float endAngle = sectionWidth * (slice + 0.5f);
        CircleTexture.CachedCircle circle = CircleTexture.getOrCreateCircle(OuterRadius, innerRadius, color, 0.7f, 1f, startAngle, endAngle);

        int size = circle.size;
        int x0 = centerX - size / 2;
        int y0 = centerY - size / 2;
        context.drawTexture(circle.textureId, x0, y0, 0, 0, size, size, size, size);
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

    private static void drawHotbarTexture(DrawContext context, int slot, int x, int y) {
        // catch hotbar selector calls
        if (slot == -1) {
            context.drawTexture(HOTBAR_TEXTURE, x - 2, y - 1, 0, 22, 24, 22);
            return;
        }
        // normal hotbar slots
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


        float delta = relativeAngle - lastRelativeAngle;

        // normalise to [-PI, PI]
        if (delta > Math.PI) {
            delta -= (float) (2 * Math.PI);
        } else if (delta < -Math.PI) {
            delta += (float) (2 * Math.PI);
        }

        angularSpeed += delta;
        lastRelativeAngle = relativeAngle;


        // settings
        float angularSpeed_target = 0.5f; // angular speed (rad/s)
        float k = 2.0f;              // strength
        float dt = 0.05f;            // 50 ms


        // friction / settings
        float torque = k * (angularSpeed_target - angularSpeed);
        angularSpeed += torque * dt;

        // integrate angle
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
        lastRelativeAngle = relativeAngle;

        if (!isHeld) {
            // Key was released, select hovered and close
            selectHovered(true);
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

        filteredHotbarRender(context);
        // Calculate hovered index based on mouse angle
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distSq = dx * dx + dy * dy;

        // Update relative angle once per frame
        relativeAngle = getRelativeAngleToMouse(centerX, centerY, mouseX, mouseY);

        if (distSq > 400 && distSq < 15000) {
            double mouseAngle = Math.atan2(dy, dx);
            if (mouseAngle < 0) mouseAngle += 2 * Math.PI;

            hoveredIndex = (int) Math.round(mouseAngle / angleStep) % blocks.size();
        }

        int innerDiam = radius - 10;
        int outerDiam = radius + 10;

        // Pass 1: Draw Background Slices
        for (int i = 0; i < blocks.size(); i++) {
            if (i == hoveredIndex) {
                DrawCircleSlice(context, centerX, centerY, outerDiam + 6, innerDiam - 4, i, blocks.size(), 0x40666666);
            } else {
                DrawCircleSlice(context, centerX, centerY, outerDiam, innerDiam, i, blocks.size(), 0x7F333333);
            }
        }

        // Pass 2: Draw Items
        for (int i = 0; i < blocks.size(); i++) {
            double angle = i * angleStep;
            Block block = blocks.get(i);
            ItemStack stack = new ItemStack(block);

            if ((i == hoveredIndex) || (block == currentBlock && hoveredIndex == -1)) {
                drawRotatedItem(context, stack, centerX, centerY + 16, -centerBlockAngle, 8f);
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
        }

        // Pass 3: Draw Tooltip
        if (hoveredIndex != -1) {
            Block block = blocks.get(hoveredIndex);
            String reason = Reshaped.MATRIX != null ? Reshaped.MATRIX.getReason(block) : "Unknown reason";
            String blockInstance = block.getClass().getSimpleName();
            List<Text> tooltip = new ArrayList<>();
            tooltip.add(block.getName());
            if (isCtrlPressed()) {
                tooltip.addAll(List.of(
                                Text.literal(blockInstance).formatted(Formatting.GRAY, Formatting.ITALIC),
                                Text.literal(reason).formatted(Formatting.GRAY, Formatting.ITALIC)
                        )
                );
            }
            context.drawTooltip(this.textRenderer, tooltip, -8, 16);
        }

        // Debug rendering
        if (isCtrlPressed()) {
            renderDebugInfo(context, centerX, centerY, radius, relativeAngle);
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

    private void renderDebugInfo(DrawContext context, int centerX, int centerY, int radius, float relativeAngle) {
        String debugText = "Radial: Hover " + hoveredIndex + " | Items: " + blocks.size() + " | relativeAngle: " + relativeAngle;
        int textX = centerX - 150;
        int textY = centerY + radius + 20;
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
                // verander slot in de richting van het scrollen
                newSlot -= j;
                // begrens slot in de bestaande hotbar slots
                while (newSlot < 0) newSlot += 9;
                while (newSlot >= 9) newSlot -= 9;
                // haal item in slot op
                ItemStack stack = this.client.player.getInventory().getStack(newSlot);
                Block block = stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
                // test of het is wat we zoeken
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

    private void filteredHotbarRender(DrawContext context) {
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
            if (i == filtered.size() - 1) slot = 9;
            else slot = i + 1;

            drawHotbarTexture(context, slot, x, y);

            // render item
            ItemStack stack = filtered.get(i);

            context.drawItem(stack, x + 2, y + 3);
            context.drawItemInSlot(this.textRenderer, stack, x + 2, y + 3);
        }

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
}