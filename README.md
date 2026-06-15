# teams-mod

In-game team system for Minecraft (NeoForge 1.21.1). Players create teams,
invite each other, share a separate team chat channel, and see a HUD with
teammate health and food. State is stored through a pluggable backend so the
mod works on its own (SQLite or PostgreSQL) or against your own HTTP API.

## Install

1. Grab `teamsmod-<version>.jar` from
   [Releases](https://github.com/SajmonOriginal/teams-mod/releases).
2. Drop it into the server's `mods/` directory.
3. Start the server once. It writes a default config to
   `config/teamsmod-server.toml`.
4. Edit the config to pick a backend (see below) and restart.

The client jar is optional. Without it you still get team chat and the
chat-style commands, but no in-game GUI or HUD.

## Backends

Three options. Pick one in `config/teamsmod-server.toml`.

### sqlite (default)

Local file under the world directory. No setup, good for a single server.

### postgres

Connects to a PostgreSQL database you run somewhere else. Use this when more
than one process needs the same team set (multiple servers, a website, a
Discord bot). The mod creates its own tables on first start.

### api

HTTP backend. The mod calls a REST API for every read and write. Use this
when you already have a service that owns team state and you want the mod to
be a thin client. The endpoints and payload shapes the mod hits are listed
in `src/main/java/cz/sajmonoriginal/teamsmod/database/HttpApiTeamStorage.java`.

## Config

```toml
[storage]
backend = "sqlite"  # "sqlite" | "postgres" | "api"

[storage.sqlite]
# leave empty for <world>/teamsmod/teams.db
file = ""

[storage.postgres]
url = "jdbc:postgresql://localhost:5432/teams"
user = ""
password = ""

[storage.api]
base_url = "http://localhost:3000"
internal_key = ""
poll_interval_seconds = 3
```

Missing required values (`postgres.url`, `api.base_url`, `api.internal_key`)
abort startup with an error in the log.

## Build

Needs JDK 21.

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

The output jar is at `build/libs/teamsmod-<version>.jar`. SQLite, PostgreSQL,
and gson are shaded in via NeoForge's `jarJar`, so the jar drops onto a
server without any extra dependencies.

## License

All rights reserved. Free modpacks may include unmodified copies, and
forks are allowed as long as they credit this repository as the source.
Commercial use requires written permission from the copyright holder.
See [LICENSE](LICENSE).
