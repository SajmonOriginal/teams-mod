package cz.sajmonoriginal.teamsmod.client.screen;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Panel chrome rendered from real PNG sprites with nine-slice scaling - same
 * mechanism vanilla uses for its widget atlas. The textures live at
 * {@code assets/teamsmod/textures/gui/sprites/panel.png} (light-gray outer
 * panel, white-top-left / dark-bottom-right bevel) and {@code panel_inset.png}
 * (dark inverted-bevel content box, like an oversized inventory slot).
 */
public final class Panels {

    public static final ResourceLocation PANEL_SPRITE       = ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "panel");
    public static final ResourceLocation PANEL_INSET_SPRITE = ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "panel_inset");

    /** Default text colour for labels / titles drawn directly on the gray panel. */
    public static final int LABEL_COLOR = 0xFF404040;
    public static final int FONT_COLOR  = 0xFF404040;

    private Panels() {}

    /** Outer gray panel with chest-style bevel. */
    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.blitSprite(PANEL_SPRITE, x, y, w, h);
    }

    /**
     * Centered text without drop shadow. Use this for titles, labels and any
     * other text drawn directly on the gray panel - vanilla's
     * {@link GuiGraphics#drawCenteredString} always paints a black shadow,
     * which on a light-gray surface looks muddy.
     */
    public static void drawCenteredText(GuiGraphics g, net.minecraft.client.gui.Font font,
                                        net.minecraft.network.chat.Component text,
                                        int cx, int y, int color) {
        int w = font.width(text);
        g.drawString(font, text, cx - w / 2, y, color, false);
    }

    /** Dark inset content box (the list / slot area inside a panel). */
    public static void drawContentBox(GuiGraphics g, int x, int y, int w, int h) {
        g.blitSprite(PANEL_INSET_SPRITE, x, y, w, h);
    }

    public static String truncate(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String suffix = "…";
        int suffixW = font.width(suffix);
        int target = Math.max(0, maxWidth - suffixW);
        String result = text;
        while (!result.isEmpty() && font.width(result) > target) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    /**
     * Returns {@code text} cropped (with a trailing ellipsis) to the longest
     * prefix that wraps into at most {@code maxLines} lines at the given
     * {@code maxWidthPerLine}. If it already fits, returns the input.
     * Binary-searches the cut-point so the cropped string is as long as
     * possible while still fitting.
     */
    public static String truncateMultiline(Font font, String text, int maxWidthPerLine, int maxLines) {
        if (text == null || text.isEmpty()) return "";
        var literal = net.minecraft.network.chat.Component.literal(text);
        if (font.split(literal, maxWidthPerLine).size() <= maxLines) return text;

        String suffix = "…";
        int low = 0, high = text.length();
        String best = suffix;
        while (low <= high) {
            int mid = (low + high) / 2;
            String candidate = text.substring(0, mid) + suffix;
            int lines = font.split(net.minecraft.network.chat.Component.literal(candidate), maxWidthPerLine).size();
            if (lines <= maxLines) {
                best = candidate;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }
}
