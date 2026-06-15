package cz.sajmonoriginal.teamsmod.team;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public enum ChatChannel {
    ALL("all", 0xFFFFFFFF),
    TEAM("team", 0xFF55FF55);

    private final String id;
    private final int color;

    ChatChannel(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    public ChatChannel toggle() {
        return this == ALL ? TEAM : ALL;
    }

    public MutableComponent badge() {
        return Component.literal("[" + name() + "] ");
    }

    public static ChatChannel byOrdinal(int ord) {
        ChatChannel[] values = values();
        if (ord < 0 || ord >= values.length) return ALL;
        return values[ord];
    }
}
