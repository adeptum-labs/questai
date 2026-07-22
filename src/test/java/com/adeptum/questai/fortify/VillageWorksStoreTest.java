package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
