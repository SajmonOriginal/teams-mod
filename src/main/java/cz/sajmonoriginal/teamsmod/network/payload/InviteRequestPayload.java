package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InviteRequestPayload(String playerName) implements CustomPacketPayload {

    public static final Type<InviteRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "invite_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InviteRequestPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.playerName),
            buf -> new InviteRequestPayload(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
