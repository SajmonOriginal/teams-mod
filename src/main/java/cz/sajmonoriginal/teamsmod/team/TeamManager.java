package cz.sajmonoriginal.teamsmod.team;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.database.TeamStorage;
import cz.sajmonoriginal.teamsmod.database.TeamStorageFactory;
import cz.sajmonoriginal.teamsmod.network.payload.InviteNotifyPayload;
import cz.sajmonoriginal.teamsmod.network.payload.InviteRemovedPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamListSyncPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamSyncPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeammateStatusPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Server-side authority for everything team-related. Holds the in-memory
 * cache, persists to the SQLite store, and pushes updates to clients.
 */
public final class TeamManager {

    private static volatile TeamManager INSTANCE;

    private final MinecraftServer server;
    private final TeamStorage db;

    private final Map<Integer, Team> teamsById = new HashMap<>();
    private final Map<UUID, Integer> playerTeam = new HashMap<>();
    private final Map<UUID, List<TeamInvite>> invitesByInvitee = new HashMap<>();
    private final Map<UUID, ChatChannel> activeChannel = new ConcurrentHashMap<>();

    private int statusBroadcastCounter;
    private int reconcileCounter;
    /** How often we re-read the DB to pick up external (web-app) changes. */
    private static final int RECONCILE_INTERVAL_TICKS = 60; // 3 seconds

    private TeamManager(MinecraftServer server, TeamStorage db) {
        this.server = server;
        this.db = db;
    }

    public static synchronized void init(MinecraftServer server) {
        if (INSTANCE != null) return;
        TeamStorage db = TeamStorageFactory.open(server);
        TeamManager mgr = new TeamManager(server, db);
        for (Team t : db.loadAllTeams()) {
            mgr.teamsById.put(t.id(), t);
            for (TeamMember m : t.members()) mgr.playerTeam.put(m.uuid(), t.id());
        }
        INSTANCE = mgr;
        TeamsMod.LOG.info("TeamManager initialized with {} team(s)", mgr.teamsById.size());
    }

    public static synchronized void shutdown() {
        if (INSTANCE != null) {
            INSTANCE.db.close();
            INSTANCE = null;
        }
    }

    public static TeamManager get() {
        TeamManager mgr = INSTANCE;
        if (mgr == null) throw new IllegalStateException("TeamManager not initialized");
        return mgr;
    }

    public static TeamManager getOrNull() {
        return INSTANCE;
    }

    public MinecraftServer server() { return server; }

    public Optional<Team> teamOf(UUID player) {
        Integer id = playerTeam.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(teamsById.get(id));
    }

    public Optional<Team> teamByName(String name) {
        for (Team t : teamsById.values()) {
            if (t.name().equalsIgnoreCase(name)) return Optional.of(t);
        }
        return Optional.empty();
    }

    public Optional<Team> teamById(int id) {
        return Optional.ofNullable(teamsById.get(id));
    }

    public ChatChannel channelOf(UUID player) {
        return activeChannel.getOrDefault(player, ChatChannel.ALL);
    }

    public void setChannel(UUID player, ChatChannel channel) {
        activeChannel.put(player, channel);
    }

    public List<TeamInvite> invitesFor(UUID invitee) {
        return invitesByInvitee.getOrDefault(invitee, List.of());
    }

    // ----- Mutating actions -----

    public CreateResult createTeam(ServerPlayer owner, String name, String description) {
        if (name.isBlank() || name.length() > 24) return CreateResult.fail("teamsmod.error.bad_name");
        String desc = description == null ? "" : description.trim();
        if (desc.length() > 64) return CreateResult.fail("teamsmod.error.bad_description");
        if (teamOf(owner.getUUID()).isPresent()) return CreateResult.fail("teamsmod.error.already_in_team");
        if (teamByName(name).isPresent()) return CreateResult.fail("teamsmod.error.name_taken");

        long now = System.currentTimeMillis();
        int id = db.insertTeam(name, desc, owner.getUUID(), now);
        if (id < 0) return CreateResult.fail("teamsmod.error.db");

        Team t = new Team(id, name, desc, owner.getUUID(), now);
        TeamMember leader = new TeamMember(owner.getUUID(), owner.getGameProfile().getName(), TeamMember.Role.OWNER, now);
        t.addMember(leader);
        db.upsertMember(id, leader);

        teamsById.put(id, t);
        playerTeam.put(owner.getUUID(), id);

        broadcastTeamSync(t);
        broadcastTeamListToAll();
        return CreateResult.ok(t);
    }

