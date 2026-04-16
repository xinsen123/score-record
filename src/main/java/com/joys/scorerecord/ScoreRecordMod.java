package com.joys.scorerecord;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScoreRecordMod implements ModInitializer {
	public static final String MOD_ID = "score_record";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		new ScoreRecordManager().register();
		LOGGER.info("{} initialized", MOD_ID);
	}
}
