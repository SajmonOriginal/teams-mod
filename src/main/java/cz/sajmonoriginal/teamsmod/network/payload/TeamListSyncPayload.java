package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client. The full directory of teams on the server, sent to every
 * online player whenever it changes (create / accept / leave / kick) and on
 * player join. Lets the client browse-screen render without a round trip.
 */
public record TeamListSyncPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<TeamListSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "team_list_sync"));

    public record MemberInfo(UUID uuid, String name, int roleOrdinal) {
        public static final StreamCodec<FriendlyByteBuf, MemberInfo> CODEC = StreamCodec.of(
                (buf, m) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, m.uuid);
                    buf.writeUtf(m.name);
                    buf.writeVarInt(m.roleOrdinal);
                },
                buf -> new MemberInfo(
                        UUIDUtil.STREAM_CODEC.decode(buf),
                        buf.readUtf(),
                        buf.readVarInt()));

        public boolean isOwner() { return roleOrdinal == 0; }
    }

    public record Entry(int id, String name, String description, UUID owner, String ownerName,
                        int memberCount, List<MemberInfo> members) {
        public static final StreamCodec<FriendlyByteBuf, Entry> CODEC = StreamCodec.of(
                (buf, e) -> {
                    buf.writeVarInt(e.id);
                    buf.writeUtf(e.name);
                    buf.writeUtf(e.description);
                    UUIDUtil.STREAM_CODEC.encode(buf, e.owner);
                    buf.writeUtf(e.ownerName);
                    buf.writeVarInt(e.memberCount);
                    buf.writeVarInt(e.members.size());
                    for (MemberInfo m : e.members) MemberInfo.CODEC.encode(buf, m);
                },
                buf -> {
                    int id = buf.readVarInt();
                    String name = buf.readUtf();
                    String description = buf.readUtf();
                    UUID owner = UUIDUtil.STREAM_CODEC.decode(buf);
                    String ownerName = buf.readUtf();
                    int memberCount = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<MemberInfo> members = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) members.add(MemberInfo.CODEC.decode(buf));
                    return new Entry(id, name, description, owner, ownerName, memberCount, members);
                });
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamListSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entries.size());
                for (Entry e : p.entries) Entry.CODEC.encode(buf, e);
            },
            buf -> {
                int n = buf.readVarInt();
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(Entry.CODEC.decode(buf));
                return new TeamListSyncPayload(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(ServerPlayer player, List<Entry> entries) {
        PacketDistributor.sendToPlayer(player, new TeamListSyncPayload(entries));
    }
}
