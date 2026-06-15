package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LeaveRequestPayload() implements CustomPacketPayload {

    public static final Type<LeaveRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "leave_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LeaveRequestPayload> CODEC =
            StreamCodec.unit(new LeaveRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
