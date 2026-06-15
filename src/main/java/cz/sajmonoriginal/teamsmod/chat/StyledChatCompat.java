package cz.sajmonoriginal.teamsmod.chat;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/**
 * Soft dependency on
 * <a href="https://modrinth.com/mod/styled-chat">styled-chat</a>: when the
 * mod is installed we route team-chat lines through its public formatting
 * helper so they pick up the same prefixes / colors / emotes / placeholders
 * the player would see in regular chat. Without styled-chat, we fall back to
 * a plain vanilla {@code "<Player> message"} line.
 *
 * <p>We talk to styled-chat via reflection rather than a direct compile-time
 * dependency so the mod can ship as a single artifact and gracefully degrade
 * if styled-chat is missing or its API shifts.
 */
public final class StyledChatCompat {

    private static final Method STYLED_CHAT_GET_CHAT;

    static {
        Method m = null;
        try {
            Class<?> clazz = Class.forName("eu.pb4.styledchat.StyledChatStyles");
            m = clazz.getMethod("getChat", ServerPlayer.class, Component.class);
            TeamsMod.LOG.info("[teamsmod] styled-chat compat ENABLED - team chat will use styled-chat formatting");
        } catch (ClassNotFoundException e) {
            TeamsMod.LOG.info("[teamsmod] styled-chat not detected - team chat will use vanilla formatting");
        } catch (NoSuchMethodException | SecurityException e) {
            TeamsMod.LOG.warn("[teamsmod] styled-chat present but reflective lookup failed; falling back to vanilla", e);
        }
        STYLED_CHAT_GET_CHAT = m;
    }

    private StyledChatCompat() {}

    public static boolean isAvailable() {
        return STYLED_CHAT_GET_CHAT != null;
    }

    /**
     * Returns a fully-formatted chat-line {@link Component} for {@code sender}'s
     * raw {@code message}. Uses styled-chat's formatter when present, otherwise
     * builds the vanilla {@code <Player> message} line.
     */
    public static Component format(ServerPlayer sender, String message) {
        Component raw = Component.literal(message);
        if (STYLED_CHAT_GET_CHAT != null) {
            try {
                Component result = (Component) STYLED_CHAT_GET_CHAT.invoke(null, sender, raw);
                if (result != null) return result;
            } catch (Throwable t) {
                TeamsMod.LOG.warn("[teamsmod] styled-chat formatter threw; falling back this message", t);
            }
        }
        return Component.literal("<" + sender.getGameProfile().getName() + "> " + message);
    }
}
