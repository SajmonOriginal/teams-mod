package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> Server. Description may be empty. */
public record CreateTeamRequestPayload(String name, String description) implements CustomPacketPayload {

    public static final Type<CreateTeamRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "create_team"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateTeamRequestPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUtf(p.name); buf.writeUtf(p.description == null ? "" : p.description); },
            buf -> new CreateTeamRequestPayload(buf.readUtf(), buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
