package com.joys.scorerecord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreRecordModTest {
	@Test
	void modIdStaysStable() {
		assertEquals("score_record", ScoreRecordMod.MOD_ID);
	}
}
