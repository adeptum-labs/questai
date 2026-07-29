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
import com.adeptum.questai.fortify.VillageWorksStore;
import com.adeptum.questai.fortify.WorkState;
import com.adeptum.questai.reputation.Reputation;
import com.adeptum.questai.reputation.VillageReputationStore;
import com.adeptum.questai.teleport.TeleportStoneStore;
import com.adeptum.questai.villager.StoredLocation;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * Drives the survey-and-settle cycle over a world snapshot in which one
 * settlement had been walked into three times and named three times.
 *
 * <p>The ledgers and the villager positions in {@code src/test/resources/world}
 * are a verbatim copy of a played-in world. Three of its rows sit 64 to 106
 * blocks apart, all further out than the claim radius, so the merger alone
 * leaves them standing: what brings them together is each row drifting onto
 * the crowd its own survey finds, which is the cycle a player standing in the
 * village drives every {@code RESURVEY_INTERVAL_MILLIS}. A fourth row 170
 * blocks off is a different settlement and has to come through untouched.
 *
 * <p>Only the entity query is stood in for, and it is answered out of the
 * recorded villager positions through Bukkit's own {@link BoundingBox} at the
 * radius {@link VillageCrowd} asks for. Everything from the measurement
 * inward is the shipped code.
 */
class SurveyedVillageMergeTest {

	/** The default behind {@code villages.nameplate.radius}. */
	private static final double CLAIM_RADIUS = 64.0;

	private static final UUID WORLD_ID =
		UUID.fromString("a9d0d6a7-60c9-4724-bc8e-d5a3dde494f9");
	private static final String WOLDMERE = WORLD_ID + "_140_135";
	private static final String LARKSPUR = WORLD_ID + "_205_135";
	private static final String HURST = WORLD_ID + "_128_208";
	private static final String HOLLOW = WORLD_ID + "_31_0";

	/** The one player holding standing on both of the rows that merge. */
	private static final UUID PLAYER =
		UUID.fromString("00000000-0000-3000-8000-0000000000a1");

	/**
	 * Every village id found on an item in any player's inventory in the
	 * snapshot, read out of the region files. Each has to go on naming a
	 * living village or the stone carrying it stops working.
	 */
	private static final List<String> CARRIED_IDS = List.of(WOLDMERE);

	/** How many passes a stable outcome is confirmed over. */
	private static final int MAX_ROUNDS = 8;

	private static final String[] FIXTURES = {
		"village-names.yml", "village-reputation.yml", "village-works.yml",
		"teleport-stones.yml", "village-commissions.yml"};

	@Mock private JavaPlugin plugin;
	@Mock private World world;

	@TempDir
	private Path dataFolder;

	private AutoCloseable mocks;
	private MockedStatic<Bukkit> bukkit;
	private List<Entity> villagers;

	private VillageRegistry registry;
	private VillageReputationStore reputation;
	private VillageWorksStore works;
	private TeleportStoneStore stones;
	private VillageMerger merger;

	@BeforeEach
	void setUp() throws IOException {
		mocks = MockitoAnnotations.openMocks(this);
		final Logger quiet = Logger.getAnonymousLogger();
		quiet.setUseParentHandlers(false);
		when(plugin.getLogger()).thenReturn(quiet);
		when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
		when(world.getUID()).thenReturn(WORLD_ID);

		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.getWorld(WORLD_ID)).thenReturn(world);

		copyFixtures();
		villagers = recordedVillagers();
		when(world.getNearbyEntities(any(Location.class), anyDouble(),
			anyDouble(), anyDouble())).thenAnswer(call -> within(
				call.getArgument(0), call.getArgument(1),
				call.getArgument(2), call.getArgument(3)));

