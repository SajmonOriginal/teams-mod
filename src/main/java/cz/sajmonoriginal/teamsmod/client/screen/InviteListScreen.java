package cz.sajmonoriginal.teamsmod.client.screen;

import cz.sajmonoriginal.teamsmod.client.ClientTeamData;
import cz.sajmonoriginal.teamsmod.network.payload.InviteResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InviteListScreen extends Screen implements Refreshable {

    private static final int PANEL_W = 320;
    private static final int HEADER_H = 26;

    private final Screen parent;
    private int panelX, panelY, panelH;

    public InviteListScreen(Screen parent) {
        super(Component.translatable("teamsmod.gui.invites"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelH = Math.min(this.height - 40, 240);
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - panelH) / 2;

        int rowX = panelX + 12;
        int y = panelY + HEADER_H + 12;
        var invites = ClientTeamData.INSTANCE.invites();
        for (var inv : invites) {
            // Row: team-name label + accept + decline buttons
            this.addRenderableWidget(Button.builder(
                            Component.literal(inv.teamName() + "  ←  " + inv.inviterName())
                                    .withStyle(ChatFormatting.WHITE),
                            b -> {})
                    .bounds(rowX, y, 160, 20).build());
            this.addRenderableWidget(Button.builder(
                            Component.translatable("teamsmod.gui.accept").withStyle(ChatFormatting.GREEN),
                            b -> {
                                PacketDistributor.sendToServer(new InviteResponsePayload(inv.teamId(), true));
                                ClientTeamData.INSTANCE.invites().removeIf(i -> i.teamId() == inv.teamId());
                                this.rebuildWidgets();
                            })
                    .bounds(rowX + 168, y, 60, 20).build());
            this.addRenderableWidget(Button.builder(
                            Component.translatable("teamsmod.gui.decline").withStyle(ChatFormatting.RED),
                            b -> {
                                PacketDistributor.sendToServer(new InviteResponsePayload(inv.teamId(), false));
                                ClientTeamData.INSTANCE.invites().removeIf(i -> i.teamId() == inv.teamId());
                                this.rebuildWidgets();
                            })
                    .bounds(rowX + 232, y, 64, 20).build());
            y += 24;
        }

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.back"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(panelX + PANEL_W / 2 - 60, panelY + panelH - 28, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);

        Panels.drawPanel(g, panelX, panelY, PANEL_W, panelH);

        Panels.drawCenteredText(g, this.font, this.title,
                panelX + PANEL_W / 2, panelY + 7, Panels.FONT_COLOR);

        if (ClientTeamData.INSTANCE.invites().isEmpty()) {
            Panels.drawCenteredText(g, this.font,
                    Component.translatable("teamsmod.invites.none").withStyle(ChatFormatting.ITALIC),
                    panelX + PANEL_W / 2, panelY + panelH / 2 - 4, 0xFF707070);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void refresh() { this.rebuildWidgets(); }
}
