package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record KickRequestPayload(UUID target) implements CustomPacketPayload {

    public static final Type<KickRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "kick_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KickRequestPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUUID(p.target),
            buf -> new KickRequestPayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
