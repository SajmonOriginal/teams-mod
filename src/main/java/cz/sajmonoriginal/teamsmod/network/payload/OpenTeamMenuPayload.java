package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> Client. Asks the client to open the team menu screen. */
public record OpenTeamMenuPayload() implements CustomPacketPayload {

    public static final Type<OpenTeamMenuPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "open_menu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTeamMenuPayload> CODEC =
            StreamCodec.unit(new OpenTeamMenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
