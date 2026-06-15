package cz.sajmonoriginal.teamsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import cz.sajmonoriginal.teamsmod.network.payload.OpenTeamMenuPayload;
import cz.sajmonoriginal.teamsmod.team.ChatChannel;
import cz.sajmonoriginal.teamsmod.team.Team;
import cz.sajmonoriginal.teamsmod.team.TeamInvite;
import cz.sajmonoriginal.teamsmod.team.TeamManager;
import cz.sajmonoriginal.teamsmod.team.TeamMember;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

public final class TeamCommand {

    private TeamCommand() {}

    private static final SuggestionProvider<CommandSourceStack> INVITE_SUGGEST = (ctx, builder) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        TeamManager mgr = TeamManager.getOrNull();
        if (p != null && mgr != null) {
            String remaining = builder.getRemainingLowerCase();
            for (TeamInvite inv : mgr.invitesFor(p.getUUID())) {
                if (remaining.isEmpty() || inv.teamName().toLowerCase().startsWith(remaining)) {
                    builder.suggest(inv.teamName());
                }
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("team")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> create(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        ""))
                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                        .executes(ctx -> create(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "description"))))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> invite(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(INVITE_SUGGEST)
                                .executes(ctx -> accept(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(INVITE_SUGGEST)
                                .executes(ctx -> decline(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> kick(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("invites").executes(ctx -> invites(ctx.getSource())))
                .then(Commands.literal("channel")
                        .then(Commands.literal("all").executes(ctx -> setChannel(ctx.getSource(), ChatChannel.ALL)))
                        .then(Commands.literal("team").executes(ctx -> setChannel(ctx.getSource(), ChatChannel.TEAM))))
                .then(Commands.literal("menu").executes(ctx -> menu(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> reload(ctx.getSource()))));
    }

    private static int create(CommandSourceStack src, String name, String description) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        TeamManager.CreateResult r = TeamManager.get().createTeam(p, name, description);
        if (!r.success()) {
            src.sendFailure(r.error());
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("teamsmod.created", name).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int invite(CommandSourceStack src, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        TeamManager.ActionResult r = TeamManager.get().invite(p, target);
        if (!r.success()) {
            src.sendFailure(r.error());
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("teamsmod.invited", target.getGameProfile().getName())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int accept(CommandSourceStack src, String teamName) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        TeamManager mgr = TeamManager.get();
        String name = teamName == null ? "" : teamName.trim();
        Optional<Team> t = mgr.teamByName(name);
        if (t.isEmpty()) {
            src.sendFailure(Component.translatable("teamsmod.error.no_team"));
            return 0;
        }
        TeamManager.ActionResult r = mgr.acceptInvite(p, t.get().id());
        if (!r.success()) {
            src.sendFailure(r.error());
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("teamsmod.joined", name).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int decline(CommandSourceStack src, String teamName) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        TeamManager mgr = TeamManager.get();
        String name = teamName == null ? "" : teamName.trim();
        Optional<Team> t = mgr.teamByName(name);
        if (t.isEmpty()) {
            src.sendFailure(Component.translatable("teamsmod.error.no_team"));
            return 0;
        }
        TeamManager.ActionResult r = mgr.declineInvite(p, t.get().id());
        if (!r.success()) {
            src.sendFailure(r.error());
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("teamsmod.declined", name), false);
        return 1;
    }

    private static int leave(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        TeamManager.ActionResult r = TeamManager.get().leave(p);
        if (!r.success()) {
            src.sendFailure(r.error());
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("teamsmod.left").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int kick(CommandSourceStack src, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        TeamManager.ActionResult r = TeamManager.get().kick(p, target.getUUID());
        if (!r.success()) {
            src.sendFailure(r.error());
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("teamsmod.kicked", target.getGameProfile().getName())
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int list(CommandSourceStack src) {
        TeamManager mgr = TeamManager.get();
        for (Team t : mgr.allTeams()) {
            src.sendSuccess(() -> Component.literal("[" + t.description() + "] " + t.name() + " (" + t.size() + ")"), false);
        }
        return 1;
    }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Optional<Team> t = TeamManager.get().teamOf(p.getUUID());
        if (t.isEmpty()) {
            src.sendFailure(Component.translatable("teamsmod.error.not_in_team"));
            return 0;
        }
        Team team = t.get();
        src.sendSuccess(() -> Component.literal("Team: " + team.name() + " [" + team.description() + "]")
                .withStyle(ChatFormatting.GREEN), false);
        for (TeamMember m : team.members()) {
            ChatFormatting color = m.role() == TeamMember.Role.OWNER ? ChatFormatting.GOLD : ChatFormatting.AQUA;
            src.sendSuccess(() -> Component.literal(" - " + m.name() + " (" + m.role().name().toLowerCase() + ")")
                    .withStyle(color), false);
        }
        return 1;
    }

    private static int invites(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var invites = TeamManager.get().invitesFor(p.getUUID());
        if (invites.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("teamsmod.invites.none"), false);
            return 0;
        }
        for (TeamInvite inv : invites) {
            MutableComponent line = Component.literal("- " + inv.teamName() + " from " + inv.inviterName())
                    .withStyle(ChatFormatting.YELLOW);
            src.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int setChannel(CommandSourceStack src, ChatChannel ch) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        TeamManager.get().setChannel(p.getUUID(), ch);
        src.sendSuccess(() -> Component.translatable("teamsmod.channel.set", ch.name()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int menu(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        PacketDistributor.sendToPlayer(p, new OpenTeamMenuPayload());
        return 1;
    }

    private static int reload(CommandSourceStack src) {
        try {
            TeamManager.get().reconcileFromDatabase();
            src.sendSuccess(() -> Component.literal("Teams reloaded from database.")
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Reload failed: " + e.getMessage()));
            return 0;
        }
    }
}
