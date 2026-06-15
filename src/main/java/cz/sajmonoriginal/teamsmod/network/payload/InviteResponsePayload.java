package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InviteResponsePayload(int teamId, boolean accept) implements CustomPacketPayload {

    public static final Type<InviteResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "invite_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InviteResponsePayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.teamId); buf.writeBoolean(p.accept); },
            buf -> new InviteResponsePayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
