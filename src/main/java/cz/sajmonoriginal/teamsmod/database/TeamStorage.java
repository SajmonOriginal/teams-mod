package cz.sajmonoriginal.teamsmod.database;

import cz.sajmonoriginal.teamsmod.team.Team;
import cz.sajmonoriginal.teamsmod.team.TeamMember;

import java.io.Closeable;
import java.util.List;
import java.util.UUID;

/**
 * Pluggable persistence contract for the team manager. Implementations may be
 * a local SQLite file, a remote PostgreSQL server, or a REST API.
 */
public interface TeamStorage extends Closeable {

    List<Team> loadAllTeams();

    int insertTeam(String name, String description, UUID owner, long createdAt);

    void deleteTeam(int teamId);

    void upsertMember(int teamId, TeamMember member);

    void removeMember(int teamId, UUID uuid);

    void updateOwner(int teamId, UUID newOwner);

    void renameTeam(int teamId, String name, String description);

    @Override
    void close();
}
