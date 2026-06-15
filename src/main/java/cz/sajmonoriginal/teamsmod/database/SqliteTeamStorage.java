package cz.sajmonoriginal.teamsmod.database;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import cz.sajmonoriginal.teamsmod.team.Team;
import cz.sajmonoriginal.teamsmod.team.TeamMember;

import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.UUID;

/**
 * SQLite store for teams. Schema is UUID-keyed so external tooling (a web
 * dashboard, a whitelist DB) can join against it without going through the mod.
 */
public final class SqliteTeamStorage implements TeamStorage {

    private final Connection conn;

    private SqliteTeamStorage(Connection conn) {
        this.conn = conn;
    }

    public static SqliteTeamStorage open(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + file.toAbsolutePath();
            Connection c = DriverManager.getConnection(url);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
            SqliteTeamStorage db = new SqliteTeamStorage(c);
            db.migrate();
            return db;
        } catch (Exception e) {
            throw new RuntimeException("Failed to open teamsmod SQLite at " + file, e);
        }
    }

    private void migrate() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS teams (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    name        TEXT    NOT NULL UNIQUE,
                    description TEXT    NOT NULL DEFAULT '',
                    owner_uuid  TEXT    NOT NULL,
                    created_at  INTEGER NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS team_members (
                    team_id     INTEGER NOT NULL,
                    uuid        TEXT    NOT NULL,
                    name        TEXT    NOT NULL,
                    role        TEXT    NOT NULL,
                    joined_at   INTEGER NOT NULL,
                    PRIMARY KEY (team_id, uuid),
                    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
                )
            """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_team_members_uuid ON team_members(uuid)");
        }

        // Migrate older schema where the column was called "tag".
        boolean hasTag = false;
        boolean hasDescription = false;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(teams)")) {
            while (rs.next()) {
                String col = rs.getString("name");
                if ("tag".equalsIgnoreCase(col)) hasTag = true;
                if ("description".equalsIgnoreCase(col)) hasDescription = true;
            }
        }
        if (hasTag && !hasDescription) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE teams RENAME COLUMN tag TO description");
                TeamsMod.LOG.info("Migrated teams.tag to teams.description");
            }
        } else if (hasTag && hasDescription) {
            try (Statement st = conn.createStatement()) {
                st.execute("UPDATE teams SET description = COALESCE(NULLIF(description, ''), tag)");
                st.execute("ALTER TABLE teams DROP COLUMN tag");
                TeamsMod.LOG.info("Dropped redundant teams.tag column");
            }
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
                ON CONFLICT(team_id, uuid) DO UPDATE SET name=excluded.name, role=excluded.role
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
            TeamsMod.LOG.error("Failed to close SqliteTeamStorage", e);
        }
    }
}
