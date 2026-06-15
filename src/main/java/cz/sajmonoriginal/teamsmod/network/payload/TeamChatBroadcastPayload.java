package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server -> Client. The team-chat line (already formatted by styled-chat or
 * whatever server-side chat plugin is installed) plus the sender's UUID. The
 * UUID lets us prime ChatHeads' head-rendering state before handing the line
 * to the chat HUD, so faces show up on team chat the same way they do on
 * regular chat.
 */
public record TeamChatBroadcastPayload(UUID sender, Component formatted) implements CustomPacketPayload {

    public static final Type<TeamChatBroadcastPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "team_chat_broadcast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamChatBroadcastPayload> CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, TeamChatBroadcastPayload::sender,
                    ComponentSerialization.STREAM_CODEC, TeamChatBroadcastPayload::formatted,
                    TeamChatBroadcastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