    public ActionResult invite(ServerPlayer inviter, ServerPlayer invitee) {
        Optional<Team> teamOpt = teamOf(inviter.getUUID());
        if (teamOpt.isEmpty()) return ActionResult.fail("teamsmod.error.not_in_team");
        Team team = teamOpt.get();
        if (!team.owner().equals(inviter.getUUID())) return ActionResult.fail("teamsmod.error.not_owner");
        if (team.hasMember(invitee.getUUID())) return ActionResult.fail("teamsmod.error.already_member");
        if (teamOf(invitee.getUUID()).isPresent()) return ActionResult.fail("teamsmod.error.invitee_in_team");

        TeamInvite inv = new TeamInvite(team.id(), team.name(), invitee.getUUID(), inviter.getUUID(),
                inviter.getGameProfile().getName(), System.currentTimeMillis());
        invitesByInvitee.computeIfAbsent(invitee.getUUID(), k -> new ArrayList<>()).add(inv);

        InviteNotifyPayload.send(invitee, inv);
        return ActionResult.ok();
    }

    public ActionResult acceptInvite(ServerPlayer player, int teamId) {
        if (teamOf(player.getUUID()).isPresent()) return ActionResult.fail("teamsmod.error.already_in_team");
        List<TeamInvite> invites = invitesByInvitee.get(player.getUUID());
        if (invites == null) return ActionResult.fail("teamsmod.error.no_invite");

        TeamInvite found = null;
        for (TeamInvite inv : invites) {
            if (inv.teamId() == teamId) { found = inv; break; }
        }
        if (found == null) return ActionResult.fail("teamsmod.error.no_invite");

        Team team = teamsById.get(teamId);
        if (team == null) {
            invites.remove(found);
            return ActionResult.fail("teamsmod.error.team_gone");
        }

        TeamMember member = new TeamMember(player.getUUID(), player.getGameProfile().getName(),
                TeamMember.Role.MEMBER, System.currentTimeMillis());
        team.addMember(member);
        db.upsertMember(team.id(), member);
        playerTeam.put(player.getUUID(), team.id());
        invitesByInvitee.remove(player.getUUID());

        // Tell the client to drop every invite it had cached locally.
        InviteRemovedPayload.send(player, teamId);

        broadcastTeamSync(team);
        broadcastTeamListToAll();
        return ActionResult.ok();
    }

    public ActionResult declineInvite(ServerPlayer player, int teamId) {
        List<TeamInvite> invites = invitesByInvitee.get(player.getUUID());
        if (invites == null) return ActionResult.fail("teamsmod.error.no_invite");
        boolean removed = invites.removeIf(i -> i.teamId() == teamId);
        if (invites.isEmpty()) invitesByInvitee.remove(player.getUUID());
        if (removed) InviteRemovedPayload.send(player, teamId);
        return removed ? ActionResult.ok() : ActionResult.fail("teamsmod.error.no_invite");
    }

    public ActionResult leave(ServerPlayer player) {
        Optional<Team> teamOpt = teamOf(player.getUUID());
        if (teamOpt.isEmpty()) return ActionResult.fail("teamsmod.error.not_in_team");
        Team team = teamOpt.get();
        boolean wasOwner = team.owner().equals(player.getUUID());

        team.removeMember(player.getUUID());
        db.removeMember(team.id(), player.getUUID());
        playerTeam.remove(player.getUUID());

        if (team.size() == 0) {
            teamsById.remove(team.id());
            db.deleteTeam(team.id());
        } else if (wasOwner) {
            TeamMember newOwner = team.members().get(0);
            team.setOwner(newOwner.uuid());
            db.updateOwner(team.id(), newOwner.uuid());
            TeamMember promoted = new TeamMember(newOwner.uuid(), newOwner.name(), TeamMember.Role.OWNER, newOwner.joinedAt());
            team.addMember(promoted);
            db.upsertMember(team.id(), promoted);
            broadcastTeamSync(team);
        } else {
            broadcastTeamSync(team);
        }

        // Tell the leaver to clear their client state.
        TeamSyncPayload.sendEmpty(player);
        broadcastTeamListToAll();
        return ActionResult.ok();
    }

    public ActionResult kick(ServerPlayer requester, UUID target) {
        Optional<Team> teamOpt = teamOf(requester.getUUID());
        if (teamOpt.isEmpty()) return ActionResult.fail("teamsmod.error.not_in_team");
        Team team = teamOpt.get();
        if (!team.owner().equals(requester.getUUID())) return ActionResult.fail("teamsmod.error.not_owner");
        if (target.equals(requester.getUUID())) return ActionResult.fail("teamsmod.error.cannot_kick_self");
        if (!team.hasMember(target)) return ActionResult.fail("teamsmod.error.not_member");

        team.removeMember(target);
        db.removeMember(team.id(), target);
        playerTeam.remove(target);

        ServerPlayer kicked = server.getPlayerList().getPlayer(target);
        if (kicked != null) TeamSyncPayload.sendEmpty(kicked);

        broadcastTeamSync(team);
        broadcastTeamListToAll();
        return ActionResult.ok();
    }

