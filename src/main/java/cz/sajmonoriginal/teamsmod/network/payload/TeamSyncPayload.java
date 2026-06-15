package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.team.ChatChannel;
import cz.sajmonoriginal.teamsmod.team.Team;
import cz.sajmonoriginal.teamsmod.team.TeamMember;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client. Full picture of the receiving player's team.
 * Sent on join, on membership change, and when the chat channel is changed
 * server-side. {@link #teamId} == -1 means "you are not in a team".
 */
public record TeamSyncPayload(int teamId, String name, String description, UUID owner,
                              int channelOrdinal, List<MemberEntry> members)
        implements CustomPacketPayload {

    public static final Type<TeamSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "team_sync"));

    public record MemberEntry(UUID uuid, String name, int roleOrdinal) {
        public static final StreamCodec<FriendlyByteBuf, MemberEntry> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, MemberEntry::uuid,
                ByteBufCodecs.STRING_UTF8, MemberEntry::name,
                ByteBufCodecs.VAR_INT, MemberEntry::roleOrdinal,
                MemberEntry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.teamId);
                buf.writeUtf(p.name);
                buf.writeUtf(p.description);
                buf.writeUUID(p.owner);
                buf.writeVarInt(p.channelOrdinal);
                buf.writeVarInt(p.members.size());
                for (MemberEntry m : p.members) MemberEntry.CODEC.encode(buf, m);
            },
            buf -> {
                int id = buf.readVarInt();
                String name = buf.readUtf();
                String description = buf.readUtf();
                UUID owner = buf.readUUID();
                int ch = buf.readVarInt();
                int n = buf.readVarInt();
                List<MemberEntry> mem = new ArrayList<>(n);
                for (int i = 0; i < n; i++) mem.add(MemberEntry.CODEC.decode(buf));
                return new TeamSyncPayload(id, name, description, owner, ch, mem);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, Team team, ChatChannel channel) {
        List<MemberEntry> entries = new ArrayList<>(team.members().size());
        for (TeamMember m : team.members()) {
            entries.add(new MemberEntry(m.uuid(), m.name(), m.role().ordinal()));
        }
        PacketDistributor.sendToPlayer(player,
                new TeamSyncPayload(team.id(), team.name(), team.description(), team.owner(), channel.ordinal(), entries));
    }

    public static void sendEmpty(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new TeamSyncPayload(-1, "", "", new UUID(0L, 0L), ChatChannel.ALL.ordinal(), List.of()));
    }
}
