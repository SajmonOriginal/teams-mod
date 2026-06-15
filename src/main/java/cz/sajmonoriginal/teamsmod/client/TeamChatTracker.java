package cz.sajmonoriginal.teamsmod.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Records the {@code Gui#getGuiTicks()} timestamp at which each team-chat
 * line was handed off to {@code ChatComponent.addMessage}. The vanilla
 * {@code GuiMessage.Line} carries that same {@code addedTime}, so the
 * sidebar layer can match a visible line back to a team-chat event without
 * relying on {@code GuiMessageTag} being preserved through the addMessage
 * pipeline (something in the chat pipeline strips it before our layer can
 * see it).
 *
 * <p>Stale entries get pruned automatically - chat fades out after roughly
 * 200 ticks anyway.
 */
public final class TeamChatTracker {

    private static final Set<Integer> teamChatTimes = ConcurrentHashMap.newKeySet();
    private static final int RETAIN_TICKS = 600; // 30 s - well past chat fade-out

    private TeamChatTracker() {}

    public static void markTeamChat(int guiTicks) {
        teamChatTimes.add(guiTicks);
        teamChatTimes.removeIf(t -> guiTicks - t > RETAIN_TICKS);
    }

    public static boolean isTeamChat(int addedTime) {
        return teamChatTimes.contains(addedTime);
    }
}
