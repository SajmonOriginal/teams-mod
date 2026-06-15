package cz.sajmonoriginal.teamsmod.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Team {

    private final int id;
    private String name;
    private String description;
    private UUID owner;
    private final long createdAt;
    private final List<TeamMember> members = new ArrayList<>();

    public Team(int id, String name, String description, UUID owner, long createdAt) {
        this.id = id;
        this.name = name;
        this.description = description == null ? "" : description;
        this.owner = owner;
        this.createdAt = createdAt;
    }

    public int id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public UUID owner() { return owner; }
    public long createdAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }
    public void setOwner(UUID owner) { this.owner = owner; }

    public List<TeamMember> members() {
        return Collections.unmodifiableList(members);
    }

    public Optional<TeamMember> findMember(UUID uuid) {
        for (TeamMember m : members) if (m.uuid().equals(uuid)) return Optional.of(m);
        return Optional.empty();
    }

    public boolean hasMember(UUID uuid) {
        return findMember(uuid).isPresent();
    }

    public void addMember(TeamMember member) {
        members.removeIf(m -> m.uuid().equals(member.uuid()));
        members.add(member);
    }

    public boolean removeMember(UUID uuid) {
        return members.removeIf(m -> m.uuid().equals(uuid));
    }

    public int size() {
        return members.size();
    }

    public Optional<TeamMember> ownerMember() {
        return findMember(owner);
    }
}