    // ----- Player lifecycle -----

    public void onPlayerJoin(ServerPlayer player) {
        Optional<Team> team = teamOf(player.getUUID());
        if (team.isPresent()) {
            TeamSyncPayload.send(player, team.get(), channelOf(player.getUUID()));
        } else {
            TeamSyncPayload.sendEmpty(player);
        }
        TeamListSyncPayload.send(player, buildTeamListEntries());
        // Re-deliver pending invites.
        for (TeamInvite inv : invitesFor(player.getUUID())) {
            InviteNotifyPayload.send(player, inv);
        }
    }

    public void onPlayerLeave(ServerPlayer player) {
        activeChannel.remove(player.getUUID());
    }

    // ----- Tick: clean expired invites + broadcast statuses -----

    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, List<TeamInvite>>> it = invitesByInvitee.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<TeamInvite>> e = it.next();
            e.getValue().removeIf(inv -> inv.isExpired(now));
            if (e.getValue().isEmpty()) it.remove();
        }

        if (++statusBroadcastCounter >= 20) {
            statusBroadcastCounter = 0;
            broadcastStatuses();
        }

        if (++reconcileCounter >= RECONCILE_INTERVAL_TICKS) {
            reconcileCounter = 0;
            try {
                reconcileFromDatabase();
            } catch (Exception e) {
                TeamsMod.LOG.error("[teamsmod] DB reconciliation failed; will retry next tick", e);
            }
        }
    }

    /**
     * Re-reads the SQLite store and applies any externally-made changes
     * (typically from the companion web app). Sends targeted {@link TeamSyncPayload}
     * updates to affected players and a fresh {@link TeamListSyncPayload} to
     * everyone if the directory changed.
     *
     * <p>Concurrency: SQLite WAL means readers don't block external writers.
     * We don't try to second-guess conflicts - DB is authoritative for
     * (id, name, description, owner, members). Mod-only state (active chat
     * channel, pending invites) lives in memory and is not affected.
     */
    public synchronized void reconcileFromDatabase() {
        List<Team> dbTeams = db.loadAllTeams();
        Map<Integer, Team> dbById = new HashMap<>();
        for (Team t : dbTeams) dbById.put(t.id(), t);

        Set<UUID> playersToSync = new HashSet<>();
        boolean changed = false;

        // Pass 1 - additions + in-place updates
        for (Team dbTeam : dbTeams) {
            Team memTeam = teamsById.get(dbTeam.id());

            if (memTeam == null) {
                // Brand-new team appeared in the DB.
                teamsById.put(dbTeam.id(), dbTeam);
                for (TeamMember m : dbTeam.members()) {
                    playerTeam.put(m.uuid(), dbTeam.id());
                    playersToSync.add(m.uuid());
                    invitesByInvitee.remove(m.uuid()); // can't be invited if you're already in a team
                }
                TeamsMod.LOG.info("[teamsmod] reconcile: ADDED team '{}' (id={})", dbTeam.name(), dbTeam.id());
                changed = true;
                continue;
            }

            boolean teamChanged = false;
            if (!Objects.equals(memTeam.name(), dbTeam.name())) {
                memTeam.setName(dbTeam.name());
                teamChanged = true;
            }
            if (!Objects.equals(memTeam.description(), dbTeam.description())) {
                memTeam.setDescription(dbTeam.description());
                teamChanged = true;
            }
            if (!memTeam.owner().equals(dbTeam.owner())) {
                memTeam.setOwner(dbTeam.owner());
                teamChanged = true;
            }

            // Member roster diff
            Map<UUID, TeamMember> oldByUuid = new HashMap<>();
            for (TeamMember m : memTeam.members()) oldByUuid.put(m.uuid(), m);
            Map<UUID, TeamMember> newByUuid = new HashMap<>();
            for (TeamMember m : dbTeam.members()) newByUuid.put(m.uuid(), m);

            boolean membersChanged = !oldByUuid.keySet().equals(newByUuid.keySet());
            if (!membersChanged) {
                for (UUID uuid : oldByUuid.keySet()) {
                    TeamMember om = oldByUuid.get(uuid);
                    TeamMember nm = newByUuid.get(uuid);
                    if (om.role() != nm.role() || !Objects.equals(om.name(), nm.name())) {
                        membersChanged = true;
                        break;
                    }
                }
            }

            if (membersChanged) {
                for (UUID uuid : oldByUuid.keySet()) memTeam.removeMember(uuid);
                for (TeamMember m : dbTeam.members()) memTeam.addMember(m);

                for (UUID uuid : oldByUuid.keySet()) {
                    if (!newByUuid.containsKey(uuid)) {
                        playerTeam.remove(uuid);
                        playersToSync.add(uuid);
                    }
                }
                for (UUID uuid : newByUuid.keySet()) {
                    playerTeam.put(uuid, memTeam.id());
                    if (!oldByUuid.containsKey(uuid)) {
                        playersToSync.add(uuid);
                        invitesByInvitee.remove(uuid);
                    }
                }
                teamChanged = true;
            }

            if (teamChanged) {
                // Push fresh sync to every current member so they see the rename / desc / owner change.
                for (TeamMember m : memTeam.members()) playersToSync.add(m.uuid());
                TeamsMod.LOG.info("[teamsmod] reconcile: UPDATED team '{}' (id={})", memTeam.name(), memTeam.id());
                changed = true;
            }
        }

        // Pass 2 - teams that vanished from the DB
        List<Integer> toRemove = new ArrayList<>();
        for (Team memTeam : new ArrayList<>(teamsById.values())) {
            if (!dbById.containsKey(memTeam.id())) {
                toRemove.add(memTeam.id());
                for (TeamMember m : memTeam.members()) {
                    playerTeam.remove(m.uuid());
                    playersToSync.add(m.uuid());
                }
                // Drop pending invites pointing at the now-gone team
                for (List<TeamInvite> invs : invitesByInvitee.values()) {
                    invs.removeIf(inv -> inv.teamId() == memTeam.id());
                }
                TeamsMod.LOG.info("[teamsmod] reconcile: REMOVED team '{}' (id={})", memTeam.name(), memTeam.id());
                changed = true;
            }
        }
        for (int id : toRemove) teamsById.remove(id);

        if (!changed) return;

        // Push targeted TeamSync to every affected online player
        for (UUID uuid : playersToSync) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) continue;
            Optional<Team> t = teamOf(uuid);
            if (t.isPresent()) TeamSyncPayload.send(p, t.get(), channelOf(uuid));
            else TeamSyncPayload.sendEmpty(p);
        }
        // Refresh the full directory for everyone
        broadcastTeamListToAll();
    }

    private void broadcastStatuses() {
        for (Team team : teamsById.values()) {
            List<ServerPlayer> online = team.members().stream()
                    .map(m -> server.getPlayerList().getPlayer(m.uuid()))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            if (online.isEmpty()) continue;
            // Always broadcast, even when only one teammate is online - that
            // recipient gets an empty entries list, which lets their client
            // clear stale HUD entries for teammates that just disconnected.
            for (ServerPlayer recipient : online) {
                List<TeammateStatusPayload.Entry> entries = new ArrayList<>();
                for (ServerPlayer other : online) {
                    if (other.getUUID().equals(recipient.getUUID())) continue;
                    entries.add(new TeammateStatusPayload.Entry(
                            other.getUUID(),
                            other.getGameProfile().getName(),
                            other.getHealth(),
                            other.getMaxHealth(),
                            other.getFoodData().getFoodLevel(),
                            other.getFoodData().getSaturationLevel(),
                            other.level().dimension().location().toString()));
                }
                TeammateStatusPayload.send(recipient, entries);
            }
        }
    }

    public void broadcastTeamSync(Team team) {
        for (TeamMember m : team.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.uuid());
            if (p != null) TeamSyncPayload.send(p, team, channelOf(m.uuid()));
        }
    }

    public void broadcastTeamListToAll() {
        List<TeamListSyncPayload.Entry> entries = buildTeamListEntries();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            TeamListSyncPayload.send(p, entries);
        }
    }

    public List<TeamListSyncPayload.Entry> buildTeamListEntries() {
        List<TeamListSyncPayload.Entry> entries = new ArrayList<>(teamsById.size());
        for (Team t : teamsById.values()) {
            String ownerName = t.ownerMember().map(TeamMember::name).orElse("?");
            List<TeamListSyncPayload.MemberInfo> members = new ArrayList<>(t.size());
            for (TeamMember m : t.members()) {
                members.add(new TeamListSyncPayload.MemberInfo(m.uuid(), m.name(), m.role().ordinal()));
            }
            entries.add(new TeamListSyncPayload.Entry(
                    t.id(), t.name(), t.description(), t.owner(), ownerName, t.size(), members));
        }
        return entries;
    }

    public List<Team> allTeams() {
        return Collections.unmodifiableList(new ArrayList<>(teamsById.values()));
    }

    // ----- Result helpers -----

    public record CreateResult(boolean success, Team team, Component error) {
        public static CreateResult ok(Team t) { return new CreateResult(true, t, null); }
        public static CreateResult fail(String key) { return new CreateResult(false, null, Component.translatable(key)); }
    }

    public record ActionResult(boolean success, Component error) {
        public static ActionResult ok() { return new ActionResult(true, null); }
        public static ActionResult fail(String key) { return new ActionResult(false, Component.translatable(key)); }
    }
}
