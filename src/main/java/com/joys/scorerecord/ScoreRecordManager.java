package com.joys.scorerecord;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardCriterion.RenderType;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

final class ScoreRecordManager {
	private static final int SYNC_INTERVAL_TICKS = 20;
	private int tickCounter;

	void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			literal("score-record")
				.executes(context -> showHelp(context.getSource()))
				.then(literal("list").executes(context -> listCategories(context.getSource())))
				.then(literal("refresh").executes(context -> refresh(context.getSource())))
				.then(literal("hide").executes(context -> hideSidebar(context.getSource())))
				.then(showLiteral(ScoreRecordCategory.PLACED))
				.then(showLiteral(ScoreRecordCategory.MINED))
				.then(showLiteral(ScoreRecordCategory.MOB_KILLS))
				.then(showLiteral(ScoreRecordCategory.DEATHS))
				.then(showLiteral(ScoreRecordCategory.PLAYTIME))
				.then(showLiteral(ScoreRecordCategory.DISTANCE))
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
		ensureObjectives(server.getScoreboard());
		syncAll(server);
	}

	private int showHelp(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal("用法: /score-record <placed|mined|kills|deaths|playtime|distance|list|refresh|hide>"), false);
		return 1;
	}

	private int listCategories(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal("可显示统计: placed, mined, kills, deaths, playtime, distance"), false);
		return 1;
	}

	private int refresh(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		ensureInitialized(server);
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
		source.sendFeedback(() -> Text.literal("当前显示: ").copy().append(category.displayName()), false);
		return 1;
	}

	private LiteralArgumentBuilder<ServerCommandSource> showLiteral(ScoreRecordCategory category) {
		return literal(category.commandName()).executes(context -> showCategory(context.getSource(), category));
	}

	private void syncAll(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		ensureObjectives(scoreboard);

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			for (ScoreRecordCategory category : ScoreRecordCategory.values()) {
				ScoreboardObjective objective = getOrCreateObjective(scoreboard, category);
				ScoreAccess score = scoreboard.getOrCreateScore(player, objective);
				score.setScore(category.readScore(player));
			}
		}
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
}
