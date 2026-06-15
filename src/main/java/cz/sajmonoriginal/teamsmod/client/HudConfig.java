package cz.sajmonoriginal.teamsmod.client;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether the on-screen teammate HUD should render. Toggleable from the team
 * menu, persisted to {@code config/teamsmod-client.txt} so the choice survives
 * relog. One-line file kept deliberately tiny - no need for the heavier
 * NeoForge config infrastructure for a single boolean.
 */
public final class HudConfig {

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("teamsmod-client.txt");
    private static boolean hudEnabled = true;
    private static boolean loaded;

    private HudConfig() {}

    public static boolean isEnabled() {
        if (!loaded) load();
        return hudEnabled;
    }

    public static void toggle() {
        if (!loaded) load();
        hudEnabled = !hudEnabled;
        save();
    }

    private static void load() {
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                hudEnabled = !Files.readString(FILE).trim().equalsIgnoreCase("hud=off");
            }
        } catch (Exception e) {
            TeamsMod.LOG.warn("[teamsmod] failed to load HUD config", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, hudEnabled ? "hud=on" : "hud=off");
        } catch (Exception e) {
            TeamsMod.LOG.warn("[teamsmod] failed to save HUD config", e);
        }
    }
}
