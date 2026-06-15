package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server -> Client. Tells the client to drop a specific pending invite from
 * its local list. Sent when the player accepts, declines, or the invite
 * otherwise becomes invalid.
 */
public record InviteRemovedPayload(int teamId) implements CustomPacketPayload {

    public static final Type<InviteRemovedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "invite_removed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InviteRemovedPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.teamId),
            buf -> new InviteRemovedPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(ServerPlayer player, int teamId) {
        PacketDistributor.sendToPlayer(player, new InviteRemovedPayload(teamId));
    }
}
