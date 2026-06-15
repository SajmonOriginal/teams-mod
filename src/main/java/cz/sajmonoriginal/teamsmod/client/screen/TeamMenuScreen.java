package cz.sajmonoriginal.teamsmod.client.screen;

import cz.sajmonoriginal.teamsmod.client.ClientTeamData;
import cz.sajmonoriginal.teamsmod.client.HudConfig;
import cz.sajmonoriginal.teamsmod.network.payload.TeamListSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.UUID;

/** Top-level team browser, drawn on a vanilla-style gray panel with a dark inset list. */
public final class TeamMenuScreen extends Screen implements Refreshable {

    private static final int PANEL_W = 320;
    private static final int HEADER_H = 26; // title text + padding inside the gray panel
    private static final int FOOTER_H = 64;

    private TeamList list;
    private int panelX, panelY, panelH;

    public TeamMenuScreen() {
        super(Component.translatable("teamsmod.gui.title"));
    }

    @Override
    protected void init() {
        var data = ClientTeamData.INSTANCE;

        panelH = Math.min(this.height - 40, 280);
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - panelH) / 2;

        int listX = panelX + 8;
        int listY = panelY + HEADER_H + 4;
        int listW = PANEL_W - 16;
        int invitesH = data.invites().isEmpty() ? 0 : 22;
        int listH = panelH - HEADER_H - FOOTER_H - 4 - invitesH;

        if (!data.invites().isEmpty()) {
            this.addRenderableWidget(Button.builder(
                            Component.translatable("teamsmod.gui.invites_banner", data.invites().size())
                                    .withStyle(ChatFormatting.YELLOW),
                            b -> this.minecraft.setScreen(new InviteListScreen(this)))
                    .bounds(listX, listY, listW, 18).build());
            listY += invitesH;
        }

