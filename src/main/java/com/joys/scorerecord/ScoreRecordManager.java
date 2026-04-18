package com.joys.scorerecord;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.CommandSource;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardCriterion.RenderType;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

final class ScoreRecordManager {
	private static final int SYNC_INTERVAL_TICKS = 20;
	private static final String PLAYER_ARGUMENT = "player";
	private int tickCounter;

	void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			literal("score-record")
				.executes(context -> showHelp(context.getSource()))
				.then(listLiteral())
				.then(playerLiteral())
		));

		ServerLifecycleEvents.SERVER_STARTED.register(this::ensureInitialized);
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
	}

	private void tick(MinecraftServer server) {
		tickCounter++;
		if (tickCounter < SYNC_INTERVAL_TICKS) {
			return;
		}

		tickCounter = 0;
		syncAll(server);
	}

	private void ensureInitialized(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		ensureObjectives(scoreboard);
		purgeExcludedPlayers(scoreboard, ScoreRecordPlayerState.get(server));
		syncAll(server);
	}

	private LiteralArgumentBuilder<ServerCommandSource> listLiteral() {
		LiteralArgumentBuilder<ServerCommandSource> list = literal("list")
			.executes(context -> listCategories(context.getSource()))
			.then(literal("hide").executes(context -> hideSidebar(context.getSource())))
			.then(literal("refresh").executes(context -> refresh(context.getSource())));

		for (ScoreRecordCategory category : ScoreRecordCategory.values()) {
			list.then(literal(category.commandName()).executes(context -> showCategory(context.getSource(), category)));
		}

		return list;
	}

	private LiteralArgumentBuilder<ServerCommandSource> playerLiteral() {
		return literal("player")
			.executes(context -> listPlayers(context.getSource()))
			.then(literal("list").executes(context -> listPlayers(context.getSource())))
			.then(literal("add")
				.then(argument(PLAYER_ARGUMENT, StringArgumentType.word())
					.suggests(this::suggestPlayers)
					.executes(context -> addPlayer(
						context.getSource(),
						StringArgumentType.getString(context, PLAYER_ARGUMENT)
					))
				)
			)
			.then(literal("remove")
				.then(argument(PLAYER_ARGUMENT, StringArgumentType.word())
					.suggests(this::suggestPlayers)
					.executes(context -> removePlayer(
						context.getSource(),
						StringArgumentType.getString(context, PLAYER_ARGUMENT)
					))
				)
			);
	}

	private int showHelp(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal("用法: /score-record <list|player> ..."), false);
		source.sendFeedback(() -> Text.literal("显示统计: /score-record list [placed|mined|kills|deaths|playtime|distance|refresh|hide]"), false);
		source.sendFeedback(() -> Text.literal("玩家管理: /score-record player <list|add|remove> [player]"), false);
		return 1;
	}

	private int listCategories(ServerCommandSource source) {
		ScoreboardObjective sidebarObjective = source.getServer().getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
		String currentDisplay = sidebarObjective == null ? "已隐藏" : describeObjective(sidebarObjective);
		source.sendFeedback(() -> Text.literal("可显示统计: placed, mined, kills, deaths, playtime, distance"), false);
		source.sendFeedback(() -> Text.literal("当前侧边栏: " + currentDisplay), false);
		return 1;
	}

	private int listPlayers(ServerCommandSource source) {
		ScoreRecordPlayerState state = ScoreRecordPlayerState.get(source.getServer());
		if (state.excludedPlayers().isEmpty()) {
			source.sendFeedback(() -> Text.literal("当前为默认全员同步，未排除任何玩家"), false);
			return 1;
		}

		source.sendFeedback(() -> Text.literal("已排除玩家: " + String.join(", ", state.excludedPlayers())), false);
		return state.excludedPlayers().size();
	}

	private int refresh(ServerCommandSource source) {
		ensureInitialized(source.getServer());
		source.sendFeedback(() -> Text.literal("Score Record 统计已刷新"), false);
		return 1;
	}

	private int hideSidebar(ServerCommandSource source) {
		source.getServer().getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
		source.sendFeedback(() -> Text.literal("已隐藏计分板侧边栏"), false);
		return 1;
	}

	private int showCategory(ServerCommandSource source, ScoreRecordCategory category) {
		MinecraftServer server = source.getServer();
		ensureInitialized(server);
		Scoreboard scoreboard = server.getScoreboard();
		scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, getOrCreateObjective(scoreboard, category));
		source.sendFeedback(() -> Text.literal("当前显示: ").append(category.displayName()), false);
		return 1;
	}

	private int addPlayer(ServerCommandSource source, String requestedName) {
		MinecraftServer server = source.getServer();
		String playerName = resolvePlayerName(server, requestedName);
		ScoreRecordPlayerState state = ScoreRecordPlayerState.get(server);
		state.include(playerName);
		syncPlayer(server, playerName);
		source.sendFeedback(() -> Text.literal("已恢复玩家统计: " + playerName), false);
		return 1;
	}

	private int removePlayer(ServerCommandSource source, String requestedName) {
		MinecraftServer server = source.getServer();
		String playerName = resolvePlayerName(server, requestedName);
		ScoreRecordPlayerState state = ScoreRecordPlayerState.get(server);
		state.exclude(playerName);
		removePlayerScores(server.getScoreboard(), playerName);
		source.sendFeedback(() -> Text.literal("已从所有统计表移除玩家: " + playerName), false);
		return 1;
	}

	private void syncAll(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		ensureObjectives(scoreboard);
		ScoreRecordPlayerState state = ScoreRecordPlayerState.get(server);

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (state.isExcluded(player.getNameForScoreboard())) {
				continue;
			}
			syncPlayer(scoreboard, player);
		}
	}

	private void syncPlayer(MinecraftServer server, String playerName) {
		if (ScoreRecordPlayerState.get(server).isExcluded(playerName)) {
			return;
		}

		ServerPlayerEntity onlinePlayer = findOnlinePlayer(server, playerName);
		Scoreboard scoreboard = server.getScoreboard();
		ensureObjectives(scoreboard);
		ScoreHolder scoreHolder = ScoreHolder.fromName(playerName);

		for (ScoreRecordCategory category : ScoreRecordCategory.values()) {
			ScoreboardObjective objective = getOrCreateObjective(scoreboard, category);
			ScoreAccess score = scoreboard.getOrCreateScore(scoreHolder, objective);
			score.setScore(onlinePlayer == null ? 0 : category.readScore(onlinePlayer));
		}
	}

	private void syncPlayer(Scoreboard scoreboard, ServerPlayerEntity player) {
		for (ScoreRecordCategory category : ScoreRecordCategory.values()) {
			ScoreboardObjective objective = getOrCreateObjective(scoreboard, category);
			ScoreAccess score = scoreboard.getOrCreateScore(player, objective);
			score.setScore(category.readScore(player));
		}
	}

	private void purgeExcludedPlayers(Scoreboard scoreboard, ScoreRecordPlayerState state) {
		for (String playerName : state.excludedPlayers()) {
			removePlayerScores(scoreboard, playerName);
		}
	}

	private void removePlayerScores(Scoreboard scoreboard, String playerName) {
		scoreboard.removeScores(ScoreHolder.fromName(playerName));
	}

	private void ensureObjectives(Scoreboard scoreboard) {
		for (ScoreRecordCategory category : ScoreRecordCategory.values()) {
			getOrCreateObjective(scoreboard, category);
		}
	}

	private ScoreboardObjective getOrCreateObjective(Scoreboard scoreboard, ScoreRecordCategory category) {
		ScoreboardObjective existing = scoreboard.getNullableObjective(category.objectiveName());
		if (existing != null) {
			return existing;
		}

		return scoreboard.addObjective(
			category.objectiveName(),
			ScoreboardCriterion.DUMMY,
			category.displayName(),
			RenderType.INTEGER,
			false,
			null
		);
	}

	private String describeObjective(ScoreboardObjective objective) {
		for (ScoreRecordCategory category : ScoreRecordCategory.values()) {
			if (category.objectiveName().equals(objective.getName())) {
				return category.commandName();
			}
		}
		return objective.getName();
	}

	private CompletableFuture<Suggestions> suggestPlayers(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		MinecraftServer server = context.getSource().getServer();
		Set<String> suggestions = new LinkedHashSet<>();
		for (String playerName : server.getPlayerNames()) {
			suggestions.add(playerName);
		}
		for (String playerName : ScoreRecordPlayerState.get(server).excludedPlayers()) {
			suggestions.add(playerName);
		}
		for (ScoreHolder scoreHolder : server.getScoreboard().getKnownScoreHolders()) {
			suggestions.add(scoreHolder.getNameForScoreboard());
		}

		return CommandSource.suggestMatching(
			suggestions.stream().sorted(Comparator.naturalOrder()),
			builder
		);
	}

	private String resolvePlayerName(MinecraftServer server, String requestedName) {
		ServerPlayerEntity onlinePlayer = findOnlinePlayer(server, requestedName);
		if (onlinePlayer != null) {
			return onlinePlayer.getNameForScoreboard();
		}

		Collection<String> excludedPlayers = ScoreRecordPlayerState.get(server).excludedPlayers();
		for (String playerName : excludedPlayers) {
			if (playerName.equalsIgnoreCase(requestedName)) {
				return playerName;
			}
		}

		for (ScoreHolder scoreHolder : server.getScoreboard().getKnownScoreHolders()) {
			String playerName = scoreHolder.getNameForScoreboard();
			if (playerName.equalsIgnoreCase(requestedName)) {
				return playerName;
			}
		}

		return requestedName;
	}

	private ServerPlayerEntity findOnlinePlayer(MinecraftServer server, String playerName) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.getNameForScoreboard().equalsIgnoreCase(playerName)) {
				return player;
			}
		}
		return null;
	}
}
