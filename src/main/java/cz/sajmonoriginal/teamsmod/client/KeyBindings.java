package cz.sajmonoriginal.teamsmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class KeyBindings {

    public static final String CATEGORY = "key.categories.teamsmod";

    public static final KeyMapping TOGGLE_CHANNEL = new KeyMapping(
            "key.teamsmod.toggle_channel",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_K,
            CATEGORY);

    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.teamsmod.open_menu",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_O,
            CATEGORY);

    private KeyBindings() {}
}
