package cz.sajmonoriginal.teamsmod.client.screen;

import cz.sajmonoriginal.teamsmod.client.ClientTeamData;
import cz.sajmonoriginal.teamsmod.network.payload.TeamListSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

/** Read-only team detail. Anyone can open it from the team list. */
public final class TeamDetailScreen extends Screen implements Refreshable {

        private static final int PANEL_W = 320;
    private static final int HEADER_H = 26;
    private static final int INFO_BASE_H = 24; // owner line + padding, no description
    private static final int FOOTER_H = 36;
    private static final int DESC_MAX_LINES = 3;

    private final Screen parent;
    private final TeamListSyncPayload.Entry team;
    private MemberList list;
    private int panelX, panelY, panelH;
    private int infoH; // header + owner line + wrapped description

    public TeamDetailScreen(Screen parent, TeamListSyncPayload.Entry team) {
        super(Component.literal(team.name()));
        this.parent = parent;
        this.team = team;
    }

    @Override
    protected void init() {
        panelH = Math.min(this.height - 40, 280);
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - panelH) / 2;

        // Cap the description at DESC_MAX_LINES so a 500-char description
        // can't push the member list off the panel.
        infoH = INFO_BASE_H;
        if (team.description() != null && !team.description().isBlank()) {
            int maxW = PANEL_W - 32;
            String capped = Panels.truncateMultiline(this.font, team.description(), maxW, DESC_MAX_LINES);
            int lines = this.font.split(Component.literal(capped), maxW).size();
            infoH += lines * (this.font.lineHeight + 1);
        }

        int listX = panelX + 8;
        int listY = panelY + HEADER_H + infoH + 4;
        int listW = PANEL_W - 16;
        int listH = panelH - HEADER_H - infoH - FOOTER_H - 4;
        list = new MemberList(listX, listY, listW, listH);
        this.addWidget(list);

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

        // Info strip (owner + wrapped description) inside the gray panel area
        Component owner = Component.translatable("teamsmod.gui.team_owned_by", team.ownerName())
                .withStyle(ChatFormatting.ITALIC);
        Panels.drawCenteredText(g, this.font, owner,
                panelX + PANEL_W / 2, panelY + HEADER_H + 6, 0xFF707070);

        if (team.description() != null && !team.description().isBlank()) {
            int maxW = PANEL_W - 32;
            String capped = Panels.truncateMultiline(this.font, team.description(), maxW, DESC_MAX_LINES);
            var lines = this.font.split(Component.literal(capped), maxW);
            int yLine = panelY + HEADER_H + 18;
            for (var line : lines) {
                int lw = this.font.width(line);
                g.drawString(this.font, line,
                        panelX + (PANEL_W - lw) / 2, yLine, 0xFF404040, false);
                yLine += this.font.lineHeight + 1;
            }
        }

        // Inset members box
        int boxX = panelX + 6;
        int boxY = panelY + HEADER_H + infoH;
        int boxW = PANEL_W - 12;
        int boxH = panelH - HEADER_H - infoH - FOOTER_H;
        Panels.drawContentBox(g, boxX, boxY, boxW, boxH);

        list.render(g, mouseX, mouseY, partial);

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void refresh() {
        boolean stillExists = ClientTeamData.INSTANCE.allTeams().stream().anyMatch(e -> e.id() == team.id());
        if (!stillExists) {
            this.minecraft.setScreen(parent);
            return;
        }
        this.rebuildWidgets();
    }

    private final class MemberList extends ObjectSelectionList<MemberList.MemberEntry> {

        private final int listX;
        private final int listW;

        MemberList(int x, int y, int w, int h) {
            super(TeamDetailScreen.this.minecraft, w, h, y, 22);
            this.setX(x);
            this.listX = x;
            this.listW = w;
            for (var m : team.members()) addEntry(new MemberEntry(m));
        }

        @Override public int getRowWidth() { return listW - 12; }
        @Override protected int getScrollbarPosition() { return listX + listW - 6; }
        @Override public int getRowLeft() { return listX + 2; }

        @Override
        protected void renderSelection(GuiGraphics g, int top, int rowWidth, int rowHeight, int outerColor, int innerColor) {
            int x1 = this.getRowLeft() - 2;
            int x2 = this.getRowLeft() + this.getRowWidth() + 2;
            g.fill(x1, top - 2, x2, top + rowHeight + 2, outerColor);
            g.fill(x1 + 1, top - 1, x2 - 1, top + rowHeight + 1, innerColor);
        }

        final class MemberEntry extends ObjectSelectionList.Entry<MemberEntry> {

            private final TeamListSyncPayload.MemberInfo member;

            MemberEntry(TeamListSyncPayload.MemberInfo member) { this.member = member; }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partial) {
                int face = 16;
                PlayerSkin skin = TeamMenuScreen.lookupSkin(member.uuid(), member.name());
                PlayerFaceRenderer.draw(g, skin, left + 3, top + (height - face) / 2, face);

                if (hovered) g.fill(left - 2, top, left + width + 2, top + height, 0x22FFFFFF);

                int tx = left + face + 8;
                int color = member.isOwner() ? 0xFFFFCC33 : 0xFFFFFFFF;
                g.drawString(font, member.name(), tx, top + (height - font.lineHeight) / 2, color, false);

                if (member.isOwner()) {
                    Component role = Component.translatable("teamsmod.gui.role_owner")
                            .withStyle(ChatFormatting.GOLD);
                    int cw = font.width(role);
                    g.drawString(font, role, left + width - cw - 8,
                            top + (height - font.lineHeight) / 2, 0xFFFFCC33, false);
                }
            }

            @Override
            public Component getNarration() { return Component.literal(member.name()); }
        }
    }
}
