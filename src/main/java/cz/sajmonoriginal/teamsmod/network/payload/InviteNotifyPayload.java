package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.team.TeamInvite;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server -> Client. "You were invited to <team> by <inviter>." */
public record InviteNotifyPayload(int teamId, String teamName, String inviterName) implements CustomPacketPayload {

    public static final Type<InviteNotifyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "invite_notify"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InviteNotifyPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.teamId); buf.writeUtf(p.teamName); buf.writeUtf(p.inviterName); },
            buf -> new InviteNotifyPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(ServerPlayer player, TeamInvite inv) {
        PacketDistributor.sendToPlayer(player,
                new InviteNotifyPayload(inv.teamId(), inv.teamName(), inv.inviterName()));
    }
}
