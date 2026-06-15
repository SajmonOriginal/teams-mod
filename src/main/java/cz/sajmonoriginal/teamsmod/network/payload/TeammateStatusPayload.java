package cz.sajmonoriginal.teamsmod.network.payload;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server -> Client. Periodic HP / food snapshot of all teammates of the recipient. */
public record TeammateStatusPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<TeammateStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TeamsMod.MOD_ID, "teammate_status"));

    public record Entry(UUID uuid, String name, float health, float maxHealth,
                        int food, float saturation, String dimension) {
        public static final StreamCodec<FriendlyByteBuf, Entry> CODEC = StreamCodec.of(
                (buf, e) -> {
                    buf.writeUUID(e.uuid);
                    buf.writeUtf(e.name);
                    buf.writeFloat(e.health);
                    buf.writeFloat(e.maxHealth);
                    buf.writeVarInt(e.food);
                    buf.writeFloat(e.saturation);
                    buf.writeUtf(e.dimension);
                },
                buf -> new Entry(buf.readUUID(), buf.readUtf(),
                        buf.readFloat(), buf.readFloat(),
                        buf.readVarInt(), buf.readFloat(), buf.readUtf()));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TeammateStatusPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entries.size());
                for (Entry e : p.entries) Entry.CODEC.encode(buf, e);
            },
            buf -> {
                int n = buf.readVarInt();
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(Entry.CODEC.decode(buf));
                return new TeammateStatusPayload(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, List<Entry> entries) {
        PacketDistributor.sendToPlayer(player, new TeammateStatusPayload(entries));
    }
}
