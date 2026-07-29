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
import com.adeptum.questai.reputation.Reputation;
import com.adeptum.questai.reputation.Standings;
import com.adeptum.questai.reputation.VillageReputationStore;
import com.adeptum.questai.teleport.TeleportStoneStore;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Runs against a real server so the greeting state machine is driven by the
 * same presence sweep a live server runs. The greeting itself is a fading
 * title, so what these tests pin is the state: when a player counts as
 * inside, when leaving re-arms, and when crossing swaps.
 */
class VillageNameplateTest {

	private static final double RADIUS = 48.0;

	@TempDir
	private Path tempDir;

	private ServerMock server;
	private World world;
	private VillageRegistry registry;
	private VillageReputationStore reputationStore;
	private VillageNameplate nameplate;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("village-test");

		final JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
		when(plugin.getConfig()).thenReturn(new YamlConfiguration());

		registry = new VillageRegistry(plugin, RADIUS);
		reputationStore = new VillageReputationStore(plugin);
		final VillageMerger merger = new VillageMerger(registry, reputationStore,
			new VillageWorksStore(plugin), new TeleportStoneStore(plugin),
			new CommissionStore(plugin));
		nameplate = new VillageNameplate(plugin, null, registry,
			new Standings(registry, reputationStore), merger);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	private Location at(final double x, final double z) {
		return new Location(world, x, 64, z);
	}

	private void claimRavenhollow() {
		registry.claim(VillageKey.from(world.getUID(), 0, 0), at(0, 0),
			"Ravenhollow");
	}

	/** A villager standing at this spot in the mock world. */
	private Villager point(final double x, final double z) {
		return world.spawn(new Location(world, x, 64, z), Villager.class);
	}

	/** Leaves only this crowd standing, for the next nearby-entity sweep. */
	private void stubCrowd(final Villager... crowd) {
		final List<Villager> keep = List.of(crowd);
		world.getEntitiesByClass(Villager.class).stream()
			.filter(villager -> !keep.contains(villager))
			.forEach(Entity::remove);
	}

	/** Claims a village from this spot, with these villagers about. */
	private NamedVillage claimFrom(final double x, final double z,
		final Villager... crowd) {

		stubCrowd(crowd);
		return nameplate.claimSurveyed(
			VillageKey.from(world.getUID(), (int) x, (int) z), at(x, z),
			"Ravenhollow");
	}

	private String stateOf(final PlayerMock player) {
		return nameplate.insideOf(player.getUniqueId());
	}

