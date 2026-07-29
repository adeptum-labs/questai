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

package com.adeptum.questai.village;

import com.adeptum.questai.craft.CommissionStore;
import com.adeptum.questai.event.VillageKey;
import com.adeptum.questai.fortify.VillageWorksStore;
import com.adeptum.questai.fortify.WorkState;
import com.adeptum.questai.reputation.VillageReputationStore;
import com.adeptum.questai.teleport.TeleportStoneStore;
import com.adeptum.questai.villager.StoredLocation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageMergerTest {

	private static final UUID WORLD_ID =
		UUID.fromString("00000000-0000-0000-0000-0000000000aa");

	@Mock private VillageRegistry registry;
	@Mock private VillageReputationStore reputation;
	@Mock private VillageWorksStore works;
	@Mock private TeleportStoneStore stones;
	@Mock private CommissionStore commissions;

	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
	}

	@AfterEach
	void tearDown() throws Exception {
		mocks.close();
	}

	private VillageMerger merger() {
		return new VillageMerger(registry, reputation, works, stones,
			commissions);
	}

	private NamedVillage village(final String id, final long discoveredAt) {
		return new NamedVillage(id, VillageKey.from(WORLD_ID, 0, 0),
			new StoredLocation(WORLD_ID, 0, 64, 0), id, discoveredAt);
	}

	private WorkState tierFour() {
		return tier(4);
	}

	private WorkState tier(final int reached) {
		final WorkState state = new WorkState();
		state.setTier(reached);
		return state;
	}

	/**
	 * Settles a village against its overlaps, with a standing ledger that
	 * really moves when the store is told to merge, as the live one does.
	 */
	private NamedVillage settleAmong(final NamedVillage village,
		final List<NamedVillage> overlaps, final Map<String, Integer> standing) {

		final Map<String, Integer> ledger = new HashMap<>(standing);
		when(reputation.playerCount(anyString())).thenAnswer(
			call -> ledger.getOrDefault(call.<String>getArgument(0), 0));
		doAnswer(call -> {
			final Integer moved = ledger.remove(call.<String>getArgument(0));
			if (moved != null) {
				ledger.merge(call.getArgument(1), moved, Integer::sum);
			}
			return null;
		}).when(reputation).merge(anyString(), anyString());
		when(registry.overlapping(village)).thenReturn(overlaps);
		return merger().settle(village);
	}

	@Test
	void theOlderClaimKeepsItsName() {
		final NamedVillage older = village("a", 1000L);
		final NamedVillage newer = village("b", 2000L);

		assertEquals(older, merger().survivorOf(newer, older));
	}

	@Test
	void ageOutweighsWhatHasBeenBuiltWhenBothClaimsAreStamped() {
		final NamedVillage older = village("larkspur", 1000L);
		final NamedVillage built = village("woldmere", 2000L);
		when(works.get("woldmere")).thenReturn(tierFour());

		assertEquals(older, merger().survivorOf(older, built));
		assertEquals(older, merger().survivorOf(built, older));
	}

	@Test
	void withoutTimestampsTheMoreInvestedVillageKeepsItsName() {
		// Both predate the discovery stamp, as every live row does
		final NamedVillage plain = village("larkspur", 0L);
		final NamedVillage built = village("woldmere", 0L);
		when(works.get("woldmere")).thenReturn(tierFour());
		when(works.get("larkspur")).thenReturn(null);

		assertEquals(built, merger().survivorOf(plain, built));
	}

	@Test
	void anUnrecordedAgeIsWeighedRatherThanTakenForTheEarliest() {
		// A zero is nobody having written the date down, not a village
		// founded at the epoch, so it must not win the age comparison
		final NamedVillage unstamped = village("larkspur", 0L);
		final NamedVillage built = village("woldmere", 5000L);
		when(works.get("woldmere")).thenReturn(tierFour());

		assertEquals(built, merger().survivorOf(unstamped, built));
		assertEquals(built, merger().survivorOf(built, unstamped));
	}

	@Test
	void survivorChoiceDoesNotDependOnArgumentOrder() {
		final NamedVillage plain = village("larkspur", 0L);
		final NamedVillage built = village("woldmere", 0L);
		when(works.get("woldmere")).thenReturn(tierFour());

		assertEquals(merger().survivorOf(plain, built),
			merger().survivorOf(built, plain));
	}

	@Test
	void twoRowsWithNothingToChooseBetweenStillChooseTheSameWay() {
		// Neither dated, neither built on, nobody's standing in either: the
		// id is all that is left, and it must decide rather than the order
		// the two happened to be handed over in
		final NamedVillage first = village("larkspur", 0L);
		final NamedVillage second = village("woldmere", 0L);

		assertEquals(first, merger().survivorOf(first, second));
		assertEquals(first, merger().survivorOf(second, first));
	}

	@Test
	void everyLedgerFollowsTheAbsorbedVillageIntoTheSurvivor() {
		final NamedVillage survivor = village("woldmere", 0L);
		final NamedVillage absorbed = village("larkspur", 0L);

		merger().mergeInto(survivor, absorbed);

		verify(reputation).merge("larkspur", "woldmere");
		verify(works).merge("larkspur", "woldmere");
		verify(stones).merge("larkspur", "woldmere");
		verify(commissions).merge("larkspur", "woldmere");
		verify(registry).absorb("larkspur", "woldmere");
	}

	@Test
	void settlingFoldsEveryOverlappingRowIntoTheOneThatSurvives() {
		final NamedVillage plain = village("larkspur", 0L);
		final NamedVillage built = village("woldmere", 0L);
		final NamedVillage third = village("marrowfen", 0L);
		when(works.get("woldmere")).thenReturn(tierFour());
		when(stones.issued("woldmere")).thenReturn(true);
		when(registry.overlapping(plain)).thenReturn(List.of(built, third));

		assertEquals(built, merger().settle(plain));

		verify(registry).absorb("larkspur", "woldmere");
		verify(registry).absorb("marrowfen", "woldmere");
		verify(works).merge("marrowfen", "woldmere");
	}

	@Test
	void theNameKeptDoesNotFollowTheOrderTheOverlapsAreListedIn() {
		// None of the three dated, so what has been put into them settles it.
		// Either of the plain pair would outweigh the built village if it
		// were let to take the other in first and count that standing as its
		// own, and which of them got to do so would be down to the listing
		final NamedVillage built = village("ashford", 0L);
		final NamedVillage plain = village("brookvale", 0L);
		final NamedVillage third = village("creekmoor", 0L);
		final Map<String, Integer> standing =
			Map.of("brookvale", 25, "creekmoor", 30);
		when(works.get("ashford")).thenReturn(tierFour());

		assertEquals(built, settleAmong(plain, List.of(built, third), standing));
		assertEquals(built, settleAmong(plain, List.of(third, built), standing));
	}

	@Test
	void rowsThatCannotBeRankedInOneLineStillSettleTheSameWay() {
		// Two dated rows are ranked against each other by age but against
		// an undated one by what has been put into them, and those rules
		// disagree here: the elder outranks the built village, the built
		// village outranks the undated one, and the undated one outranks
		// the elder. Working through such a ring in pairs lands wherever
		// the listing happens to lead, and it must land in one place
		final NamedVillage elder = village("ashford", 100L);
		final NamedVillage built = village("brookvale", 200L);
		final NamedVillage undated = village("creekmoor", 0L);
		final NamedVillage youngest = village("duskmere", 300L);
		final Map<String, Integer> standing = Map.of("creekmoor", 30);
		when(works.get("brookvale")).thenReturn(tier(6));

		assertEquals(
			settleAmong(youngest, List.of(elder, built, undated), standing),
			settleAmong(youngest, List.of(built, undated, elder), standing));
	}

	@Test
	void aVillageThatOverlapsNothingIsLeftAsItIs() {
		final NamedVillage alone = village("larkspur", 0L);

		assertEquals(alone, merger().settle(alone));
		verify(registry, never()).absorb(anyString(), anyString());
	}

	@Test
	void sweepingAnswersWithHowManyRowsTheRegistryLost() {
		final NamedVillage plain = village("larkspur", 0L);
		final NamedVillage built = village("woldmere", 0L);
		final NamedVillage third = village("marrowfen", 0L);
		when(works.get("woldmere")).thenReturn(tierFour());
		when(registry.all()).thenReturn(List.of(plain, built, third));
		when(registry.isLive("larkspur")).thenReturn(true);
		when(registry.overlapping(plain)).thenReturn(List.of(built, third));
		when(registry.size()).thenReturn(3, 1);

		assertEquals(2, merger().sweep());
	}

	@Test
	void aRowAlreadyAbsorbedIsNotSettledASecondTime() {
		final NamedVillage kept = village("larkspur", 0L);
		final NamedVillage gone = village("woldmere", 0L);
		when(registry.all()).thenReturn(List.of(kept, gone));
		when(registry.isLive("larkspur")).thenReturn(true);
		when(registry.isLive("woldmere")).thenReturn(false);
		// The absorbed id keeps answering, to its survivor, so a lookup that
		// walks the alias table finds a village here and would settle this
		// row all over again
		when(registry.byRowId("woldmere")).thenReturn(kept);
		when(registry.overlapping(kept)).thenReturn(List.of(gone));
		when(registry.overlapping(gone)).thenReturn(List.of(kept));
		when(registry.size()).thenReturn(2, 1);

		assertEquals(1, merger().sweep());

		verify(registry, never()).overlapping(gone);
		verify(registry).absorb("woldmere", "larkspur");
	}

	@Test
	void theVillageHoldingTheStoneAndTheWorksKeepsItsName() {
		// The shape of the live data: one settlement claimed three times,
		// none of the rows dated, and only one of them built on and bound
		// to a stone in a player's pocket
		final NamedVillage first = village("hawthorn", 0L);
		final NamedVillage woldmere = village("woldmere", 0L);
		final NamedVillage third = village("marrowfen", 0L);
		when(works.get("woldmere")).thenReturn(tierFour());
		when(stones.issued("woldmere")).thenReturn(true);
		when(registry.overlapping(first)).thenReturn(List.of(third, woldmere));

		assertEquals(woldmere, merger().settle(first));

		// Both of the others are folded straight into the survivor, so
		// neither leaves an alias pointing at a row that is itself gone
		verify(registry).absorb("hawthorn", "woldmere");
		verify(registry).absorb("marrowfen", "woldmere");
	}
}
