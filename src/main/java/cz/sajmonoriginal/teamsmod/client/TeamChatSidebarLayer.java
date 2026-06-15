package cz.sajmonoriginal.teamsmod.client;

import cz.sajmonoriginal.teamsmod.TeamsMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.util.List;

/**
 * HUD layer registered immediately above the vanilla chat. Walks the chat's
 * visible lines, finds the ones we tagged {@code "teamsmod_team"}, and paints
 * a green sidebar at the same coordinates vanilla would have used for its
 * GuiMessageTag indicator. Reflection on {@code trimmedMessages} +
 * {@code chatScrollbarPos} keeps this Mixin-free.
 */
public final class TeamChatSidebarLayer implements LayeredDraw.Layer {

    /** Must match {@code ClientPayloadHandlers#TEAM_CHAT_TAG.logTag()}. */
    private static final String TEAM_LOG_TAG = "teamsmod_team";
    /** Same 2px width as the accent stripe on the chat-channel toggle pill. */
    private static final int BAR_WIDTH = 2;
    /** Slide-in animation duration - chatanimation's default {@code fadeTimeMessage}. */
    private static final float FADE_TIME_MS = 150f;
    /** Same fraction of line-height chatanimation slides over (0.8). */
    private static final float SLIDE_FRACTION = 0.8f;

    private static final Field TRIMMED_FIELD = obtainField("trimmedMessages");
    private static final Field SCROLLBAR_FIELD = obtainField("chatScrollbarPos");

    private static boolean loggedFirstRender;
    private static boolean loggedFirstTeamLine;

    /** Synced with chatanimation: track the most recent line's addedTime so we
     *  know when a new message arrived and re-trigger the slide-in animation. */
    private static int lastTopAddedTime = Integer.MIN_VALUE;
    private static long animStartMs = 0L;

    private static Field obtainField(String name) {
        try {
            Field f = ChatComponent.class.getDeclaredField(name);
            f.setAccessible(true);
            TeamsMod.LOG.info("[teamsmod] sidebar layer: ChatComponent.{} reflection ready", name);
            return f;
        } catch (NoSuchFieldException e) {
            TeamsMod.LOG.error("[teamsmod] sidebar layer: ChatComponent.{} not found", name, e);
            return null;
        }
    }

    @Override
    public void render(GuiGraphics gui, DeltaTracker delta) {
        if (!loggedFirstRender) {
            loggedFirstRender = true;
            TeamsMod.LOG.info("[teamsmod] team_chat_sidebar layer first render() - layer is live");
        }
        paintBars(gui);
    }

