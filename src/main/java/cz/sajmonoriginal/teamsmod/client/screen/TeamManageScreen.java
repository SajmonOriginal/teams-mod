package cz.sajmonoriginal.teamsmod.client.screen;

import cz.sajmonoriginal.teamsmod.client.ClientTeamData;
import cz.sajmonoriginal.teamsmod.network.payload.InviteRequestPayload;
import cz.sajmonoriginal.teamsmod.network.payload.KickRequestPayload;
import cz.sajmonoriginal.teamsmod.network.payload.LeaveRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TeamManageScreen extends Screen implements Refreshable {

    private static final int PANEL_W = 320;
    private static final int HEADER_H = 26;
    private static final int FOOTER_H = 92;

    private final Screen parent;
    private MemberList list;
    private EditBox inviteBox;
    private int panelX, panelY, panelH;

    public TeamManageScreen(Screen parent) {
        super(Component.literal(ClientTeamData.INSTANCE.teamName()));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var data = ClientTeamData.INSTANCE;
        boolean owner = data.isOwner();

        panelH = Math.min(this.height - 40, 280);
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - panelH) / 2;

        int listX = panelX + 8;
        int listY = panelY + HEADER_H + 4;
        int listW = PANEL_W - 16;
        int listH = panelH - HEADER_H - (owner ? FOOTER_H : 56);
        list = new MemberList(listX, listY, listW, listH);
        this.addWidget(list);

        if (owner) {
            int inviteRow = panelY + panelH - 56;
            inviteBox = new EditBox(this.font, panelX + 8, inviteRow, 200, 20,
                    Component.translatable("teamsmod.gui.invite_name"));
            inviteBox.setMaxLength(16);
            inviteBox.setHint(Component.translatable("teamsmod.gui.invite_name"));
            this.addRenderableWidget(inviteBox);

            this.addRenderableWidget(Button.builder(
                            Component.translatable("teamsmod.gui.invite"),
                            b -> {
                                String n = inviteBox.getValue().trim();
                                if (n.isEmpty()) return;
                                PacketDistributor.sendToServer(new InviteRequestPayload(n));
                                inviteBox.setValue("");
                            })
                    .bounds(panelX + 212, inviteRow, PANEL_W - 220, 20).build());
        }

        int btnY = panelY + panelH - 28;
        int btnW = (PANEL_W - 24) / 2;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("teamsmod.gui.leave").withStyle(ChatFormatting.RED),
                        b -> {
                            PacketDistributor.sendToServer(new LeaveRequestPayload());
                            this.minecraft.setScreen(parent);
                        })
                .bounds(panelX + 8, btnY, btnW, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.back"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(panelX + PANEL_W - 8 - btnW, btnY, btnW, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);

        Panels.drawPanel(g, panelX, panelY, PANEL_W, panelH);

        var data = ClientTeamData.INSTANCE;
        Component header = Component.translatable("teamsmod.gui.subtitle_in_team", data.teamName());
        Panels.drawCenteredText(g, this.font, header,
                panelX + PANEL_W / 2, panelY + 7, Panels.FONT_COLOR);

        // Inset content box for member list
        boolean owner = data.isOwner();
        int boxX = panelX + 6;
        int boxY = panelY + HEADER_H;
        int boxW = PANEL_W - 12;
        int boxH = panelH - HEADER_H - (owner ? FOOTER_H : 56);
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
        if (!ClientTeamData.INSTANCE.inTeam()) {
            this.minecraft.setScreen(parent);
            return;
        }
        this.rebuildWidgets();
    }

    // ─── Member list ────────────────────────────────────────────────────────

    private final class MemberList extends ObjectSelectionList<MemberList.MemberEntry> {

        private final int listX;
        private final int listW;

        MemberList(int x, int y, int w, int h) {
            super(TeamManageScreen.this.minecraft, w, h, y, 24);
            this.setX(x);
            this.listX = x;
            this.listW = w;
            for (var m : ClientTeamData.INSTANCE.members()) addEntry(new MemberEntry(m));
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

            private static final int KICK_W = 50;
            private static final int KICK_H = 18;

            private final ClientTeamData.Member member;
            private int kickX = -1, kickY = -1;
            private boolean kickShown;

            MemberEntry(ClientTeamData.Member member) { this.member = member; }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovered, float partial) {
                var data = ClientTeamData.INSTANCE;
                int face = 16;
                PlayerSkin skin = TeamMenuScreen.lookupSkin(member.uuid(), member.name());
                PlayerFaceRenderer.draw(g, skin, left + 3, top + (height - face) / 2, face);

                if (hovered) g.fill(left - 2, top, left + width + 2, top + height, 0x22FFFFFF);

                int tx = left + face + 8;
                int nameColor = member.isOwner() ? 0xFFFFCC33 : 0xFFFFFFFF;
                g.drawString(font, member.name(), tx, top + (height - font.lineHeight) / 2, nameColor, false);

                if (member.isOwner()) {
                    Component role = Component.translatable("teamsmod.gui.role_owner")
                            .withStyle(ChatFormatting.GOLD);
                    int cw = font.width(role);
                    g.drawString(font, role, left + width - cw - 8,
                            top + (height - font.lineHeight) / 2, 0xFFFFCC33, false);
                    kickShown = false;
                } else if (data.isOwner()) {
                    kickX = left + width - KICK_W - 4;
                    kickY = top + (height - KICK_H) / 2;
                    kickShown = true;
                    drawKickButton(g, kickX, kickY, KICK_W, KICK_H, mouseX, mouseY);
                } else {
                    kickShown = false;
                }
            }

            @Override
            public boolean mouseClicked(double mx, double my, int button) {
                if (kickShown && button == 0
                        && mx >= kickX && mx < kickX + KICK_W
                        && my >= kickY && my < kickY + KICK_H) {
                    PacketDistributor.sendToServer(new KickRequestPayload(member.uuid()));
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() { return Component.literal(member.name()); }
        }
    }

    private static void drawKickButton(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int fill = hover ? 0xFFCC2C2C : 0xFFA02020;
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, 0xFFFF6E6E);
        g.fill(x, y, x + 1, y + h, 0xFFFF6E6E);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF3B0707);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF3B0707);
        var mc = net.minecraft.client.Minecraft.getInstance();
        g.drawCenteredString(mc.font, Component.translatable("teamsmod.gui.kick"),
                x + w / 2, y + (h - mc.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }
}
