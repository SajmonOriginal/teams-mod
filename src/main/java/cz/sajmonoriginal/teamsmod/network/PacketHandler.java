package cz.sajmonoriginal.teamsmod.network;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.network.payload.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PacketHandler {

    public static final String VERSION = "1";

    private PacketHandler() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(PacketHandler::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TeamsMod.MOD_ID).versioned(VERSION);

        // Server -> Client
        registrar.playToClient(TeamSyncPayload.TYPE, TeamSyncPayload.CODEC, ClientPayloadHandlers::handleTeamSync);
        registrar.playToClient(TeamListSyncPayload.TYPE, TeamListSyncPayload.CODEC, ClientPayloadHandlers::handleTeamListSync);
        registrar.playToClient(TeammateStatusPayload.TYPE, TeammateStatusPayload.CODEC, ClientPayloadHandlers::handleTeammateStatus);
        registrar.playToClient(InviteNotifyPayload.TYPE, InviteNotifyPayload.CODEC, ClientPayloadHandlers::handleInviteNotify);
        registrar.playToClient(InviteRemovedPayload.TYPE, InviteRemovedPayload.CODEC, ClientPayloadHandlers::handleInviteRemoved);
        registrar.playToClient(OpenTeamMenuPayload.TYPE, OpenTeamMenuPayload.CODEC, ClientPayloadHandlers::handleOpenMenu);
        registrar.playToClient(TeamChatBroadcastPayload.TYPE, TeamChatBroadcastPayload.CODEC, ClientPayloadHandlers::handleTeamChatBroadcast);

        // Client -> Server
        registrar.playToServer(SetChannelPayload.TYPE, SetChannelPayload.CODEC, ServerPayloadHandlers::handleSetChannel);
        registrar.playToServer(CreateTeamRequestPayload.TYPE, CreateTeamRequestPayload.CODEC, ServerPayloadHandlers::handleCreateTeam);
        registrar.playToServer(InviteRequestPayload.TYPE, InviteRequestPayload.CODEC, ServerPayloadHandlers::handleInvite);
        registrar.playToServer(InviteResponsePayload.TYPE, InviteResponsePayload.CODEC, ServerPayloadHandlers::handleInviteResponse);
        registrar.playToServer(KickRequestPayload.TYPE, KickRequestPayload.CODEC, ServerPayloadHandlers::handleKick);
        registrar.playToServer(LeaveRequestPayload.TYPE, LeaveRequestPayload.CODEC, ServerPayloadHandlers::handleLeave);
        registrar.playToServer(TeamChatPayload.TYPE, TeamChatPayload.CODEC, ServerPayloadHandlers::handleTeamChat);
    }
}
