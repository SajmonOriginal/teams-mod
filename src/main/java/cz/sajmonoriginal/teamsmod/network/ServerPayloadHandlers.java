package cz.sajmonoriginal.teamsmod.network;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.network.payload.CreateTeamRequestPayload;
import cz.sajmonoriginal.teamsmod.network.payload.InviteRequestPayload;
import cz.sajmonoriginal.teamsmod.network.payload.InviteResponsePayload;
import cz.sajmonoriginal.teamsmod.network.payload.KickRequestPayload;
import cz.sajmonoriginal.teamsmod.network.payload.LeaveRequestPayload;
import cz.sajmonoriginal.teamsmod.chat.StyledChatCompat;
import cz.sajmonoriginal.teamsmod.network.payload.SetChannelPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamChatBroadcastPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamChatPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamSyncPayload;
import cz.sajmonoriginal.teamsmod.team.ChatChannel;
import cz.sajmonoriginal.teamsmod.team.Team;
import cz.sajmonoriginal.teamsmod.team.TeamManager;
import cz.sajmonoriginal.teamsmod.team.TeamMember;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public final class ServerPayloadHandlers {

    private ServerPayloadHandlers() {}

    public static void handleSetChannel(SetChannelPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ChatChannel ch = ChatChannel.byOrdinal(payload.ordinal());
            TeamManager mgr = TeamManager.getOrNull();
            if (mgr == null) return;
            mgr.setChannel(sp.getUUID(), ch);
            TeamsMod.LOG.info("[teamsmod] {} switched chat channel to {}", sp.getGameProfile().getName(), ch);
            // Echo back so the client UI is in sync.
            Optional<Team> team = mgr.teamOf(sp.getUUID());
            if (team.isPresent()) TeamSyncPayload.send(sp, team.get(), ch);
            else TeamSyncPayload.sendEmpty(sp);
        });
    }

    public static void handleCreateTeam(CreateTeamRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TeamManager mgr = TeamManager.getOrNull();
            if (mgr == null) return;
            TeamManager.CreateResult r = mgr.createTeam(sp, payload.name(), payload.description());
            if (!r.success()) sp.sendSystemMessage(r.error().copy().withStyle(ChatFormatting.RED));
            else sp.sendSystemMessage(Component.translatable("teamsmod.created", payload.name()).withStyle(ChatFormatting.GREEN));
        });
    }

    public static void handleInvite(InviteRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TeamsMod.LOG.info("[teamsmod] InviteRequest: from={} target={}",
                    sp.getGameProfile().getName(), payload.playerName());
            MinecraftServer server = sp.getServer();
            if (server == null) return;
            ServerPlayer target = server.getPlayerList().getPlayerByName(payload.playerName());
            if (target == null) {
                TeamsMod.LOG.info("[teamsmod] -> target offline / not found");
                sp.sendSystemMessage(Component.translatable("teamsmod.error.player_offline").withStyle(ChatFormatting.RED));
                return;
            }
            TeamManager.ActionResult r = TeamManager.get().invite(sp, target);
            TeamsMod.LOG.info("[teamsmod] -> invite success={} error={}",
                    r.success(), r.success() ? "none" : r.error().getString());
            if (!r.success()) sp.sendSystemMessage(r.error().copy().withStyle(ChatFormatting.RED));
            else sp.sendSystemMessage(Component.translatable("teamsmod.invited", payload.playerName()).withStyle(ChatFormatting.GREEN));
        });
    }

    public static void handleInviteResponse(InviteResponsePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TeamManager mgr = TeamManager.getOrNull();
            if (mgr == null) return;
            TeamManager.ActionResult r = payload.accept()
                    ? mgr.acceptInvite(sp, payload.teamId())
                    : mgr.declineInvite(sp, payload.teamId());
            if (!r.success()) sp.sendSystemMessage(r.error().copy().withStyle(ChatFormatting.RED));
        });
    }

    public static void handleKick(KickRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TeamManager.ActionResult r = TeamManager.get().kick(sp, payload.target());
            if (!r.success()) sp.sendSystemMessage(r.error().copy().withStyle(ChatFormatting.RED));
        });
    }

    public static void handleLeave(LeaveRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TeamManager.get().leave(sp);
        });
    }

    /**
     * Receives a TEAM-channel message from the client and ships the formatted
     * {@link Component} only to that player's teammates. We deliberately
     * bypass {@code ServerChatEvent} so server-side chat plugins can't leak
     * the line into ALL chat by broadcasting manually.
     *
     * <p>Formatting goes through {@link StyledChatCompat} so styled-chat (or
     * any future chat-formatter compat shim we add) produces the same look
     * as a regular chat line. The green "team chat" cue is the
     * {@link net.minecraft.client.GuiMessageTag} indicator side-bar applied
     * client-side - no extra characters are prepended to the message itself.
     */
    public static void handleTeamChat(TeamChatPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            TeamManager mgr = TeamManager.getOrNull();
            if (mgr == null) return;

            var teamOpt = mgr.teamOf(sp.getUUID());
            if (teamOpt.isEmpty()) {
                sp.sendSystemMessage(Component.translatable("teamsmod.chat.no_team").withStyle(ChatFormatting.YELLOW));
                return;
            }
            Team team = teamOpt.get();
            String msg = payload.message() == null ? "" : payload.message().trim();
            if (msg.isEmpty()) return;
            if (msg.length() > 256) msg = msg.substring(0, 256);

            Component formatted = StyledChatCompat.format(sp, msg);

            int delivered = 0;
            for (TeamMember m : team.members()) {
                ServerPlayer recipient = mgr.server().getPlayerList().getPlayer(m.uuid());
                if (recipient != null) {
                    PacketDistributor.sendToPlayer(recipient,
                            new TeamChatBroadcastPayload(sp.getUUID(), formatted));
                    delivered++;
                }
            }
            TeamsMod.LOG.info("[teamsmod] team chat from {}: delivered to {} of '{}' (styled-chat={})",
                    sp.getGameProfile().getName(), delivered, team.name(), StyledChatCompat.isAvailable());
        });
    }
}