		registry = new VillageRegistry(plugin, CLAIM_RADIUS);
		reputation = new VillageReputationStore(plugin);
		works = new VillageWorksStore(plugin);
		stones = new TeleportStoneStore(plugin);
		merger = new VillageMerger(registry, reputation, works, stones,
			new CommissionStore(plugin));
	}

	@AfterEach
	void tearDown() throws Exception {
		bukkit.close();
		mocks.close();
	}

	@Test
	void theSnapshotStartsWithOneSettlementNamedThreeTimes() {
		assertEquals(4, registry.size());
		assertEquals("Woldmere Hamlets", registry.byRowId(WOLDMERE).name());
		assertEquals("Larkspur Hollow", registry.byRowId(LARKSPUR).name());
		assertEquals("Hawthorn Hurst", registry.byRowId(HURST).name());
		assertEquals("Hawthorn Hollow", registry.byRowId(HOLLOW).name());

		// Every pair is outside the claim radius, so the merger on its own
		// has nothing to take in and the sweep is a no-op
		for (final NamedVillage village : registry.all()) {
			assertTrue(registry.overlapping(village).isEmpty(), village.name());
		}
		final StoredLocation woldmere = registry.byRowId(WOLDMERE).centre();
		assertTrue(gapTo(woldmere, LARKSPUR) > CLAIM_RADIUS);
		assertTrue(gapTo(woldmere, HURST) > CLAIM_RADIUS);

		assertEquals(0, merger.sweep());
		assertEquals(4, registry.size());
	}

	@Test
	void surveyingTheVillageBringsItsThreeNamesTogether() {
		assertEquals(1, roundsToSettle());

		assertEquals(2, registry.size());
		assertTrue(registry.isLive(WOLDMERE));
		assertFalse(registry.isLive(LARKSPUR));
		assertFalse(registry.isLive(HURST));
	}

	@Test
	void woldmereIsTheNameTheSettlementKeeps() {
		roundsToSettle();

		assertEquals("Woldmere Hamlets", registry.byRowId(WOLDMERE).name());
		assertEquals(WOLDMERE, registry.resolve(LARKSPUR));
		assertEquals(WOLDMERE, registry.resolve(HURST));
		assertEquals("Woldmere Hamlets", registry.byRowId(LARKSPUR).name());
		assertEquals("Woldmere Hamlets", registry.byRowId(HURST).name());
	}

	@Test
	void theSettlementAcrossTheValleyIsLeftAlone() {
		roundsToSettle();

		assertTrue(registry.isLive(HOLLOW));
		assertEquals(HOLLOW, registry.resolve(HOLLOW));
		assertEquals("Hawthorn Hollow", registry.byRowId(HOLLOW).name());
	}

	@Test
	void aPlayerCarriesBothStandingsOntoTheOneRow() {
		// The grudge on Woldmere is stamped days back and two hours mend a
		// point of it, so it is spent by the time the rows meet; the goodwill
		// on Hawthorn Hurst is positive and never mends. Nothing plus three.
		assertEquals(0, Reputation.mend(-3, storedAge(WOLDMERE, PLAYER)));
		assertEquals(3, Reputation.mend(3, storedAge(HURST, PLAYER)));

		roundsToSettle();

		assertEquals(3, reputation.get(registry.resolve(WOLDMERE), PLAYER));
		assertEquals(0, reputation.playerCount(LARKSPUR));
		assertEquals(0, reputation.playerCount(HURST));
		assertEquals(2, reputation.playerCount(WOLDMERE));
	}

	@Test
	void everyStoneAlreadyInHandGoesOnNamingAVillage() {
		roundsToSettle();

		for (final String carried : CARRIED_IDS) {
			assertTrue(registry.isLive(registry.resolve(carried)), carried);
			assertNotNull(registry.byRowId(carried), carried);
		}
		assertEquals(PLAYER, stones.holderOf(registry.resolve(WOLDMERE)));
		assertTrue(stones.issued(registry.resolve(WOLDMERE)));
	}

	@Test
	void theWorksLedgerIsStillReadableUnderTheSurvivingId() {
		roundsToSettle();

		final WorkState state = works.get(registry.resolve(WOLDMERE));
		assertNotNull(state);
		assertEquals(4, state.getTier());
		assertEquals(4, state.getBuiltSites().size());
	}

	@Test
	void theOutcomeDoesNotDependOnWhichRowIsWalkedIntoFirst() {
		roundsToSettle(Comparator.comparing(NamedVillage::id).reversed());

		assertEquals(2, registry.size());
		assertTrue(registry.isLive(WOLDMERE));
		assertEquals(WOLDMERE, registry.resolve(LARKSPUR));
		assertEquals(WOLDMERE, registry.resolve(HURST));
		assertTrue(registry.isLive(HOLLOW));
		assertEquals(3, reputation.get(WOLDMERE, PLAYER));
	}

	/** How many passes moved anything before the registry came to rest. */
	private int roundsToSettle() {
		return roundsToSettle(Comparator.comparing(NamedVillage::id));
	}

	private int roundsToSettle(final Comparator<NamedVillage> order) {
		int moved = 0;
		for (int round = 1; round <= MAX_ROUNDS; round++) {
			if (!survey(order)) {
				return moved;
			}
			moved = round;
		}
		return fail("The registry never came to rest");
	}

	/**
	 * One pass of a player standing in each living village in turn, doing
	 * exactly what the greeting tick's re-survey does: draw the centre toward
	 * the crowd, then take in whatever that has brought within reach. Answers
	 * whether anything moved.
	 *
	 * <p>Rows are walked from a snapshot and asked whether they are still
	 * standing, since one can be taken in part way through the pass.
	 */
	private boolean survey(final Comparator<NamedVillage> order) {
		final List<NamedVillage> listed = new ArrayList<>(registry.all());
		listed.sort(order);

		boolean moved = false;
		for (final NamedVillage row : listed) {
			if (!registry.isLive(row.id())) {
				continue;
			}
			final NamedVillage village = registry.byRowId(row.id());
			final NamedVillage drifted = registry.recentre(village,
				VillageCrowd.measure(village.centre()));
			if (drifted != null) {
				moved = true;
				merger.settle(drifted);
			}
		}
		return moved;
	}

	/** How far a row's stored centre lies from another point. */
	private double gapTo(final StoredLocation from, final String rowId) {
		final StoredLocation to = registry.byRowId(rowId).centre();
		return Math.hypot(to.x() - from.x(), to.z() - from.z());
	}

	/** How long ago a stored standing was last written. */
	private long storedAge(final String rowId, final UUID playerId) {
		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(
			dataFolder.resolve("village-reputation.yml").toFile());
		return System.currentTimeMillis()
			- cfg.getLong("reputation." + rowId + "." + playerId + ".at");
	}

	/** The recorded villagers inside the box Bukkit would have searched. */
	private List<Entity> within(final Location centre, final double x,
		final double y, final double z) {

		final BoundingBox box = BoundingBox.of(centre, x, y, z);
		return villagers.stream()
			.filter(entity -> box.contains(entity.getLocation().toVector()))
			.toList();
	}

	private void copyFixtures() throws IOException {
		for (final String name : FIXTURES) {
			try (InputStream in = resource(name)) {
				Files.copy(in, dataFolder.resolve(name));
			}
		}
	}

	/** Every villager the snapshot recorded a standing position for. */
	private List<Entity> recordedVillagers() throws IOException {
		final YamlConfiguration cfg = new YamlConfiguration();
		try (InputStream in = resource("villager-points.yml")) {
			cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (final InvalidConfigurationException e) {
			throw new IOException(e);
		}
		final List<Entity> recorded = new ArrayList<>();
		for (final Map<?, ?> point : cfg.getMapList("points")) {
			recorded.add(villagerAt(point));
		}
		return recorded;
	}

	private Entity villagerAt(final Map<?, ?> point) {
		final Entity entity = mock(Entity.class);
		when(entity.getType()).thenReturn(EntityType.VILLAGER);
		when(entity.getLocation()).thenReturn(new Location(world,
			((Number) point.get("x")).doubleValue(),
			((Number) point.get("y")).doubleValue(),
			((Number) point.get("z")).doubleValue()));
		return entity;
	}

	private InputStream resource(final String name) throws IOException {
		final InputStream in = getClass().getClassLoader()
			.getResourceAsStream("world/" + name);
		if (in == null) {
			throw new IOException("Missing fixture world/" + name);
		}
		return in;
	}
}
