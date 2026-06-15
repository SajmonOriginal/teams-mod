package cz.sajmonoriginal.teamsmod.chat;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Soft dependency on the <a href="https://modrinth.com/mod/chat-heads">ChatHeads</a>
 * client mod.
 *
 * <p>Inspecting ChatHeads' source (<code>ChatComponentMixin2</code>) shows
 * the integration point is {@code ChatHeads.lastSenderData} - the static
 * field caller code primes <em>before</em> {@code chat.addMessage(...)}.
 * ChatHeads' {@code @ModifyArg} on the {@code addMessageToDisplayQueue}
 * invocation reads {@code lastSenderData} and attaches it to the new
 * {@code GuiMessage} via {@code ChatHeads.setHeadData}. After {@code
 * addMessage} returns, the field is reset to {@code HeadData.EMPTY}, so
 * we must prime it for every team-chat line.
 */
public final class ChatHeadsCompat {

    private static final Method HEAD_DATA_OF;
    private static final Field LAST_SENDER_DATA_FIELD;

    static {
        Method m = null;
        Field f = null;
        try {
            Class<?> headDataCls = Class.forName("dzwdz.chat_heads.HeadData");
            m = headDataCls.getMethod("of", PlayerInfo.class);
            Class<?> chatHeadsCls = Class.forName("dzwdz.chat_heads.ChatHeads");
            f = chatHeadsCls.getField("lastSenderData");
            TeamsMod.LOG.info("[teamsmod] chat-heads compat ENABLED (lastSenderData hooked)");
        } catch (ClassNotFoundException e) {
            TeamsMod.LOG.info("[teamsmod] chat-heads not detected - team chat will not show face icons");
        } catch (NoSuchMethodException | NoSuchFieldException | SecurityException e) {
            TeamsMod.LOG.warn("[teamsmod] chat-heads present but reflective lookup failed", e);
        }
        HEAD_DATA_OF = m;
        LAST_SENDER_DATA_FIELD = f;
    }

    private ChatHeadsCompat() {}

    /**
     * Primes ChatHeads' {@code lastSenderData} so the next
     * {@code chat.addMessage(...)} call gets a face attached. No-op if
     * ChatHeads isn't installed.
     */
    public static void primeForNextMessage(UUID senderUuid) {
        if (HEAD_DATA_OF == null || LAST_SENDER_DATA_FIELD == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        PlayerInfo info = mc.getConnection().getPlayerInfo(senderUuid);
        if (info == null) {
            TeamsMod.LOG.info("[teamsmod] chat-heads: no PlayerInfo for {} (skipping head)", senderUuid);
            return;
        }
        try {
            Object headData = HEAD_DATA_OF.invoke(null, info);
            LAST_SENDER_DATA_FIELD.set(null, headData);
            TeamsMod.LOG.info("[teamsmod] chat-heads primed lastSenderData for {}", info.getProfile().getName());
        } catch (Throwable t) {
            TeamsMod.LOG.warn("[teamsmod] chat-heads prime failed", t);
        }
    }
}
