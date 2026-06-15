package cz.sajmonoriginal.teamsmod.client.screen;

import cz.sajmonoriginal.teamsmod.network.payload.CreateTeamRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CreateTeamScreen extends Screen implements Refreshable {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 168;
    private static final int HEADER_H = 26;

    private final Screen parent;
    private EditBox nameBox;
    private EditBox descBox;
    private Button createBtn;
    private int panelX, panelY;

    public CreateTeamScreen(Screen parent) {
        super(Component.translatable("teamsmod.gui.create"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        int formX = panelX + 12;
        int formW = PANEL_W - 24;

        nameBox = new EditBox(this.font, formX, panelY + HEADER_H + 18, formW, 20,
                Component.translatable("teamsmod.gui.team_name"));
        nameBox.setMaxLength(24);
        nameBox.setHint(Component.translatable("teamsmod.gui.team_name"));
        nameBox.setResponder(s -> updateCreateState());
        this.addRenderableWidget(nameBox);

        descBox = new EditBox(this.font, formX, panelY + HEADER_H + 60, formW, 20,
                Component.translatable("teamsmod.gui.team_description"));
        descBox.setMaxLength(64);
        descBox.setHint(Component.translatable("teamsmod.gui.team_description_hint"));
        this.addRenderableWidget(descBox);

        int btnY = panelY + PANEL_H - 28;
        int btnW = (PANEL_W - 28) / 2;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(formX, btnY, btnW, 20).build());

        createBtn = Button.builder(
                        Component.translatable("teamsmod.gui.create"),
                        b -> submit())
                .bounds(panelX + PANEL_W - 12 - btnW, btnY, btnW, 20).build();
        this.addRenderableWidget(createBtn);

        this.setInitialFocus(nameBox);
        updateCreateState();
    }

    private void updateCreateState() {
        if (createBtn != null) createBtn.active = !nameBox.getValue().trim().isEmpty();
    }

    private void submit() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) return;
        PacketDistributor.sendToServer(new CreateTeamRequestPayload(name, descBox.getValue().trim()));
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);

        Panels.drawPanel(g, panelX, panelY, PANEL_W, PANEL_H);

        Panels.drawCenteredText(g, this.font, this.title,
                panelX + PANEL_W / 2, panelY + 7, Panels.FONT_COLOR);

        int formX = panelX + 12;
        // Field labels
        g.drawString(this.font, Component.translatable("teamsmod.gui.team_name"),
                formX, panelY + HEADER_H + 6, Panels.LABEL_COLOR, false);
        g.drawString(this.font, Component.translatable("teamsmod.gui.team_description_label"),
                formX, panelY + HEADER_H + 48, Panels.LABEL_COLOR, false);

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void refresh() {
        if (cz.sajmonoriginal.teamsmod.client.ClientTeamData.INSTANCE.inTeam()) {
            this.minecraft.setScreen(parent);
        }
    }
}