    /**
     * Paints the green sidebar over every visible team-chat line. Public so it
     * can be called from both the HUD-layer path (chat closed) and a
     * {@code ScreenEvent.Render.Post} path (chat open - ChatScreen re-renders
     * the chat at the end of its own render method, which would otherwise
     * paint over the bars we drew during the HUD pass).
     */
    public static void paintBars(GuiGraphics gui) {
        if (TRIMMED_FIELD == null || SCROLLBAR_FIELD == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.options.hideGui) return;

        ChatComponent chat = mc.gui.getChat();
        if (chat == null) return;

        List<GuiMessage.Line> trimmed;
        int scrollbarPos;
        try {
            @SuppressWarnings("unchecked")
            List<GuiMessage.Line> tm = (List<GuiMessage.Line>) TRIMMED_FIELD.get(chat);
            trimmed = tm;
            scrollbarPos = (Integer) SCROLLBAR_FIELD.get(chat);
        } catch (IllegalAccessException e) {
            return;
        }

        int messageCount = trimmed.size();
        if (messageCount <= 0) return;

        int linesPerPage = chat.getLinesPerPage();
        int visible = Math.min(linesPerPage, messageCount - scrollbarPos);
        if (visible <= 0) return;

        // Skip the whole pass if no visible line is ours. We accept either
        // a surviving GuiMessageTag.logTag() OR a recorded addedTime - the
        // tag turned out to be unreliable in some chat-mod stacks.
        int teamLineCount = 0;
        for (int j = 0; j < visible; j++) {
            GuiMessage.Line l = trimmed.get(j + scrollbarPos);
            if (l == null) continue;
            if (isTeamLine(l)) teamLineCount++;
        }
        if (teamLineCount == 0) return;

        if (!loggedFirstTeamLine) {
            loggedFirstTeamLine = true;
            TeamsMod.LOG.info("[teamsmod] sidebar layer drawing for first team line ({} visible)", teamLineCount);
        }

        // Mirror vanilla ChatComponent.render's pose stack and y math
        float scale = (float) chat.getScale();
        int chatBottom = Mth.floor((float) (gui.guiHeight() - 40) / scale);
        // ChatComponent#getLineHeight is package-private; same formula vanilla uses.
        int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));

        // Replicate vanilla's fade-out so the stripe disappears together with
        // the chat line it's beside. We deliberately *don't* multiply by
        // chatOpacity here (vanilla's indicator does, but our bar is the only
        // team-chat cue so it should stay clearly visible even when the user
        // dropped chatOpacity for the messages themselves).
        int tickCount = mc.gui.getGuiTicks();
        boolean focused = chat.isChatFocused();

        // Slide-in matched to chatanimation. Trigger on the *newest* line's
        // addedTime changing - trimmed.get(0) is always the newest entry,
        // independent of where the user scrolled to.
        int topAddedTime = trimmed.get(0).addedTime();
        if (topAddedTime != lastTopAddedTime) {
            lastTopAddedTime = topAddedTime;
            animStartMs = System.currentTimeMillis();
        }
        float displacement = 0f;
        if (scrollbarPos == 0) { // chatanimation gates its anim the same way
            long elapsed = System.currentTimeMillis() - animStartMs;
            float maxDisp = lineHeight * SLIDE_FRACTION;
            float a = Math.min(elapsed / FADE_TIME_MS, 1.0f);
            displacement = maxDisp - a * maxDisp;
        }

        gui.pose().pushPose();
        // Pre-scale slide offset - applied at the same matrix point chatanimation
        // applies its translate(0, displacement, 0) at HEAD of chat render.
        if (displacement != 0f) gui.pose().translate(0.0F, displacement, 0.0F);
        gui.pose().scale(scale, scale, 1.0F);
        gui.pose().translate(4.0F, 0.0F, 0.0F);

        for (int j = 0; j < visible; j++) {
            GuiMessage.Line line = trimmed.get(j + scrollbarPos);
            if (line == null || !isTeamLine(line)) continue;

            int age = tickCount - line.addedTime();
            if (age >= 200 && !focused) continue;
            double timeFactor = focused ? 1.0 : getTimeFactor(age);
            // No chatOpacity multiplier - keep the team marker clearly visible.
            int alpha = (int) (255.0 * timeFactor);
            if (alpha < 4) continue;

            int yBottom = chatBottom - j * lineHeight;
            int yTop = yBottom - lineHeight;
            // Green TEAM-channel colour, alpha gated by the same fade curve
            // vanilla uses for chat lines.
            int color = (alpha << 24) | 0x55FF55;
            gui.fill(-4, yTop, -4 + BAR_WIDTH, yBottom, color);
        }

        gui.pose().popPose();
    }

    /**
     * Same fade curve vanilla {@code ChatComponent.render} uses
     * (cf. {@code ChatComponent#getTimeFactor}).
     */
    private static double getTimeFactor(int age) {
        double d = (double) age / 200.0;
        d = 1.0 - d;
        d = d * 10.0;
        d = Mth.clamp(d, 0.0, 1.0);
        return d * d;
    }

    private static boolean isTeamLine(GuiMessage.Line line) {
        GuiMessageTag tag = line.tag();
        if (tag != null && TEAM_LOG_TAG.equals(tag.logTag())) return true;
        return TeamChatTracker.isTeamChat(line.addedTime());
    }
}
