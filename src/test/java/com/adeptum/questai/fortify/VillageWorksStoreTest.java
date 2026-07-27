package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adeptum.questai.villager.StoredLocation;
import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VillageWorksStoreTest {

	private static final String ROW = "world_100_100";

	private static JavaPlugin pluginIn(final Path folder) {
		final JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getDataFolder()).thenReturn(folder.toFile());
		// Anonymous and detached so the malformed-row warning this suite
		// deliberately provokes does not land in the build log
		final Logger quiet = Logger.getAnonymousLogger();
		quiet.setUseParentHandlers(false);
		when(plugin.getLogger()).thenReturn(quiet);
		return plugin;
	}

	@Test
	void donationsAccumulateAndSurviveAReload(@TempDir final Path folder) {
		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.donate(ROW, "logs", 12);
		store.donate(ROW, "logs", 20);
		store.donate(ROW, "rough_stone", 5);

		final VillageWorksStore reloaded = new VillageWorksStore(pluginIn(folder));
		final WorkState state = reloaded.get(ROW);
		assertNotNull(state);
		assertEquals(32, state.getTally().get("logs"));
		assertEquals(5, state.getTally().get("rough_stone"));
		assertEquals(0, state.getTier());
	}

	@Test
	void unknownVillageHasNoState(@TempDir final Path folder) {
		assertNull(new VillageWorksStore(pluginIn(folder)).get("nowhere"));
	}

	@Test
	void buildProgressSurvivesAReload(@TempDir final Path folder) {
		final UUID world = UUID.randomUUID();
		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.donate(ROW, "logs", 1);
		store.beginBuild(ROW, new StoredLocation(world, 8, 64, 16), 2);
		store.advanceStage(ROW, 1_700_000_000_000L);

		final WorkState state = new VillageWorksStore(pluginIn(folder)).get(ROW);
		assertEquals(1, state.getStage());
		assertEquals(1_700_000_000_000L, state.getStageAt());
		assertEquals(2, state.getRotation());
		assertEquals(world, state.getSite().worldId());
		assertEquals(16, state.getSite().z());
	}

	@Test
	void completingATierClearsTheTallyAndSite(@TempDir final Path folder) {
		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.donate(ROW, "logs", 32);
		store.beginBuild(ROW, new StoredLocation(UUID.randomUUID(), 1, 2, 3), 0);
		store.completeTier(ROW);

		final WorkState state = store.get(ROW);
		assertEquals(1, state.getTier());
		assertEquals(0, state.getStage());
		assertNull(state.getSite());
		assertEquals(0, state.getTally().size());
	}

	@Test
	void aCompletedTierKeepsItsSiteOnRecord(@TempDir final Path folder) {
		final UUID world = UUID.randomUUID();
		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.beginBuild(ROW, new StoredLocation(world, 40, 65, -12), 3);
		store.completeTier(ROW);

		final WorkState reloaded =
			new VillageWorksStore(pluginIn(folder)).get(ROW);
		final WorkState.BuiltSite built = reloaded.getBuiltSites().get(0);
		assertNotNull(built, "the finished tier lost its site");
		assertEquals(world, built.origin().worldId());
		assertEquals(40, built.origin().x());
		assertEquals(-12, built.origin().z());
		assertEquals(3, built.rotation());
	}

	@Test
	void oneMalformedRowDoesNotPoisonTheRest(@TempDir final Path folder)
		throws Exception {

		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.donate(ROW, "logs", 7);

		final File file = new File(folder.toFile(), "village-works.yml");
		final String text = java.nio.file.Files.readString(file.toPath());
		java.nio.file.Files.writeString(file.toPath(),
			text + "\n  broken:\n    tier: not-a-number\n");

		final VillageWorksStore reloaded = new VillageWorksStore(pluginIn(folder));
		assertEquals(7, reloaded.get(ROW).getTally().get("logs"));
		assertNull(reloaded.get("broken"));
	}

	@Test
	void aCorruptStageTimestampSkipsTheRow(@TempDir final Path folder)
		throws Exception {

		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.donate(ROW, "logs", 7);

		final File file = new File(folder.toFile(), "village-works.yml");
		final String text = java.nio.file.Files.readString(file.toPath());
		java.nio.file.Files.writeString(file.toPath(),
			text + "\n  broken2:\n    stageAt: not-a-number\n");

		final VillageWorksStore reloaded = new VillageWorksStore(pluginIn(folder));
		assertEquals(7, reloaded.get(ROW).getTally().get("logs"));
		assertNull(reloaded.get("broken2"));
	}

	@Test
	void aCorruptSiteCoordinateSkipsTheRow(@TempDir final Path folder)
		throws Exception {

		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.donate(ROW, "logs", 7);

		final File file = new File(folder.toFile(), "village-works.yml");
		final String text = java.nio.file.Files.readString(file.toPath());
		java.nio.file.Files.writeString(file.toPath(), text
			+ "\n  broken3:\n    site:\n      world: " + UUID.randomUUID()
			+ "\n      x: not-a-number\n      y: 64\n      z: 0\n");

		final VillageWorksStore reloaded = new VillageWorksStore(pluginIn(folder));
		assertEquals(7, reloaded.get(ROW).getTally().get("logs"));
		assertNull(reloaded.get("broken3"));
	}

	@Test
	void palisadeFrontsAndGapsSurviveAReload(@TempDir final Path folder) {
		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.donate(ROW, "logs", 1);
		store.advanceFronts(ROW, 12, 9, 1_700_000_000_000L);
		store.recordGap(ROW, 40);
		store.recordGap(ROW, 41);
		store.recordGap(ROW, 40);

		final WorkState state = new VillageWorksStore(pluginIn(folder)).get(ROW);
		assertEquals(12, state.getFrontForward());
		assertEquals(9, state.getFrontBackward());
		assertEquals(1_700_000_000_000L, state.getStageAt());
		assertEquals(java.util.List.of(40, 41), state.getRingGaps());
	}

	@Test
	void mendingAGapForgetsThatSlotAndNothingElse(@TempDir final Path folder) {
		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.donate(ROW, "logs", 1);
		store.recordGap(ROW, 40);
		store.recordGap(ROW, 41);
		// A slot nobody skipped, then one that reads as a list position
		store.clearGap(ROW, 99);
		store.clearGap(ROW, 1);
		assertEquals(java.util.List.of(40, 41), store.get(ROW).getRingGaps());

		store.clearGap(ROW, 40);
		final WorkState state = new VillageWorksStore(pluginIn(folder)).get(ROW);
		assertEquals(java.util.List.of(41), state.getRingGaps());
	}

	@Test
	void mendingTheLastGapLeavesTheRingWithNone(@TempDir final Path folder) {
		final VillageWorksStore store = new VillageWorksStore(pluginIn(folder));
		store.donate(ROW, "logs", 1);
		store.recordGap(ROW, 7);
		store.clearGap(ROW, 7);

		assertTrue(new VillageWorksStore(pluginIn(folder))
			.get(ROW).getRingGaps().isEmpty());
	}
}
