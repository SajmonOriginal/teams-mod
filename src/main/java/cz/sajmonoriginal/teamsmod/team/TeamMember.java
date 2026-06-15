package cz.sajmonoriginal.teamsmod.team;

import java.util.UUID;

public record TeamMember(UUID uuid, String name, Role role, long joinedAt) {
    public enum Role {
        OWNER, MEMBER
    }
}
