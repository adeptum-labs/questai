package com.adeptum.questai.utility;

import com.adeptum.questai.model.world.quest.QuestObjective;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumUtilTest {

	@Test
	void randomReturnsValidEnumConstant() {
		for (int i = 0; i < 100; i++) {
			final QuestObjective.Type result =
				EnumUtil.random(QuestObjective.Type.class);
			assertNotNull(result);
		}
	}

	@Test
	void randomCoversAllValues() {
		final EnumSet<QuestObjective.Type> seen =
			EnumSet.noneOf(QuestObjective.Type.class);

		for (int i = 0; i < 1000; i++) {
			seen.add(EnumUtil.random(QuestObjective.Type.class));
		}

		assertEquals(
			EnumSet.allOf(QuestObjective.Type.class), seen,
			"All enum values should appear after 1000 random picks"
		);
	}
}