	@Test
	void enteringAVillageGreetsThePlayer() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(10, 10));

		nameplate.tick();

		assertEquals("Ravenhollow", stateOf(player));
	}

	@Test
	void theGreetingReadsTheStandingOfTheVillageWalkedInto() {
		claimRavenhollow();
		registry.claim(VillageKey.from(world.getUID(), 200, 0), at(200, 0),
			"Frostmere");
		final PlayerMock player = server.addPlayer();
		reputationStore.adjust(
			VillageRegistry.rowIdFor(registry.find(at(0, 0))),
			player.getUniqueId(), 30);

		final int home =
			nameplate.standingIn(registry.find(at(0, 0)), player.getUniqueId());
		final int away = nameplate.standingIn(registry.find(at(200, 0)),
			player.getUniqueId());

		assertEquals(30, home);
		assertEquals("Respected · 30", VillageNameplate.standingLine(home));
		assertEquals(0, away, "standing is held per village, not per player");
	}

	@Test
	void aVillageWithNoOpinionStillSaysSo() {
		assertEquals("Neutral · 0", VillageNameplate.standingLine(0));
		assertEquals("Disliked · -12", VillageNameplate.standingLine(-12));
		assertEquals("Hated · -60",
			VillageNameplate.standingLine(Reputation.NO_TRADE_AT - 20));
		assertEquals("Revered · 80", VillageNameplate.standingLine(80));
	}

	@Test
	void stayingInsideGreetsOnlyOnce() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(10, 10));

		nameplate.tick();
		nameplate.tick();
		nameplate.tick();

		// Still greeted under the same name; a re-show would need the
		// state to have been cleared in between, which it never was
		assertEquals("Ravenhollow", stateOf(player));
	}

	@Test
	void aPlayerOutsideIsNeverGreeted() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(500, 500));

		nameplate.tick();

		assertNull(stateOf(player));
	}

	@Test
	void leavingReArmsOnlyAfterTheDebounce() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(10, 10));
		nameplate.tick();

		player.setLocation(at(500, 500));
		nameplate.tick();
		nameplate.tick();
		// Two ticks outside: still counted as inside, no re-greet on return
		assertEquals("Ravenhollow", stateOf(player));

		nameplate.tick();
		assertNull(stateOf(player));
	}

	@Test
	void aBoundarySkirterIsNotReGreeted() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(10, 10));
		nameplate.tick();

		// Dip out for a moment, come straight back
		player.setLocation(at(500, 500));
		nameplate.tick();
		player.setLocation(at(10, 10));
		nameplate.tick();

		// The miss count must have reset rather than accumulated
		player.setLocation(at(500, 500));
		nameplate.tick();
		nameplate.tick();
		assertEquals("Ravenhollow", stateOf(player));
	}

	@Test
	void crossingBetweenVillagesSwapsWithoutPassingOutside() {
		claimRavenhollow();
		registry.claim(VillageKey.from(world.getUID(), 200, 0), at(200, 0),
			"Frostmere");
		final PlayerMock player = server.addPlayer();

		player.setLocation(at(0, 0));
		nameplate.tick();
		assertEquals("Ravenhollow", stateOf(player));

		player.setLocation(at(200, 0));
		nameplate.tick();
		assertEquals("Frostmere", stateOf(player));
	}

	@Test
	void quittingClearsTheGreetingState() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(10, 10));
		nameplate.tick();
		assertNotNull(stateOf(player));

		final PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
		when(quit.getPlayer()).thenReturn(player);
		nameplate.onPlayerQuit(quit);

		assertNull(stateOf(player));
	}

	@Test
	void disablingClearsEveryone() {
		claimRavenhollow();
		final PlayerMock first = server.addPlayer();
		final PlayerMock second = server.addPlayer();
		first.setLocation(at(0, 0));
		second.setLocation(at(5, 5));
		nameplate.tick();
		assertNotNull(stateOf(first));
		assertNotNull(stateOf(second));

		nameplate.onDisable();

		assertNull(stateOf(first));
		assertNull(stateOf(second));
	}

	@Test
	void aClaimLandsOnTheCrowdNotTheClaimant() {
		// Three villagers well north of where the player is standing
		final NamedVillage village = claimFrom(140, 135,
			point(130, 150), point(137, 161), point(150, 170));

		assertEquals(137, village.centre().x(), 0.5);
		assertEquals(161, village.centre().z(), 0.5);
	}

	@Test
	void aClaimWithNoCrowdKeepsTheClaimantsSpot() {
		final NamedVillage village = claimFrom(140, 135);

		assertEquals(140, village.centre().x(), 0.5);
		assertEquals(135, village.centre().z(), 0.5);
	}

	@Test
	void standingInAVillageDrawsItsCentreTowardTheCrowd() {
		final NamedVillage village = claimFrom(140, 135);
		stubCrowd(point(130, 150), point(137, 161), point(150, 170));
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(140, 135));

		nameplate.tick();

		final double moved = registry.byRowId(village.id()).centre().z();
		assertTrue(moved > village.centre().z() + 8,
			"expected the centre to be drawn north, got z=" + moved);
	}

	@Test
	void aVillageThatDriftsOntoAnotherTakesItIn() {
		// The live geometry: two rows 65 blocks apart on one settlement
		claimFrom(140, 135);
		claimFrom(205, 135);
		assertEquals(2, registry.size());

		stubCrowd(point(130, 150), point(137, 161), point(150, 170));
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(205, 135));

		nameplate.tick();

		assertEquals(1, registry.size());
	}

	@Test
	void theGreetingIsNotWhatCollapsesDuplicateRows() {
		// Registry hygiene rides on the merger, which the plugin drives on its
		// own. Hanging it off the greeting would mean a server that wanted no
		// titles quietly got no duplicate collapsing and no centre correction
		// either, and there is no setting that is meant to say that
		claimRavenhollow();
		registry.claim(VillageKey.from(world.getUID(), 20, 0), at(20, 0),
			"Frostmere");
		assertEquals(2, registry.size());

		nameplate.onEnable();

		assertEquals(2, registry.size(),
			"the greeting must not be the thing that folds rows together");
		nameplate.onDisable();
	}

	@Test
	void aClaimLandingOnAKnownVillageIsTakenStraightIn() {
		// A settlement wider than two claim radii: the far side is outside
		// every stored centre's reach, so standing there is enough to set a
		// second naming going even though the village is already known
		final NamedVillage first = claimFrom(0, 0);
		assertNull(registry.find(at(0, 100)), "the far side reads as unclaimed");

		final NamedVillage settled = claimFrom(0, 100, point(0, 30),
			point(5, 35), point(-5, 40));

		assertEquals(1, registry.size(),
			"a second row inside the first's reach must not be left standing");
		assertEquals(first.id(), settled.id());
		assertEquals(first.id(), registry.resolve(settled.id()));
	}

	@Test
	void aClaimOutOfEveryoneElsesReachStandsOnItsOwn() {
		claimFrom(0, 0);

		final NamedVillage far = claimFrom(400, 400);

		assertEquals(2, registry.size());
		assertTrue(registry.isLive(far.id()));
	}

	@Test
	void aSecondTickRightAfterTheFirstDoesNotReSurveyAgain() {
		// A gap wide enough that an un-throttled second tick would still
		// find more than DRIFT_THRESHOLD left to close: the first tick
		// halves it from 40 to 20, and 20 alone would still move the centre
		final NamedVillage village = claimFrom(140, 135);
		stubCrowd(point(140, 160), point(140, 175), point(140, 220));
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(140, 135));

		nameplate.tick();
		final double afterFirst = registry.byRowId(village.id()).centre().z();

		nameplate.tick();
		final double afterSecond = registry.byRowId(village.id()).centre().z();

		assertEquals(afterFirst, afterSecond, 0.001,
			"expected the second tick to be throttled, not measured again");
	}

	@Test
	void twoPlayersInOneTickDriftTheCentreOnlyOnce() {
		// Same geometry as the throttle test: a second, un-throttled
		// measurement in this one tick would still find 20 blocks to close
		final NamedVillage village = claimFrom(140, 135);
		stubCrowd(point(140, 160), point(140, 175), point(140, 220));
		final PlayerMock first = server.addPlayer();
		final PlayerMock second = server.addPlayer();
		first.setLocation(at(140, 135));
		second.setLocation(at(140, 135));

		nameplate.tick();

		assertEquals(155, registry.byRowId(village.id()).centre().z(), 0.001,
			"a second player in the same tick must not drift the centre again");
	}
}
