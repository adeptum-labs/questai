package com.adeptum.questai.dialogue;

import com.adeptum.questai.craft.Commission;
import com.adeptum.questai.craft.CommissionDesk;
import com.adeptum.questai.craft.CommissionOrder;
import com.adeptum.questai.craft.CommissionStore;
import com.adeptum.questai.fortify.VillageWorksStore;
import com.adeptum.questai.fortify.WorkState;
import com.adeptum.questai.reputation.Standings;
import java.lang.reflect.Method;
import java.util.UUID;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A craftsman only offers what the village has built up to and the player
 * has earned, only offers at all while nothing of theirs is on the bench,
 * and hands over only what is finished.
 */
class ConversationManagerCommissionTest {

	private static final String ROW_ID = "world_10_20";
	private static final String SMITH = "WEAPONSMITH";
	private static final long MINUTE = 60L * 1000;

	private ServerMock server;
	private ConversationManager manager;
	private CommissionStore store;
	private VillageWorksStore worksStore;
	private Standings standings;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		store = mock(CommissionStore.class);
		worksStore = mock(VillageWorksStore.class);
		standings = mock(Standings.class);
		manager = new ConversationManager(null, null, null);
		manager.setCommissionDesk(
			new CommissionDesk(store, worksStore, standings));
		manager.setCommissionOrderHandler((p, row, smith, piece) -> { });
		manager.setCommissionCollectHandler((p, row) -> { });
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	private ConversationState stateFor(final String profession) {
		final ConversationState state = ConversationState.builder()
			.phase(ConversationPhase.OPTIONS)
			.npcProfession(profession)
			.build();
		state.setVillageRowId(ROW_ID);
		return state;
	}

	private Object call(final String name, final PlayerMock player,
		final ConversationState state) {

		try {
			final Method method = ConversationManager.class.getDeclaredMethod(
				name, org.bukkit.entity.Player.class, ConversationState.class);
			method.setAccessible(true);
			return method.invoke(manager, player, state);
		} catch (final ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private void village(final int tier, final int rep) {
		final WorkState works = new WorkState();
		works.setTier(tier);
		when(worksStore.get(ROW_ID)).thenReturn(works);
		when(standings.of(eq(ROW_ID), any(UUID.class))).thenReturn(rep);
	}

	private void onTheBench(final Commission piece, final long readyAt) {
		when(store.get(eq(ROW_ID), any(UUID.class))).thenReturn(
			new CommissionOrder(piece.name(), UUID.randomUUID(), 0L, readyAt));
	}

	@Test
	void theSmithOffersWhatTheVillageHasBuiltUpTo() {
		village(1, 25);
		assertEquals(Commission.KEEN_BLADE,
			call("commissionOffer", server.addPlayer(), stateFor(SMITH)));
	}

	@Test
	void anUnbuiltVillageHasNothingForTheSmithToOffer() {
		village(0, 25);
		assertNull(call("commissionOffer", server.addPlayer(), stateFor(SMITH)));
	}

	@Test
	void aSouredVillageOffersNothing() {
		village(4, -50);
		assertNull(call("commissionOffer", server.addPlayer(), stateFor(SMITH)));
	}

	@Test
	void anIdleVillagerOffersNothing() {
		village(4, 100);
		assertNull(
			call("commissionOffer", server.addPlayer(), stateFor("NITWIT")));
	}

	@Test
	void aPieceStillOnTheBenchIsNotYetCollectable() {
		village(1, 25);
		onTheBench(Commission.KEEN_BLADE, System.currentTimeMillis() + MINUTE);
		final PlayerMock player = server.addPlayer();

		assertNotNull(call("openCommission", player, stateFor(SMITH)));
		assertEquals(false,
			call("canCollectCommission", player, stateFor(SMITH)));
	}

	@Test
	void aFinishedPieceIsCollectableFromTheTradeThatMadeIt() {
		village(1, 25);
		onTheBench(Commission.KEEN_BLADE, System.currentTimeMillis() - MINUTE);

		assertEquals(true, call("canCollectCommission", server.addPlayer(),
			stateFor(SMITH)));
	}

	@Test
	void anotherTradeWillNotHandOverAFreshPiece() {
		village(1, 25);
		onTheBench(Commission.KEEN_BLADE, System.currentTimeMillis() - MINUTE);

		assertEquals(false, call("canCollectCommission", server.addPlayer(),
			stateFor("FARMER")));
	}

	@Test
	void anUnwiredDeskDrawsNothingAtAll() {
		village(4, 100);
		final ConversationManager bare =
			new ConversationManager(null, null, null);
		manager = bare;

		assertNull(call("commissionOffer", server.addPlayer(), stateFor(SMITH)));
		assertEquals(false, call("canCollectCommission", server.addPlayer(),
			stateFor(SMITH)));
	}

	@Test
	void anUnnamedVillageKeepsItsCraftsmenQuiet() {
		village(4, 100);
		final ConversationState nameless = ConversationState.builder()
			.phase(ConversationPhase.OPTIONS)
			.npcProfession(SMITH)
			.build();

		assertNull(call("commissionOffer", server.addPlayer(), nameless));
		assertEquals(false,
			call("canCollectCommission", server.addPlayer(), nameless));
	}
}
