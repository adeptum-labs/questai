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

import com.adeptum.questai.fortify.VillageWork;
import com.adeptum.questai.fortify.VillageWorksStore;
import com.adeptum.questai.fortify.WorkState;
import com.adeptum.questai.teleport.VillageTeleportStone;
import com.adeptum.questai.village.VillageRegistry;
import java.lang.reflect.Method;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The stone is only offered when the ladder is finished and no stone for
 * the village exists yet — the census stands in for a persisted flag.
 */
class ConversationManagerStoneTest {

	private static final String ROW_ID = "world_10_20";

	private ServerMock server;
	private ConversationManager manager;
	private VillageWorksStore worksStore;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		worksStore = mock(VillageWorksStore.class);
		manager = new ConversationManager(null, null, null);
		manager.setWorksStore(worksStore);
		manager.setRegistry(mock(VillageRegistry.class));
		manager.setStoneClaimHandler((player, rowId) -> { });
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	private boolean canClaim(final PlayerMock player) {
		try {
			final Method method = ConversationManager.class.getDeclaredMethod(
				"canClaimStone", org.bukkit.entity.Player.class,
				ConversationState.class);
			method.setAccessible(true);
			final ConversationState state = ConversationState.builder()
				.phase(ConversationPhase.OPTIONS)
				.build();
			state.setVillageRowId(ROW_ID);
			return (boolean) method.invoke(manager, player, state);
		} catch (final ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private void withTier(final int tier) {
		final WorkState works = new WorkState();
		works.setTier(tier);
		when(worksStore.get(ROW_ID)).thenReturn(works);
	}

	@Test
	void offersWhenFullyBuiltAndNoStoneExists() {
		withTier(VillageWork.count());
		assertTrue(canClaim(server.addPlayer()));
	}

	@Test
	void doesNotOfferWhileTheWorksAreStillOpen() {
		withTier(0);
		assertFalse(canClaim(server.addPlayer()));
	}

	@Test
	void doesNotOfferWhenAStoneAlreadyExists() {
		withTier(VillageWork.count());
		final PlayerMock holder = server.addPlayer();
		holder.getInventory()
			.addItem(VillageTeleportStone.create(ROW_ID, "Harrowdale", 120));

		assertFalse(canClaim(server.addPlayer()));
	}
}
