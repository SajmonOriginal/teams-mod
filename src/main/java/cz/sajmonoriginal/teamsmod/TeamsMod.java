package cz.sajmonoriginal.teamsmod;

import cz.sajmonoriginal.teamsmod.chat.ChatHandler;
import cz.sajmonoriginal.teamsmod.command.TeamCommand;
import cz.sajmonoriginal.teamsmod.network.PacketHandler;
import cz.sajmonoriginal.teamsmod.team.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TeamsMod.MOD_ID)
public final class TeamsMod {

    public static final String MOD_ID = "teamsmod";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public TeamsMod(IEventBus modBus, ModContainer container) {
        PacketHandler.register(modBus);
        NeoForge.EVENT_BUS.register(this);
        ChatHandler.register(NeoForge.EVENT_BUS);
        container.registerConfig(ModConfig.Type.SERVER, StorageConfig.SPEC, MOD_ID + "-server.toml");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        TeamManager.init(server);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        TeamManager.shutdown();
    }

    // Slash commands intentionally disabled - every team action goes through
    // the in-game GUI now. The TeamCommand class is preserved so we can flip
    // this back on later by un-commenting the register call below.
    // @SubscribeEvent
    // public void onRegisterCommands(RegisterCommandsEvent event) {
    //     TeamCommand.register(event.getDispatcher());
    // }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        TeamManager manager = TeamManager.getOrNull();
        if (manager != null) {
            manager.tick();
        }
    }
}
