package cz.sajmonoriginal.teamsmod.client;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.network.payload.SetChannelPayload;
import cz.sajmonoriginal.teamsmod.team.ChatChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Compact ALL / TEAM channel pill, anchored to the <em>top-right corner</em>
 * of the chat input row. The position deliberately stays out of the way of:
 *
 * <ul>
 *   <li>vanilla command suggestions, which appear flush-left above the input
 *       when the player types {@code /…}; we additionally hide the pill
 *       entirely while the input starts with a slash so suggestions get the
 *       full row,</li>
 *   <li>third-party "is typing" / status indicators (WAILA-style chat mods)
 *       which historically anchor flush-left above the input.</li>
 * </ul>
 *
 * The "click to switch" / "join a team" hint is no longer rendered inline -
 * it surfaces as a hover tooltip so the pill's footprint is just
 * {@code [ALL]} / {@code [TEAM]} text.
 */
@EventBusSubscriber(modid = TeamsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {

    private ClientGameEvents() {}

    private static int badgeX, badgeY, badgeW, badgeH;
    private static boolean badgeVisible;

    /** Cached reflective handle for ChatScreen's protected {@code input} EditBox. */
    private static final Field CHAT_INPUT_FIELD;
    static {
        Field f = null;
        try {
            f = ChatScreen.class.getDeclaredField("input");
            f.setAccessible(true);
        } catch (NoSuchFieldException ignored) {
            // Vanilla field renamed - we'll just always show the badge.
        }
        CHAT_INPUT_FIELD = f;
    }

    private static String currentChatInput(ChatScreen chat) {
        if (CHAT_INPUT_FIELD == null) return "";
        try {
            Object box = CHAT_INPUT_FIELD.get(chat);
            return (box instanceof EditBox eb) ? eb.getValue() : "";
        } catch (IllegalAccessException ignored) {
            return "";
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen chat)) {
            badgeVisible = false;
            return;
        }

        // Hide entirely while typing a command - vanilla's suggestion pop-up
        // fills the same row and we'd just clobber it.
        String input = currentChatInput(chat);
        if (input.startsWith("/")) {
            badgeVisible = false;
            return;
        }

        ChatChannel ch = ClientTeamData.INSTANCE.channel();
        boolean inTeam = ClientTeamData.INSTANCE.inTeam();
        Minecraft mc = Minecraft.getInstance();

        Component label = Component.literal("[" + ch.name() + "]");
        int labelW = mc.font.width(label);
        int padX = 4, padY = 2;
        int accentW = 2;
        int totalW = accentW + 2 + labelW + padX * 2;

        badgeX = chat.width - totalW - 4;
        badgeY = chat.height - 28;
        badgeW = totalW;
        badgeH = mc.font.lineHeight + padY * 2;
        badgeVisible = true;

        int accent = inTeam ? ch.color() : 0xFF666666;
        int textColor = inTeam ? ch.color() : 0xFF888888;

        GuiGraphics g = event.getGuiGraphics();
        g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xCC101010);
        g.fill(badgeX, badgeY, badgeX + accentW, badgeY + badgeH, accent);
        g.drawString(mc.font, label, badgeX + accentW + padX, badgeY + padY, textColor, false);

        // Hover tooltip carries the longer "click to switch" / "no team" hint
        // so the pill itself can stay compact.
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        if (mouseX >= badgeX && mouseX < badgeX + badgeW && mouseY >= badgeY && mouseY < badgeY + badgeH) {
            Component tooltip = inTeam
                    ? Component.translatable("teamsmod.gui.click_to_toggle").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("teamsmod.gui.no_team_hint").withStyle(ChatFormatting.DARK_GRAY);
            g.renderTooltip(mc.font, tooltip, mouseX, mouseY);
        }
    }

    /**
     * Re-paints the team chat sidebar after {@link ChatScreen#render} runs,
     * because that method calls {@code chat.render(..., true)} a second time
     * which would otherwise repaint the chat backgrounds on top of the bars
     * we already drew during the HUD layer pass.
     */
    @SubscribeEvent
    public static void onScreenRenderPostForSidebar(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;
        TeamChatSidebarLayer.paintBars(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;
        if (event.getButton() != 0) return;
        if (!badgeVisible) return;

        double mx = event.getMouseX();
        double my = event.getMouseY();
        if (mx < badgeX || mx > badgeX + badgeW || my < badgeY || my > badgeY + badgeH) return;

        if (!ClientTeamData.INSTANCE.inTeam()) {
            // No team - toggle is a no-op so nothing flickers.
            event.setCanceled(true);
            return;
        }

        ChatChannel next = ClientTeamData.INSTANCE.channel().toggle();
        ClientTeamData.INSTANCE.setChannel(next);
        PacketDistributor.sendToServer(new SetChannelPayload(next.ordinal()));
        event.setCanceled(true);
    }
}
