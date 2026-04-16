package com.joys.scorerecord;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.StatHandler;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.ToIntFunction;

enum ScoreRecordCategory {
	PLACED("placed", "sr_placed", Text.literal("放置榜"), player -> {
		StatHandler statHandler = player.getStatHandler();
		int total = 0;
		for (Item item : placeableBlockItems()) {
			total += statHandler.getStat(Stats.USED, item);
		}
		return total;
	}),
	MINED("mined", "sr_mined", Text.literal("挖掘榜"), player -> {
		StatHandler statHandler = player.getStatHandler();
		int total = 0;
		for (Block block : Registries.BLOCK) {
			total += statHandler.getStat(Stats.MINED, block);
		}
		return total;
	}),
	MOB_KILLS("kills", "sr_kills", Text.literal("击杀榜"), player -> player.getStatHandler().getStat(Stats.CUSTOM, Stats.MOB_KILLS)),
	DEATHS("deaths", "sr_deaths", Text.literal("败北榜"), player -> player.getStatHandler().getStat(Stats.CUSTOM, Stats.DEATHS)),
	PLAYTIME("playtime", "sr_playtime", Text.literal("熬老头榜"), player -> player.getStatHandler().getStat(Stats.CUSTOM, Stats.PLAY_TIME) / 1200),
	DISTANCE("distance", "sr_distance", Text.literal("马拉松榜"), player -> totalDistanceCm(player.getStatHandler()) / 100);

	private final String commandName;
	private final String objectiveName;
	private final Text displayName;
	private final ToIntFunction<ServerPlayerEntity> valueProvider;

	ScoreRecordCategory(String commandName, String objectiveName, Text displayName, ToIntFunction<ServerPlayerEntity> valueProvider) {
		this.commandName = commandName;
		this.objectiveName = objectiveName;
		this.displayName = displayName;
		this.valueProvider = valueProvider;
	}

	String commandName() {
		return commandName;
	}

	String objectiveName() {
		return objectiveName;
	}

	Text displayName() {
		return displayName;
	}

	int readScore(ServerPlayerEntity player) {
		return valueProvider.applyAsInt(player);
	}

	private static int totalDistanceCm(StatHandler statHandler) {
		return statHandler.getStat(Stats.CUSTOM, Stats.WALK_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.CROUCH_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.SPRINT_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.SWIM_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.CLIMB_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.FLY_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.WALK_UNDER_WATER_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.WALK_ON_WATER_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.AVIATE_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.BOAT_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.MINECART_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.HORSE_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.PIG_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.STRIDER_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.NAUTILUS_ONE_CM)
			+ statHandler.getStat(Stats.CUSTOM, Stats.HAPPY_GHAST_ONE_CM);
	}

	private static List<Item> placeableBlockItems() {
		return Holder.PLACEABLE_BLOCK_ITEMS;
	}

	private static final class Holder {
		private static final List<Item> PLACEABLE_BLOCK_ITEMS = Registries.BLOCK.stream()
			.map(Block::asItem)
			.filter(item -> item != Items.AIR)
			.distinct()
			.toList();
	}
}
