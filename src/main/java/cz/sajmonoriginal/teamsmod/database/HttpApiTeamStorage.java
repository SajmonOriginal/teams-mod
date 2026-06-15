package cz.sajmonoriginal.teamsmod.database;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.team.Team;
import cz.sajmonoriginal.teamsmod.team.TeamMember;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST backend that talks to a HTTP-based internal API. Writes are guarded by a bearer key
 * and identify the in-game player via the ActorIdentifier discriminated union (mc-uuid).
 *
 * <p>Endpoints the mod hits:
 * <ul>
 *   <li>GET    /internal/teams/changes-since?cursor=N  - polled every poll_interval_seconds</li>
 *   <li>GET    /teams/&lt;name&gt;                                - team detail (members embedded)</li>
 *   <li>POST   /internal/teams                              - create-team (actor=owner)</li>
 *   <li>PATCH  /internal/teams/&lt;name&gt;                       - rename/change description (actor=owner)</li>
 *   <li>PATCH  /internal/teams/&lt;name&gt;/owner                 - transfer ownership (actor=owner, target=new)</li>
 *   <li>DELETE /internal/teams/&lt;name&gt;                       - delete team (actor=owner)</li>
 *   <li>POST   /internal/teams/&lt;name&gt;/members               - add-member (actor=owner, target=member)</li>
 *   <li>DELETE /internal/teams/&lt;name&gt;/members               - remove-member (actor=owner, target=member)</li>
 * </ul>
 *
 * <p>State sync model: a background thread polls /internal/teams/changes-since with the last
 * seen cursor every {@code poll_interval_seconds}. Created/updated teams are refetched and
 * cached; deleted teams are dropped from the cache. {@link #loadAllTeams()} returns a snapshot
 * of the cache and does not block on the network in the common case.
 */
public final class HttpApiTeamStorage implements TeamStorage {

    private final HttpClient http;
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String internalKey;
    private final ScheduledExecutorService poller;
    private final ScheduledFuture<?> pollHandle;

    private final Map<Integer, Team> teamsById = new ConcurrentHashMap<>();
    private final Map<String, Integer> teamIdByName = new ConcurrentHashMap<>();
    private volatile long lastCursor = 0L;
    private volatile boolean initialSyncDone = false;
    private int nextSyntheticId = -1;

    private HttpApiTeamStorage(String baseUrl, String internalKey, long pollIntervalSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalKey = internalKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "teamsmod-http-poller");
            t.setDaemon(true);
            return t;
        });
        // First tick after `pollIntervalSeconds` so the synchronous initial sync below has run.
        long period = Math.max(1L, pollIntervalSeconds);
        this.pollHandle = poller.scheduleAtFixedRate(this::pollChanges, period, period, TimeUnit.SECONDS);
    }

    public static HttpApiTeamStorage open(String baseUrl, String internalKey, long pollIntervalSeconds) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("storage.api.base_url must be set when backend=api");
        }
        if (internalKey == null || internalKey.isBlank()) {
            throw new IllegalArgumentException("storage.api.internal_key must be set when backend=api");
        }
        HttpApiTeamStorage storage = new HttpApiTeamStorage(baseUrl, internalKey, pollIntervalSeconds);
        storage.initialSync();
        return storage;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + internalKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15));
    }

    private JsonObject send(HttpRequest req) throws Exception {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + res.statusCode() + ": " + res.body());
        }
        if (res.body() == null || res.body().isEmpty()) return new JsonObject();
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private JsonObject mcUuidActor(UUID uuid) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "mc-uuid");
        o.addProperty("value", uuid.toString());
        return o;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private void initialSync() {
        try {
            // Walk every change from the start so we know every live team's name.
            Set<String> liveNames = new HashSet<>();
            long cursor = 0L;
            while (true) {
                HttpRequest req = request("/internal/teams/changes-since?cursor=" + cursor + "&limit=500").GET().build();
                JsonObject body = send(req);
                JsonArray changes = body.getAsJsonArray("changes");
                if (changes == null || changes.isEmpty()) break;
                for (JsonElement el : changes) {
                    JsonObject ch = el.getAsJsonObject();
                    long id = ch.get("id").getAsLong();
                    cursor = Math.max(cursor, id);
                    String type = ch.get("type").getAsString();
                    JsonElement payloadEl = ch.get("payload");
                    if (payloadEl == null || !payloadEl.isJsonObject()) continue;
                    JsonObject payload = payloadEl.getAsJsonObject();
                    String name = payload.has("name") && !payload.get("name").isJsonNull()
                            ? payload.get("name").getAsString() : null;
                    if ("team.deleted".equals(type) && name != null) {
                        liveNames.remove(name);
                    } else if (name != null) {
                        liveNames.add(name);
                    }
                }
                JsonElement next = body.get("nextCursor");
                if (next == null || next.isJsonNull()) break;
            }
            for (String name : liveNames) cacheTeam(fetchTeam(name));
            lastCursor = cursor;
            initialSyncDone = true;
        } catch (Exception e) {
            TeamsMod.LOG.error("HttpApiTeamStorage: initial sync failed; cache will populate on next poll", e);
        }
    }

    private void pollChanges() {
        try {
            HttpRequest req = request("/internal/teams/changes-since?cursor=" + lastCursor + "&limit=500").GET().build();
            JsonObject body = send(req);
            JsonArray changes = body.getAsJsonArray("changes");
            if (changes == null) return;
            Set<String> dirtyNames = new HashSet<>();
            Set<String> deleted = new HashSet<>();
            for (JsonElement el : changes) {
                JsonObject ch = el.getAsJsonObject();
                long id = ch.get("id").getAsLong();
                if (id > lastCursor) lastCursor = id;
                String type = ch.get("type").getAsString();
                JsonElement payloadEl = ch.get("payload");
                if (payloadEl == null || !payloadEl.isJsonObject()) continue;
                JsonObject payload = payloadEl.getAsJsonObject();
                String name = payload.has("name") && !payload.get("name").isJsonNull()
                        ? payload.get("name").getAsString() : null;
                if (name == null) continue;
                if ("team.deleted".equals(type)) {
                    deleted.add(name);
                } else {
                    dirtyNames.add(name);
                }
            }
            for (String name : deleted) {
                Integer id = teamIdByName.remove(name);
                if (id != null) teamsById.remove(id);
            }
            for (String name : dirtyNames) {
                try {
                    cacheTeam(fetchTeam(name));
                } catch (Exception e) {
                    TeamsMod.LOG.warn("HttpApiTeamStorage: failed to refresh team {} during poll", name, e);
                }
            }
        } catch (Exception e) {
            TeamsMod.LOG.warn("HttpApiTeamStorage: poll failed; will retry next tick", e);
        }
    }

    private void cacheTeam(Team t) {
        if (t == null) return;
        teamsById.put(t.id(), t);
        teamIdByName.put(t.name(), t.id());
    }

    private Team fetchTeam(String name) throws Exception {
        HttpRequest req = request("/teams/" + urlEncode(name)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) return null;
        if (res.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + res.statusCode() + " fetching team " + name + ": " + res.body());
        }
        return parseTeam(JsonParser.parseString(res.body()).getAsJsonObject());
    }

    private Team parseTeam(JsonObject detail) {
        int id = detail.get("id").getAsInt();
        String name = detail.get("name").getAsString();
        String desc = detail.has("description") && !detail.get("description").isJsonNull()
                ? detail.get("description").getAsString() : "";
        String ownerMcRaw = detail.has("ownerMinecraftUuid") && !detail.get("ownerMinecraftUuid").isJsonNull()
                ? detail.get("ownerMinecraftUuid").getAsString() : null;
        if (ownerMcRaw == null) {
            TeamsMod.LOG.warn("HttpApiTeamStorage: team {} has no ownerMinecraftUuid; skipping", name);
            return null;
        }
        UUID owner = UUID.fromString(ownerMcRaw);
        long createdAt = parseInstantMillis(detail.get("createdAt"));
        Team team = new Team(id, name, desc, owner, createdAt);
        if (detail.has("members") && detail.get("members").isJsonArray()) {
            for (JsonElement el : detail.getAsJsonArray("members")) {
                JsonObject m = el.getAsJsonObject();
                String mcRaw = m.has("minecraftUuid") && !m.get("minecraftUuid").isJsonNull()
                        ? m.get("minecraftUuid").getAsString() : null;
                String mcName = m.has("minecraftName") && !m.get("minecraftName").isJsonNull()
                        ? m.get("minecraftName").getAsString()
                        : (m.has("displayName") ? m.get("displayName").getAsString() : "?");
                if (mcRaw == null) continue;
                TeamMember.Role role = TeamMember.Role.valueOf(m.get("role").getAsString());
                long joinedAt = parseInstantMillis(m.get("joinedAt"));
                team.addMember(new TeamMember(UUID.fromString(mcRaw), mcName, role, joinedAt));
            }
        }
        return team;
    }

    private long parseInstantMillis(JsonElement el) {
        if (el == null || el.isJsonNull()) return System.currentTimeMillis();
        try {
            return java.time.Instant.parse(el.getAsString()).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    @Override
    public synchronized List<Team> loadAllTeams() {
        if (!initialSyncDone) initialSync();
        return new ArrayList<>(teamsById.values());
    }

    @Override
    public synchronized int insertTeam(String name, String description, UUID owner, long createdAt) {
        JsonObject payload = new JsonObject();
        payload.add("actor", mcUuidActor(owner));
        payload.addProperty("name", name);
        payload.addProperty("description", description == null ? "" : description);
        try {
            HttpRequest req = request("/internal/teams")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
                    .build();
            JsonObject body = send(req);
            Team t = parseTeam(body);
            if (t != null) {
                cacheTeam(t);
                return t.id();
            }
            return nextSyntheticId--;
        } catch (Exception e) {
            TeamsMod.LOG.error("HttpApiTeamStorage: failed to insert team {}", name, e);
            return nextSyntheticId--;
        }
    }

    @Override
    public synchronized void deleteTeam(int teamId) {
        Team team = teamsById.get(teamId);
        if (team == null) {
            TeamsMod.LOG.warn("HttpApiTeamStorage: deleteTeam for unknown team id {}; call loadAllTeams first", teamId);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.add("actor", mcUuidActor(team.owner()));
        try {
            HttpRequest req = request("/internal/teams/" + urlEncode(team.name()))
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
                    .build();
            send(req);
            teamsById.remove(teamId);
            teamIdByName.remove(team.name());
        } catch (Exception e) {
            TeamsMod.LOG.error("HttpApiTeamStorage: failed to delete team {}", team.name(), e);
        }
    }

    @Override
    public synchronized void upsertMember(int teamId, TeamMember member) {
        Team team = teamsById.get(teamId);
        if (team == null) {
            TeamsMod.LOG.warn("HttpApiTeamStorage: upsertMember for unknown team id {}; call loadAllTeams first", teamId);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.add("actor", mcUuidActor(team.owner()));
        payload.add("target", mcUuidActor(member.uuid()));
        try {
            HttpRequest req = request("/internal/teams/" + urlEncode(team.name()) + "/members")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
                    .build();
            send(req);
        } catch (Exception e) {
            TeamsMod.LOG.error("HttpApiTeamStorage: failed to add member to team {}", team.name(), e);
        }
    }

    @Override
    public synchronized void removeMember(int teamId, UUID uuid) {
        Team team = teamsById.get(teamId);
        if (team == null) {
            TeamsMod.LOG.warn("HttpApiTeamStorage: removeMember for unknown team id {}; call loadAllTeams first", teamId);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.add("actor", mcUuidActor(team.owner()));
        payload.add("target", mcUuidActor(uuid));
        try {
            HttpRequest req = request("/internal/teams/" + urlEncode(team.name()) + "/members")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
                    .build();
            send(req);
        } catch (Exception e) {
            TeamsMod.LOG.error("HttpApiTeamStorage: failed to remove member {} from team {}", uuid, team.name(), e);
        }
    }

    @Override
    public synchronized void updateOwner(int teamId, UUID newOwner) {
        Team team = teamsById.get(teamId);
        if (team == null) {
            TeamsMod.LOG.warn("HttpApiTeamStorage: updateOwner for unknown team id {}; call loadAllTeams first", teamId);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.add("actor", mcUuidActor(team.owner()));
        payload.add("target", mcUuidActor(newOwner));
        try {
            HttpRequest req = request("/internal/teams/" + urlEncode(team.name()) + "/owner")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
                    .build();
            send(req);
        } catch (Exception e) {
            TeamsMod.LOG.error("HttpApiTeamStorage: failed to transfer ownership of team {}", team.name(), e);
        }
    }

    @Override
    public synchronized void renameTeam(int teamId, String name, String description) {
        Team team = teamsById.get(teamId);
        if (team == null) {
            TeamsMod.LOG.warn("HttpApiTeamStorage: renameTeam for unknown team id {}; call loadAllTeams first", teamId);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.add("actor", mcUuidActor(team.owner()));
        if (name != null && !name.equals(team.name())) payload.addProperty("name", name);
        if (description != null) payload.addProperty("description", description);
        try {
            HttpRequest req = request("/internal/teams/" + urlEncode(team.name()))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(payload), StandardCharsets.UTF_8))
                    .build();
            send(req);
        } catch (Exception e) {
            TeamsMod.LOG.error("HttpApiTeamStorage: failed to rename team {}", team.name(), e);
        }
    }

    @Override
    public synchronized void close() {
        pollHandle.cancel(false);
        poller.shutdownNow();
    }
}
