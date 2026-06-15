package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server. The raw text the player typed while on the TEAM channel.
 * The client cancels the vanilla chat send entirely and routes the message
 * through this payload so it never enters the public {@code ServerChatEvent}
 * pipeline - third-party chat mods that broadcast manually therefore can't
 * leak a team line into ALL chat.
 */
public record TeamChatPayload(String message) implements CustomPacketPayload {

    public static final Type<TeamChatPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "team_chat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamChatPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.message, 256),
            buf -> new TeamChatPayload(buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
