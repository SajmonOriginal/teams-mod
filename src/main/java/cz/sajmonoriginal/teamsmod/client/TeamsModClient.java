package cz.sajmonoriginal.teamsmod.client;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.network.payload.SetChannelPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamChatPayload;
import cz.sajmonoriginal.teamsmod.team.ChatChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TeamsMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TeamsModClient {

    private TeamsModClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE_CHANNEL);
        event.register(KeyBindings.OPEN_MENU);
        NeoForge.EVENT_BUS.addListener(TeamsModClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(TeamsModClient::onClientChat);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "team_hud"),
                new TeamHudOverlay());
        // Green sidebar painted right after vanilla chat. Avoids depending on
        // GuiMessageTag.indicatorColor (which other client mods like NCR can
        // strip) - we just walk the chat's visible lines ourselves and paint
        // a stripe for any that carry our team tag.
        event.registerAbove(
                VanillaGuiLayers.CHAT,
                ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "team_chat_sidebar"),
                new TeamChatSidebarLayer());
        TeamsMod.LOG.info("[teamsmod] HUD layers registered: team_hud (above all) + team_chat_sidebar (above chat)");
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (KeyBindings.TOGGLE_CHANNEL.consumeClick()) {
            if (!ClientTeamData.INSTANCE.inTeam()) {
                mc.player.displayClientMessage(
                        Component.translatable("teamsmod.error.not_in_team").withStyle(ChatFormatting.YELLOW), true);
                continue;
            }
            ChatChannel next = ClientTeamData.INSTANCE.channel().toggle();
            ClientTeamData.INSTANCE.setChannel(next);
            PacketDistributor.sendToServer(new SetChannelPayload(next.ordinal()));
            mc.player.displayClientMessage(Component.translatable("teamsmod.channel.set", next.name())
                    .withStyle(next == ChatChannel.TEAM ? ChatFormatting.GREEN : ChatFormatting.WHITE), true);
        }

        while (KeyBindings.OPEN_MENU.consumeClick()) {
            ClientTeamData.INSTANCE.openTeamMenu();
        }
    }

    /**
     * Catches the player's chat message before it ever reaches the server.
     * If they're on the TEAM channel, we cancel the vanilla send (so no
     * server-side chat plugin can broadcast it to ALL) and forward the raw
     * text via {@link TeamChatPayload}; the server formats it and dispatches
     * it strictly to teammates.
     */
    private static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message == null || message.isEmpty()) return;
        if (message.startsWith("/")) return;
        if (!ClientTeamData.INSTANCE.inTeam()) return;
        if (ClientTeamData.INSTANCE.channel() != ChatChannel.TEAM) return;

        event.setCanceled(true);
        PacketDistributor.sendToServer(new TeamChatPayload(message));
    }
}
