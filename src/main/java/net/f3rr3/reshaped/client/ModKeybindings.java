package net.f3rr3.reshaped.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModKeybindings {
    public static KeyBinding OPEN_RADIAL_MENU;

    public static void register() {
        OPEN_RADIAL_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.reshaped.open_radial_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT, // Left Alt
            "category.reshaped"
        ));
    }

    /**
     * Check if the radial menu keybind matches the given mouse button.
     */
    public static boolean isRadialMenuButton(int button) {
        return OPEN_RADIAL_MENU.matchesMouse(button);
    }

    /**
     * Check if the radial menu keybind matches the given keyboard key.
     */
    public static boolean isRadialMenuKey(int keyCode) {
        return OPEN_RADIAL_MENU.matchesKey(keyCode, 0);
    }
}
