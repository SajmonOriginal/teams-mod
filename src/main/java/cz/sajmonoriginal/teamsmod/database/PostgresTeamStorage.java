package cz.sajmonoriginal.teamsmod.database;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.team.Team;
import cz.sajmonoriginal.teamsmod.team.TeamMember;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * PostgreSQL store for teams. Schema mirrors {@link SqliteTeamStorage} 1:1 so
 * a mod operator can move between the two by dumping and reloading. The mod is
 * self-sufficient on top of this backend, .
 */
public final class PostgresTeamStorage implements TeamStorage {

    private final Connection conn;

    private PostgresTeamStorage(Connection conn) {
        this.conn = conn;
    }

    public static PostgresTeamStorage open(String url, String user, String password) {
        try {
            Class.forName("org.postgresql.Driver");
            Properties props = new Properties();
            if (user != null && !user.isEmpty()) props.setProperty("user", user);
            if (password != null && !password.isEmpty()) props.setProperty("password", password);
            Connection c = DriverManager.getConnection(url, props);
            PostgresTeamStorage db = new PostgresTeamStorage(c);
            db.migrate();
            return db;
        } catch (Exception e) {
            throw new RuntimeException("Failed to open teamsmod Postgres at " + url, e);
        }
    }

    private void migrate() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS teams (
                    id          SERIAL PRIMARY KEY,
                    name        TEXT    NOT NULL UNIQUE,
                    description TEXT    NOT NULL DEFAULT '',
                    owner_uuid  TEXT    NOT NULL,
                    created_at  BIGINT  NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS team_members (
                    team_id     INTEGER NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
                    uuid        TEXT    NOT NULL,
                    name        TEXT    NOT NULL,
                    role        TEXT    NOT NULL,
                    joined_at   BIGINT  NOT NULL,
                    PRIMARY KEY (team_id, uuid)
                )
            """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_team_members_uuid ON team_members(uuid)");
        }
    }

    @Override
    public synchronized List<Team> loadAllTeams() {
        Map<Integer, Team> map = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, description, owner_uuid, created_at FROM teams")) {
            while (rs.next()) {
                Team t = new Team(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getLong("created_at"));
                map.put(t.id(), t);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load teams", e);
        }

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT team_id, uuid, name, role, joined_at FROM team_members")) {
            while (rs.next()) {
                Team t = map.get(rs.getInt("team_id"));
                if (t == null) continue;
                t.addMember(new TeamMember(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        TeamMember.Role.valueOf(rs.getString("role")),
                        rs.getLong("joined_at")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load team members", e);
        }

        return new ArrayList<>(map.values());
    }

    @Override
    public synchronized int insertTeam(String name, String description, UUID owner, long createdAt) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teams (name, description, owner_uuid, created_at) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description == null ? "" : description);
            ps.setString(3, owner.toString());
            ps.setLong(4, createdAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            TeamsMod.LOG.error("Failed to insert team {}", name, e);
        }
        return -1;
    }

    @Override
    public synchronized void deleteTeam(int teamId) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM teams WHERE id = ?")) {
            ps.setInt(1, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            TeamsMod.LOG.error("Failed to delete team {}", teamId, e);
        }
    }

    @Override
    public synchronized void upsertMember(int teamId, TeamMember member) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO team_members (team_id, uuid, name, role, joined_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (team_id, uuid) DO UPDATE SET name = EXCLUDED.name, role = EXCLUDED.role
                """)) {
            ps.setInt(1, teamId);
            ps.setString(2, member.uuid().toString());
            ps.setString(3, member.name());
            ps.setString(4, member.role().name());
            ps.setLong(5, member.joinedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            TeamsMod.LOG.error("Failed to upsert team member", e);
        }
    }

    @Override
    public synchronized void removeMember(int teamId, UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM team_members WHERE team_id = ? AND uuid = ?")) {
            ps.setInt(1, teamId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            TeamsMod.LOG.error("Failed to remove team member", e);
        }
    }

    @Override
    public synchronized void updateOwner(int teamId, UUID newOwner) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE teams SET owner_uuid = ? WHERE id = ?")) {
            ps.setString(1, newOwner.toString());
            ps.setInt(2, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            TeamsMod.LOG.error("Failed to update owner", e);
        }
    }

    @Override
    public synchronized void renameTeam(int teamId, String name, String description) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE teams SET name = ?, description = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, description == null ? "" : description);
            ps.setInt(3, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            TeamsMod.LOG.error("Failed to rename team", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            TeamsMod.LOG.error("Failed to close PostgresTeamStorage", e);
        }
    }
}