        // Square HUD-toggle pill in the panel's top-right corner. Mirrors
        // the icon-tab pattern Simple Voice Chat uses in its menus.
        boolean hudOn = HudConfig.isEnabled();
        Component hudLabel = Component.literal("HUD")
                .withStyle(hudOn ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
        Button hudBtn = Button.builder(hudLabel, b -> {
                            HudConfig.toggle();
                            this.rebuildWidgets();
                        })
                .bounds(panelX + PANEL_W - 28, panelY + 5, 22, 16)
                .build();
        hudBtn.setTooltip(Tooltip.create(Component.translatable(
                hudOn ? "teamsmod.gui.hud_hide" : "teamsmod.gui.hud_show")));
        this.addRenderableWidget(hudBtn);

        list = new TeamList(listX, listY, listW, listH);
        this.addWidget(list);

        // Bottom buttons inside the footer area
        int btnY = panelY + panelH - 28;
        int btnW = (PANEL_W - 24) / 2;
        if (data.inTeam()) {
            this.addRenderableWidget(Button.builder(
                            Component.translatable("teamsmod.gui.manage_team"),
                            b -> this.minecraft.setScreen(new TeamManageScreen(this)))
                    .bounds(panelX + 8, btnY, btnW, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(
                            Component.translatable("teamsmod.gui.create_new"),
                            b -> this.minecraft.setScreen(new CreateTeamScreen(this)))
                    .bounds(panelX + 8, btnY, btnW, 20).build());
        }
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        b -> this.onClose())
                .bounds(panelX + PANEL_W - 8 - btnW, btnY, btnW, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);

        Panels.drawPanel(g, panelX, panelY, PANEL_W, panelH);

        // Title in dark text on the gray panel (vanilla chest convention),
        // no drop shadow - shadow muddies dark-on-light readings.
        Panels.drawCenteredText(g, this.font, this.title,
                panelX + PANEL_W / 2, panelY + 7, Panels.FONT_COLOR);

        // Dark inset for the list
        var data = ClientTeamData.INSTANCE;
        int invitesH = data.invites().isEmpty() ? 0 : 22;
        int boxX = panelX + 6;
        int boxY = panelY + HEADER_H + invitesH;
        int boxW = PANEL_W - 12;
        int boxH = panelH - HEADER_H - FOOTER_H - invitesH;
        Panels.drawContentBox(g, boxX, boxY, boxW, boxH);

        list.render(g, mouseX, mouseY, partial);

        if (list.children().isEmpty()) {
            Panels.drawCenteredText(g, this.font,
                    Component.translatable("teamsmod.gui.no_teams").withStyle(ChatFormatting.ITALIC),
                    panelX + PANEL_W / 2, boxY + boxH / 2 - 4, 0xFFB0B0B0);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void refresh() { this.rebuildWidgets(); }

    // ─── List widget ───────────────────────────────────────────────────────

    private final class TeamList extends ObjectSelectionList<TeamList.TeamEntry> {

        private final int listX;
        private final int listW;

        TeamList(int x, int y, int w, int h) {
            super(TeamMenuScreen.this.minecraft, w, h, y, 28);
            this.setX(x);
            this.listX = x;
            this.listW = w;

            var data = ClientTeamData.INSTANCE;
            var sorted = new ArrayList<>(data.allTeams());
            sorted.sort((a, b) -> {
                boolean ma = data.inTeam() && a.id() == data.teamId();
                boolean mb = data.inTeam() && b.id() == data.teamId();
                return ma && !mb ? -1 : mb && !ma ? 1 : 0;
            });
            sorted.forEach(e -> addEntry(new TeamEntry(e)));
        }

        @Override public int getRowWidth() { return listW - 12; }
        @Override protected int getScrollbarPosition() { return listX + listW - 6; }
        @Override public int getRowLeft() { return listX + 2; }

        // Default renderSelection centers the outline on the list's overall
        // width, but our rows are anchored flush-left via getRowLeft, so the
        // outline ends up crossing through the face icon. Anchor on
        // getRowLeft() instead so the outline wraps the row cleanly.
        @Override
        protected void renderSelection(GuiGraphics g, int top, int rowWidth, int rowHeight, int outerColor, int innerColor) {
            int x1 = this.getRowLeft() - 2;
            int x2 = this.getRowLeft() + this.getRowWidth() + 2;
            g.fill(x1, top - 2, x2, top + rowHeight + 2, outerColor);
            g.fill(x1 + 1, top - 1, x2 - 1, top + rowHeight + 1, innerColor);
        }

        final class TeamEntry extends ObjectSelectionList.Entry<TeamEntry> {

            private static final int VIEW_W = 50;
            private static final int VIEW_H = 18;

            private final TeamListSyncPayload.Entry team;
            private int viewX = -1, viewY = -1;

            TeamEntry(TeamListSyncPayload.Entry team) { this.team = team; }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partial) {
                var data = ClientTeamData.INSTANCE;
                boolean mine = data.inTeam() && data.teamId() == team.id();

                // Faint row tint for "your team", subtle hover highlight otherwise.
                // Fill extends 2px past the row on each side to match the selection
                // outline so neither crosses through the face icon.
                if (mine) g.fill(left - 2, top, left + width + 2, top + height, 0x335CFF60);
                else if (hovered) g.fill(left - 2, top, left + width + 2, top + height, 0x22FFFFFF);

                PlayerSkin skin = lookupSkin(team.owner(), team.ownerName());
                int face = 18;
                PlayerFaceRenderer.draw(g, skin, left + 3, top + (height - face) / 2, face);

                int tx = left + face + 8;

                // View button on the right (positions the right-side content)
                viewX = left + width - VIEW_W - 4;
                viewY = top + (height - VIEW_H) / 2;

                // Reserve the right-hand area for member count / "Your team" badge
                String count = team.memberCount() + (team.memberCount() == 1 ? " member" : " members");
                int countW = font.width(count);
                Component badge = Component.translatable("teamsmod.gui.your_team").withStyle(ChatFormatting.GREEN);
                int badgeW = font.width(badge);
                int rightStuffW = mine ? Math.max(countW, badgeW) : countW;
                int rightAnchor = viewX - 8;

                // Available width for name + description, leaving 8px gutter before the right block
                int textMaxW = rightAnchor - rightStuffW - tx - 8;
                if (textMaxW < 40) textMaxW = 40;

                String nameTrimmed = Panels.truncate(font, team.name(), textMaxW);
                g.drawString(font, nameTrimmed, tx, top + 4, mine ? 0xFFFFCC33 : 0xFFFFFFFF, false);

                String subText = team.description() != null && !team.description().isBlank()
                        ? team.description()
                        : "by " + team.ownerName();
                String subTrimmed = Panels.truncate(font, subText, textMaxW);
                g.drawString(font, subTrimmed, tx, top + 15, 0xFFAAAAAA, false);

                drawSmallButton(g, viewX, viewY, VIEW_W, VIEW_H,
                        Component.translatable("teamsmod.gui.view_detail"), mouseX, mouseY);

                if (mine) {
                    g.drawString(font, badge, rightAnchor - badgeW, top + 4, 0xFF55FF55, false);
                    g.drawString(font, count, rightAnchor - countW, top + 15, 0xFFAAAAAA, false);
                } else {
                    g.drawString(font, count, rightAnchor - countW,
                            top + (height - font.lineHeight) / 2, 0xFFAAAAAA, false);
                }
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                if (button == 0
                        && mx >= viewX && mx < viewX + VIEW_W
                        && my >= viewY && my < viewY + VIEW_H) {
                    Minecraft.getInstance().setScreen(new TeamDetailScreen(TeamMenuScreen.this, team));
                    return true;
                }
                return false;
            }

            @Override public Component getNarration() { return Component.literal(team.name()); }
        }
    }

    /** Beveled mini-button used inside list rows (Detail / Kick). */
    static void drawSmallButton(GuiGraphics g, int x, int y, int w, int h,
                                Component label, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int fill = hover ? 0xFF4F596A : 0xFF3B4453;
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, 0xFF8C9CB6);
        g.fill(x, y, x + 1, y + h, 0xFF8C9CB6);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF1A1F26);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF1A1F26);
        var mc = Minecraft.getInstance();
        g.drawCenteredString(mc.font, label, x + w / 2,
                y + (h - mc.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    static PlayerSkin lookupSkin(UUID uuid, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(uuid);
    }
}
