package cz.sajmonoriginal.teamsmod.network;

import cz.sajmonoriginal.teamsmod.chat.ChatHeadsCompat;
import cz.sajmonoriginal.teamsmod.client.ClientTeamData;
import cz.sajmonoriginal.teamsmod.network.payload.InviteNotifyPayload;
import cz.sajmonoriginal.teamsmod.network.payload.InviteRemovedPayload;
import cz.sajmonoriginal.teamsmod.network.payload.OpenTeamMenuPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamChatBroadcastPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamListSyncPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamSyncPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeammateStatusPayload;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Receives payloads on the client. Each handler is invoked on the netty
 * thread; we hop onto the main client thread via {@link IPayloadContext#enqueueWork}.
 */
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {}

    public static void handleTeamSync(TeamSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTeamData.INSTANCE.applySync(payload));
    }

    public static void handleTeamListSync(TeamListSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTeamData.INSTANCE.applyTeamList(payload));
    }

    public static void handleTeammateStatus(TeammateStatusPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTeamData.INSTANCE.applyStatus(payload));
    }

    public static void handleInviteNotify(InviteNotifyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTeamData.INSTANCE.onInvite(payload));
    }

    public static void handleInviteRemoved(InviteRemovedPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTeamData.INSTANCE.onInviteRemoved(payload.teamId()));
    }

    public static void handleOpenMenu(OpenTeamMenuPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTeamData.INSTANCE.openTeamMenu());
    }

    /**
     * Bright green sidebar for team chat. Mirrors how vanilla's
     * {@code GuiMessageTag.system()} / {@code chatNotSecure()} render the
     * trust-indicator bar - same mechanism, our own colour.
     */
    /** logTag must match {@code ChatComponentMixin.TEAM_LOG_TAG} so our Mixin can spot the line. */
    private static final GuiMessageTag TEAM_CHAT_TAG = new GuiMessageTag(
            0xFF55FF55, null,
            Component.translatable("teamsmod.chat.tag"),
            "teamsmod_team");

    public static void handleTeamChatBroadcast(TeamChatBroadcastPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui == null) return;
            // Tell ChatHeads who sent this line so it can render the head icon
            // before we hand the message off to the chat HUD.
            ChatHeadsCompat.primeForNextMessage(payload.sender());
            // Record the gui-tick *before* addMessage so we can match the
            // resulting GuiMessage.Line by addedTime in the sidebar layer.
            // GuiMessageTag-based detection turned out to be unreliable in
            // practice (something in the chat pipeline drops the tag), so we
            // additionally remember the timestamp ourselves.
            int now = mc.gui.getGuiTicks();
            cz.sajmonoriginal.teamsmod.client.TeamChatTracker.markTeamChat(now);
            mc.gui.getChat().addMessage(payload.formatted(), null, TEAM_CHAT_TAG);
        });
    }
}
