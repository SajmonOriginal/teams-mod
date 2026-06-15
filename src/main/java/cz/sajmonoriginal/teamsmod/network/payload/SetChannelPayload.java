package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server. Switch your active chat channel. */
public record SetChannelPayload(int ordinal) implements CustomPacketPayload {

    public static final Type<SetChannelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "set_channel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetChannelPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.ordinal),
            buf -> new SetChannelPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
