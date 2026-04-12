package com.adeptum.questai.model.world;

import com.adeptum.questai.model.world.quest.Quest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcTest {

	@Test
	void isQuestTrueWhenQuestSet() {
		final Npc npc = Npc.builder()
			.quest(new Quest())
			.timestamp(1000L)
			.build();
		assertTrue(npc.isQuest());
	}

	@Test
	void isQuestFalseWhenQuestNull() {
		final Npc npc = Npc.builder()
			.timestamp(1000L)
			.build();
		assertFalse(npc.isQuest());
	}

	@Test
	void isPhraseTrueWhenPhraseSet() {
		final Npc npc = Npc.builder()
			.nonsensePhrase("Hmm, carrots!")
			.timestamp(1000L)
			.build();
		assertTrue(npc.isPhrase());
	}

	@Test
	void isPhraseFalseWhenPhraseNull() {
		final Npc npc = Npc.builder()
			.timestamp(1000L)
			.build();
		assertFalse(npc.isPhrase());
	}

	@Test
	void builderSetsAllFields() {
		final Quest quest = new Quest();
		final Npc npc = Npc.builder()
			.quest(quest)
			.nonsensePhrase("hello")
			.timestamp(42L)
			.build();

		assertSame(quest, npc.getQuest());
		assertEquals("hello", npc.getNonsensePhrase());
		assertEquals(42L, npc.getTimestamp());
	}
}
