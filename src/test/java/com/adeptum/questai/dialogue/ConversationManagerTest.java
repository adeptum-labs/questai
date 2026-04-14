/*
 * Copyright (C) 2026 Adeptum AB, org nr. 559494-1824
 *
 * This file is part of QuestAI.
 *
 * QuestAI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * QuestAI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with QuestAI. If not, see
 * <https://www.gnu.org/licenses/>.
 */

package com.adeptum.questai.dialogue;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ConversationManager} — specifically verifying that
 * the THINKING phase blocks clicks while AI responses are pending.
 */
class ConversationManagerTest {

	@Mock private Player player;

	private AutoCloseable mocks;
	private ConversationManager conversationManager;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		conversationManager = new ConversationManager(null, null);
	}

	@AfterEach
	void tearDown() throws Exception {
		mocks.close();
	}

	/**
	 * Reproduces the original bug: DialogueGui.wordWrap (called by
	 * createOptions → createBase → dialogueItem) throws NPE when the
	 * dialogue text is null. This is what happens when lastAiResponse
	 * hasn't been set yet and the GUI tries to render it.
	 */
	@Test
	void wordWrapThrowsNpeOnNullText() throws Exception {
		final Method wordWrap = DialogueGui.class.getDeclaredMethod(
			"wordWrap", String.class, int.class);
		wordWrap.setAccessible(true);

		assertThrows(NullPointerException.class,
			() -> {
				try {
					wordWrap.invoke(null, null, 40);
				} catch (final java.lang.reflect.InvocationTargetException e) {
					throw e.getCause();
				}
			},
			"wordWrap must throw NPE when text is null");
	}

	/**
	 * Verifies that the THINKING phase prevents the NPE above. Clicking
	 * any slot during THINKING does nothing — no exception, no phase
	 * change, no attempt to build a GUI with null dialogue text.
	 */
	@Test
	void clickDuringThinkingPhaseIsIgnored() {
		final ConversationState state = ConversationState.builder()
			.phase(ConversationPhase.THINKING)
			.npcUuid(UUID.randomUUID())
			.npcName("Test NPC")
			.npcProfession("FARMER")
			.questAvailable(true)
			.tradeable(false)
			.build();

		assertNull(state.getLastAiResponse(),
			"lastAiResponse should be null before AI responds");
		injectConversation(player, state);

		for (final int slot : new int[]{
			DialogueGui.OPTION_1_SLOT, DialogueGui.OPTION_2_SLOT,
			DialogueGui.OPTION_3_SLOT, DialogueGui.OPTION_4_SLOT,
			DialogueGui.CENTER_SLOT}) {

			assertDoesNotThrow(
				() -> conversationManager.handleClick(player, slot));
		}

		assertSame(ConversationPhase.THINKING, state.getPhase(),
			"Phase must remain THINKING when AI has not responded");
	}

	/**
	 * Verifies that GREETING phase with a non-null response would
	 * attempt to handle the click (proving the phase gate works).
	 * We set the response so wordWrap won't NPE, and verify the phase
	 * advances to OPTIONS — confirming clicks ARE processed once the
	 * AI has responded and the phase transitions from THINKING.
	 *
	 * <p>Note: the actual GUI creation fails without a Bukkit server,
	 * but the phase change happens before that, proving the handler ran.
	 */
	@Test
	void greetingPhaseWithResponseAdvancesToOptions() {
		final ConversationState state = ConversationState.builder()
			.phase(ConversationPhase.GREETING)
			.npcUuid(UUID.randomUUID())
			.npcName("Test NPC")
			.npcProfession("FARMER")
			.questAvailable(true)
			.tradeable(false)
			.lastAiResponse("Hello traveler!")
			.build();

		injectConversation(player, state);

		// The handler sets phase to OPTIONS before calling createOptions,
		// so even though GUI creation fails without Bukkit, the phase
		// change proves the handler executed.
		try {
			conversationManager.handleClick(player, DialogueGui.CENTER_SLOT);
		} catch (final Exception ignored) {
			// GUI creation fails without server — expected
		}

		assertSame(ConversationPhase.OPTIONS, state.getPhase(),
			"Phase should advance to OPTIONS after clicking Continue");
	}

	private void injectConversation(final Player player,
		final ConversationState state) {

		try {
			final var field = ConversationManager.class
				.getDeclaredField("conversations");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			final var map = (java.util.Map<UUID, ConversationState>) field.get(
				conversationManager);
			map.put(player.getUniqueId(), state);
		} catch (final ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
