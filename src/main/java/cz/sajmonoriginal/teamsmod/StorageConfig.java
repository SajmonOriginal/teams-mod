package cz.sajmonoriginal.teamsmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side storage configuration. Held in a single TOML file:
 *
 * <pre>
 * [storage]
 * backend = "sqlite"  # "sqlite" | "postgres" | "api"
 *
 * [storage.sqlite]
 * file = ""           # default: &lt;world&gt;/teamsmod/teams.db
 *
 * [storage.postgres]
 * url = "jdbc:postgresql://localhost:5432/teams"
 * user = ""
 * password = ""
 *
 * [storage.api]
 * base_url = "http://localhost:3000"
 * internal_key = ""
 * poll_interval_seconds = 3
 * </pre>
 */
public final class StorageConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> BACKEND;
    public static final ModConfigSpec.ConfigValue<String> SQLITE_FILE;
    public static final ModConfigSpec.ConfigValue<String> POSTGRES_URL;
    public static final ModConfigSpec.ConfigValue<String> POSTGRES_USER;
    public static final ModConfigSpec.ConfigValue<String> POSTGRES_PASSWORD;
    public static final ModConfigSpec.ConfigValue<String> API_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> API_INTERNAL_KEY;
    public static final ModConfigSpec.IntValue API_POLL_INTERVAL_SECONDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("storage");
        BACKEND = b.comment("Which storage backend to use: sqlite | postgres | api")
                .define("backend", "sqlite");

        b.push("sqlite");
        SQLITE_FILE = b.comment("Absolute path to the SQLite file. Empty = <world>/teamsmod/teams.db")
                .define("file", "");
        b.pop();

        b.push("postgres");
        POSTGRES_URL = b.define("url", "jdbc:postgresql://localhost:5432/teams");
        POSTGRES_USER = b.define("user", "");
        POSTGRES_PASSWORD = b.define("password", "");
        b.pop();

        b.push("api");
        API_BASE_URL = b.define("base_url", "http://localhost:3000");
        API_INTERNAL_KEY = b.define("internal_key", "");
        API_POLL_INTERVAL_SECONDS = b.comment("Polling interval for the HTTP backend's in-memory cache.")
                .defineInRange("poll_interval_seconds", 3, 1, 3600);
        b.pop();

        b.pop();
        SPEC = b.build();
    }

    private StorageConfig() {}
}
