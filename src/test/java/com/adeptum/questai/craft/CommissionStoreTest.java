package com.adeptum.questai.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class CommissionStoreTest {

	private static final String ROW = "world_100_100";
	private static final String OTHER_ROW = "world_900_900";
	private static final UUID PLAYER = UUID.randomUUID();
	private static final UUID OTHER_PLAYER = UUID.randomUUID();
	private static final UUID SMITH = UUID.randomUUID();
	private static final long NOON = 1_700_000_000_000L;

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

	private static CommissionOrder order(final Commission commission) {
		return new CommissionOrder(commission.name(), SMITH, NOON,
			NOON + 60_000);
	}

	@Test
	void aVillageWithNoWorkInHandHasNothingToShow(@TempDir final Path folder) {
		assertNull(new CommissionStore(pluginIn(folder)).get(ROW, PLAYER));
	}

	@Test
	void anOrderSurvivesAReload(@TempDir final Path folder) {
		new CommissionStore(pluginIn(folder))
			.place(ROW, PLAYER, order(Commission.KEEN_BLADE));

		final CommissionOrder reloaded =
			new CommissionStore(pluginIn(folder)).get(ROW, PLAYER);
		assertNotNull(reloaded);
		assertEquals(Commission.KEEN_BLADE.name(), reloaded.commission());
		assertEquals(SMITH, reloaded.craftsman());
		assertEquals(NOON, reloaded.placedAt());
		assertEquals(NOON + 60_000, reloaded.readyAt());
	}

	@Test
	void aSecondOrderReplacesTheFirst(@TempDir final Path folder) {
		final CommissionStore store = new CommissionStore(pluginIn(folder));
		store.place(ROW, PLAYER, order(Commission.KEEN_BLADE));
		store.place(ROW, PLAYER, order(Commission.PLATED_HELM));

		assertEquals(Commission.PLATED_HELM.name(),
			store.get(ROW, PLAYER).commission());
	}

	@Test
	void clearingTheShelfSticks(@TempDir final Path folder) {
		final CommissionStore store = new CommissionStore(pluginIn(folder));
		store.place(ROW, PLAYER, order(Commission.KEEN_BLADE));
		store.clear(ROW, PLAYER);

		assertNull(store.get(ROW, PLAYER));
		assertNull(new CommissionStore(pluginIn(folder)).get(ROW, PLAYER));
	}

	@Test
	void clearingWhatWasNeverThereIsHarmless(@TempDir final Path folder) {
		final CommissionStore store = new CommissionStore(pluginIn(folder));
		store.clear(ROW, PLAYER);
		assertNull(store.get(ROW, PLAYER));
	}

	@Test
	void villagesAndPlayersKeepTheirOwnShelves(@TempDir final Path folder) {
		final CommissionStore store = new CommissionStore(pluginIn(folder));
		store.place(ROW, PLAYER, order(Commission.KEEN_BLADE));
		store.place(ROW, OTHER_PLAYER, order(Commission.PLATED_HELM));
		store.place(OTHER_ROW, PLAYER, order(Commission.QUIVER_OF_ARROWS));

		assertEquals(Commission.KEEN_BLADE.name(),
			store.get(ROW, PLAYER).commission());
		assertEquals(Commission.PLATED_HELM.name(),
			store.get(ROW, OTHER_PLAYER).commission());
		assertEquals(Commission.QUIVER_OF_ARROWS.name(),
			store.get(OTHER_ROW, PLAYER).commission());
	}

	@Test
	void aPieceWeNoLongerMakeIsDropped(@TempDir final Path folder)
		throws Exception {

		writeRows(folder, "  " + ROW + ":\n"
			+ "    " + PLAYER + ":\n"
			+ "      commission: A_PIECE_WE_NO_LONGER_MAKE\n"
			+ "      craftsman: " + SMITH + "\n"
			+ "      placedAt: " + NOON + "\n"
			+ "      readyAt: " + NOON + "\n");

		assertNull(new CommissionStore(pluginIn(folder)).get(ROW, PLAYER));
	}

	@Test
	void aCorruptTimestampDropsOnlyItsOwnVillage(@TempDir final Path folder)
		throws Exception {

		writeRows(folder, "  " + ROW + ":\n"
			+ "    " + PLAYER + ":\n"
			+ "      commission: KEEN_BLADE\n"
			+ "      craftsman: " + SMITH + "\n"
			+ "      placedAt: " + NOON + "\n"
			+ "      readyAt: soon\n"
			+ "  " + OTHER_ROW + ":\n"
			+ "    " + PLAYER + ":\n"
			+ "      commission: QUIVER_OF_ARROWS\n"
			+ "      craftsman: " + SMITH + "\n"
			+ "      placedAt: " + NOON + "\n"
			+ "      readyAt: " + NOON + "\n");

		final CommissionStore store = new CommissionStore(pluginIn(folder));
		assertNull(store.get(ROW, PLAYER));
		assertNotNull(store.get(OTHER_ROW, PLAYER),
			"one bad row should not poison the ledger");
	}

	private static void writeRows(final Path folder, final String rows)
		throws Exception {

		final File file = new File(folder.toFile(), "village-commissions.yml");
		Files.writeString(file.toPath(), "commissions:\n" + rows);
	}
}
