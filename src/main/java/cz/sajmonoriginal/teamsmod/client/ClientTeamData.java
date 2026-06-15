package cz.sajmonoriginal.teamsmod.client;

import cz.sajmonoriginal.teamsmod.client.screen.Refreshable;
import cz.sajmonoriginal.teamsmod.client.screen.TeamMenuScreen;
import cz.sajmonoriginal.teamsmod.network.payload.InviteNotifyPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamListSyncPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeamSyncPayload;
import cz.sajmonoriginal.teamsmod.network.payload.TeammateStatusPayload;
import cz.sajmonoriginal.teamsmod.team.ChatChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative cache of team state on the client. Updated only via packets;
 * never mutated based on local guesses.
 */
public final class ClientTeamData {

    public static final ClientTeamData INSTANCE = new ClientTeamData();

    private boolean inTeam;
    private int teamId;
    private String teamName = "";
    private String teamDescription = "";
    private UUID owner;
    private final List<Member> members = new ArrayList<>();
    private final Map<UUID, Status> statuses = new LinkedHashMap<>();
    private ChatChannel channel = ChatChannel.ALL;
    private final List<Invite> invites = new ArrayList<>();
    private final List<TeamListSyncPayload.Entry> allTeams = new ArrayList<>();

    private ClientTeamData() {}

    public boolean inTeam() { return inTeam; }
    public int teamId() { return teamId; }
    public String teamName() { return teamName; }
    public String teamDescription() { return teamDescription; }
    public UUID owner() { return owner; }
    public List<Member> members() { return members; }
    public ChatChannel channel() { return channel; }
    public List<Invite> invites() { return invites; }
    public List<TeamListSyncPayload.Entry> allTeams() { return allTeams; }

    public boolean isOwner() {
        Minecraft mc = Minecraft.getInstance();
        return inTeam && mc.player != null && mc.player.getUUID().equals(owner);
    }

    public Status status(UUID uuid) {
        return statuses.get(uuid);
    }

    public void setChannel(ChatChannel ch) {
        this.channel = ch;
    }

    public void applySync(TeamSyncPayload payload) {
        // Capture the previous member roster so we can detect joiners.
        java.util.Set<UUID> previousMembers = new java.util.HashSet<>();
        for (Member m : members) previousMembers.add(m.uuid());
        boolean wasInTeam = inTeam;
        int previousTeamId = teamId;

        members.clear();
        if (payload.teamId() == -1) {
            inTeam = false;
            teamId = -1;
            teamName = "";
            teamDescription = "";
            owner = null;
            channel = ChatChannel.byOrdinal(payload.channelOrdinal());
            statuses.clear();
            refreshActiveScreen();
            return;
        }
        inTeam = true;
        teamId = payload.teamId();
        teamName = payload.name();
        teamDescription = payload.description();
        owner = payload.owner();
        channel = ChatChannel.byOrdinal(payload.channelOrdinal());
        for (TeamSyncPayload.MemberEntry me : payload.members()) {
            members.add(new Member(me.uuid(), me.name(), me.roleOrdinal()));
        }
        statuses.keySet().retainAll(members.stream().map(Member::uuid).toList());
        // Once you're in a team you can't have outstanding invites - wipe stale ones.
        invites.clear();

        // Toast: any teammate UUID that wasn't in the previous roster (and isn't us)
        // is a new joiner. Skip if we just changed teams entirely (different teamId).
        if (wasInTeam && previousTeamId == teamId) {
            Minecraft mc = Minecraft.getInstance();
            UUID self = mc.player != null ? mc.player.getUUID() : null;
            for (Member m : members) {
                if (previousMembers.contains(m.uuid())) continue;
                if (self != null && self.equals(m.uuid())) continue;
                showToast(
                        Component.translatable("teamsmod.toast.member_joined_title").withStyle(ChatFormatting.GREEN),
                        Component.translatable("teamsmod.toast.member_joined_desc", m.name(), teamName));
            }
        }

        refreshActiveScreen();
    }

    private static void showToast(Component title, Component description) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getToasts() == null) return;
        SystemToast.add(
                mc.getToasts(),
                new SystemToast.SystemToastId(5000L),
                title,
                description);
    }

    public void onInviteRemoved(int teamId) {
        boolean changed = invites.removeIf(i -> i.teamId() == teamId);
        if (changed) refreshActiveScreen();
    }

    public void applyTeamList(TeamListSyncPayload payload) {
        allTeams.clear();
        allTeams.addAll(payload.entries());
        refreshActiveScreen();
    }

    /** If one of our screens is currently shown, force a re-init so it reflects fresh data. */
    private static void refreshActiveScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof Refreshable r) r.refresh();
    }

    public void applyStatus(TeammateStatusPayload payload) {
        statuses.clear();
        for (TeammateStatusPayload.Entry e : payload.entries()) {
            statuses.put(e.uuid(), new Status(e.name(), e.health(), e.maxHealth(),
                    e.food(), e.saturation(), e.dimension()));
        }
    }

    public void onInvite(InviteNotifyPayload payload) {
        invites.removeIf(i -> i.teamId() == payload.teamId());
        invites.add(new Invite(payload.teamId(), payload.teamName(), payload.inviterName()));
        refreshActiveScreen();

        // The toast is the only invite notification now - slash commands are
        // disabled, so the old chat message with /team accept and /team decline
        // click-events would lead nowhere. Players accept / decline via the
        // invites screen reachable from the team menu's invites banner.
        showToast(
                Component.translatable("teamsmod.toast.invite_title").withStyle(ChatFormatting.YELLOW),
                Component.translatable("teamsmod.toast.invite_desc", payload.inviterName(), payload.teamName()));
    }

    public void openTeamMenu() {
        Minecraft.getInstance().setScreen(new TeamMenuScreen());
    }

    public Optional<Member> findMember(UUID uuid) {
        for (Member m : members) if (m.uuid().equals(uuid)) return Optional.of(m);
        return Optional.empty();
    }

    public record Member(UUID uuid, String name, int roleOrdinal) {
        public boolean isOwner() { return roleOrdinal == 0; }
    }

    public record Status(String name, float health, float maxHealth, int food, float saturation, String dimension) {}

    public record Invite(int teamId, String teamName, String inviterName) {}
}
