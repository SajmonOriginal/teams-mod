package cz.sajmonoriginal.teamsmod.chat;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.team.TeamManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Hooks player login/logout into {@link TeamManager}. The chat redirection
 * itself is handled <em>client-side</em> via
 * {@link cz.sajmonoriginal.teamsmod.client.TeamsModClient#registerKeys}'
 * {@code ClientChatEvent} listener - it cancels the vanilla chat send for
 * TEAM-channel lines and forwards the raw text via
 * {@link cz.sajmonoriginal.teamsmod.network.payload.TeamChatPayload}, so the
 * message never enters {@code ServerChatEvent} and no third-party chat mod
 * can leak it to ALL chat.
 */
public final class ChatHandler {

    private ChatHandler() {}

    public static void register(IEventBus bus) {
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, ChatHandler::onLogin);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, ChatHandler::onLogout);
        TeamsMod.LOG.info("[teamsmod] login/logout handler registered");
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            TeamManager mgr = TeamManager.getOrNull();
            if (mgr != null) mgr.onPlayerJoin(sp);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            TeamManager mgr = TeamManager.getOrNull();
            if (mgr != null) mgr.onPlayerLeave(sp);
        }
    }
}
