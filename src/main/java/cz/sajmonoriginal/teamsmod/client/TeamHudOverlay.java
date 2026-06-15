package cz.sajmonoriginal.teamsmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Top-left HUD layer listing each online teammate with their face, name,
 * health and food. Mirrors the screenshot the user attached.
 */
public final class TeamHudOverlay implements LayeredDraw.Layer {

    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation HEART_EMPTY = ResourceLocation.withDefaultNamespace("hud/heart/empty");
    private static final ResourceLocation FOOD = ResourceLocation.withDefaultNamespace("hud/food_full");

    private static final int FACE_SIZE = 16;
    private static final int ROW_HEIGHT = 26;
    private static final int LEFT_PAD = 4;
    private static final int TOP_PAD = 4;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.player == null) return;
        if (!ClientTeamData.INSTANCE.inTeam()) return;
        if (!HudConfig.isEnabled()) return;

        var members = ClientTeamData.INSTANCE.members();
        UUID self = mc.player.getUUID();
        int x = LEFT_PAD;
        int y = TOP_PAD;

        for (var member : members) {
            if (member.uuid().equals(self)) continue;
            ClientTeamData.Status status = ClientTeamData.INSTANCE.status(member.uuid());
            if (status == null) continue; // not online / no data yet

            renderRow(graphics, x, y, member, status);
            y += ROW_HEIGHT;
        }
    }

    private void renderRow(GuiGraphics graphics, int x, int y, ClientTeamData.Member member, ClientTeamData.Status status) {
        Minecraft mc = Minecraft.getInstance();

        // Face
        PlayerSkin skin = lookupSkin(member.uuid(), member.name());
        PlayerFaceRenderer.draw(graphics, skin, x, y, FACE_SIZE);

        // Name
        Component name = Component.literal(member.name());
        graphics.drawString(mc.font, name, x + FACE_SIZE + 4, y, 0xFFFFFFFF, true);

        // Hearts
        int barY = y + 10;
        int barX = x + FACE_SIZE + 4;
        renderHearts(graphics, barX, barY, status.health(), status.maxHealth());

        // Food
        int foodX = barX + 28;
        graphics.blitSprite(FOOD, foodX, barY, 9, 9);
        graphics.drawString(mc.font, String.valueOf(status.food()), foodX + 11, barY + 1, 0xFFFFFFFF, true);
    }

    private void renderHearts(GuiGraphics graphics, int x, int y, float hp, float maxHp) {
        graphics.blitSprite(HEART_FULL, x, y, 9, 9);
        int shown = Math.max(0, Math.round(hp));
        graphics.drawString(Minecraft.getInstance().font, String.valueOf(shown), x + 11, y + 1, 0xFFFFFFFF, true);
    }

    private PlayerSkin lookupSkin(UUID uuid, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(uuid);
    }
}
