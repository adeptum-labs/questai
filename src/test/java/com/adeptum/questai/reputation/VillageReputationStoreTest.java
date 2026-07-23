package com.adeptum.questai.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VillageReputationStoreTest {

	private static final String ROW = "world_100_100";
	private static final UUID PLAYER = UUID.randomUUID();
	private static final long NOON = 1_700_000_000_000L;
	private static final long TWO_HOURS = 2L * 60 * 60 * 1000;

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
	void strangersStartAtZero(@TempDir final Path folder) {
		assertEquals(0,
			new VillageReputationStore(pluginIn(folder)).get(ROW, PLAYER));
	}

	@Test
	void deedsAccumulateAndSurviveAReload(@TempDir final Path folder) {
		final VillageReputationStore store =
			new VillageReputationStore(pluginIn(folder));
		store.adjustAt(ROW, PLAYER, 5, NOON);
		store.adjustAt(ROW, PLAYER, 10, NOON);

		final VillageReputationStore reloaded =
			new VillageReputationStore(pluginIn(folder));
		assertEquals(15, reloaded.getAt(ROW, PLAYER, NOON));
	}

	@Test
	void standingIsClampedAtBothEnds(@TempDir final Path folder) {
		final VillageReputationStore store =
			new VillageReputationStore(pluginIn(folder));
		assertEquals(-100, store.adjustAt(ROW, PLAYER, -250, NOON));
		assertEquals(100, store.adjustAt(ROW, PLAYER, 500, NOON));
	}

	@Test
	void grudgesMendWhileNobodyIsLooking(@TempDir final Path folder) {
		final VillageReputationStore store =
			new VillageReputationStore(pluginIn(folder));
		store.adjustAt(ROW, PLAYER, -10, NOON);

		assertEquals(-7, store.getAt(ROW, PLAYER, NOON + 3 * TWO_HOURS));
		// Reading never writes; the stored value still mends from NOON
		assertEquals(-6, store.getAt(ROW, PLAYER, NOON + 4 * TWO_HOURS));
	}

	@Test
	void adjustingMendsFirstThenApplies(@TempDir final Path folder) {
		final VillageReputationStore store =
			new VillageReputationStore(pluginIn(folder));
		store.adjustAt(ROW, PLAYER, -10, NOON);

		assertEquals(-2,
			store.adjustAt(ROW, PLAYER, 5, NOON + 3 * TWO_HOURS));
	}

	@Test
	void separateVillagesAndPlayersKeepSeparateLedgers(
		@TempDir final Path folder) {

		final UUID other = UUID.randomUUID();
		final VillageReputationStore store =
			new VillageReputationStore(pluginIn(folder));
		store.adjustAt(ROW, PLAYER, 20, NOON);
		store.adjustAt("world_500_500", PLAYER, -20, NOON);

		assertEquals(20, store.getAt(ROW, PLAYER, NOON));
		assertEquals(-20, store.getAt("world_500_500", PLAYER, NOON));
		assertEquals(0, store.getAt(ROW, other, NOON));
	}

	@Test
	void oneMalformedEntryDoesNotPoisonTheRest(@TempDir final Path folder)
		throws Exception {

		final VillageReputationStore store =
			new VillageReputationStore(pluginIn(folder));
		store.adjustAt(ROW, PLAYER, 7, NOON);

		final File file = new File(folder.toFile(), "village-reputation.yml");
		final String text = Files.readString(file.toPath());
		Files.writeString(file.toPath(), text
			+ "\n  broken:\n    " + UUID.randomUUID()
			+ ":\n      value: not-a-number\n      at: 1\n");

		final VillageReputationStore reloaded =
			new VillageReputationStore(pluginIn(folder));
		assertEquals(7, reloaded.getAt(ROW, PLAYER, NOON));
		assertEquals(0, reloaded.getAt("broken", PLAYER, NOON));
	}
}
