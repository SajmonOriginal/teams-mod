package cz.sajmonoriginal.teamsmod.team;

import java.util.UUID;

public record TeamInvite(int teamId, String teamName, UUID invitee, UUID inviter, String inviterName, long createdAt) {
    public static final long TTL_MILLIS = 1000L * 60L * 5L; // 5 minutes

    public boolean isExpired(long now) {
        return now - createdAt > TTL_MILLIS;
    }
}
