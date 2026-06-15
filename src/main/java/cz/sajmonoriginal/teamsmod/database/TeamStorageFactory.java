package cz.sajmonoriginal.teamsmod.database;

import cz.sajmonoriginal.teamsmod.StorageConfig;
import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class TeamStorageFactory {

    private TeamStorageFactory() {}

    public static TeamStorage open(MinecraftServer server) {
        String backend = StorageConfig.BACKEND.get().trim().toLowerCase();
        switch (backend) {
            case "sqlite": {
                String configured = StorageConfig.SQLITE_FILE.get();
                Path path = (configured == null || configured.isBlank())
                        ? server.getWorldPath(LevelResource.ROOT).resolve("teamsmod").resolve("teams.db")
                        : Paths.get(configured);
                TeamsMod.LOG.info("teamsmod storage: sqlite at {}", path);
                return SqliteTeamStorage.open(path);
            }
            case "postgres": {
                String url = StorageConfig.POSTGRES_URL.get();
                if (url == null || url.isBlank()) {
                    throw new IllegalStateException(
                            "storage.backend=postgres but storage.postgres.url is empty; set it in teamsmod-server.toml");
                }
                TeamsMod.LOG.info("teamsmod storage: postgres at {}", url);
                return PostgresTeamStorage.open(url, StorageConfig.POSTGRES_USER.get(), StorageConfig.POSTGRES_PASSWORD.get());
            }
            case "api": {
                String baseUrl = StorageConfig.API_BASE_URL.get();
                String key = StorageConfig.API_INTERNAL_KEY.get();
                if (baseUrl == null || baseUrl.isBlank()) {
                    throw new IllegalStateException(
                            "storage.backend=api but storage.api.base_url is empty; set it in teamsmod-server.toml");
                }
                if (key == null || key.isBlank()) {
                    throw new IllegalStateException(
                            "storage.backend=api but storage.api.internal_key is empty; set it in teamsmod-server.toml");
                }
                long pollInterval = StorageConfig.API_POLL_INTERVAL_SECONDS.get();
                TeamsMod.LOG.info("teamsmod storage: api at {}", baseUrl);
                return HttpApiTeamStorage.open(baseUrl, key, pollInterval);
            }
            default:
                throw new IllegalStateException(
                        "Unknown storage.backend '" + backend + "'; expected one of: sqlite, postgres, api");
        }
    }
}
