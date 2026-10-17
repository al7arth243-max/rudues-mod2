package com.rudues.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class ClientKeys {
    /** Hold to open the skill wheel. Rebindable in Controls. */
    public static final KeyMapping OPEN_WHEEL = new KeyMapping("key.rudues.open_wheel",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_R, "key.categories.rudues");

    /** Universal button: casts whichever ability is currently selected on the wheel. Rebindable in Controls. */
    public static final KeyMapping CAST_SELECTED = new KeyMapping("key.rudues.cast_ability",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_G, "key.categories.rudues");

    /** True while the physical key/button of this mapping is held (works for keyboard and mouse binds). */
    public static boolean isHeld(KeyMapping mapping) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        InputConstants.Key key = mapping.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getValue() < 0) {
            return false;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }
}
