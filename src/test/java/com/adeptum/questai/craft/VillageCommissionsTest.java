package com.adeptum.questai.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adeptum.questai.fortify.VillageWorksStore;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The counter must never take a price it cannot fill, and never fill one
 * it has not taken. Only supply commissions are exercised here: building
 * gear runs through the forge, which owns the enchantment registry and
 * cannot be loaded off-server.
 */
class VillageCommissionsTest {

	private static final String ROW = "world_10_20";
	private static final UUID SMITH = UUID.randomUUID();

	private ServerMock server;
	private CommissionStore store;
	private VillageCommissions commissions;

	@BeforeEach
	void setUp(@TempDir final Path folder) {
		server = MockBukkit.mock();
		final JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getDataFolder()).thenReturn(folder.toFile());
		when(plugin.getConfig()).thenReturn(new YamlConfiguration());
		final Logger quiet = Logger.getAnonymousLogger();
		quiet.setUseParentHandlers(false);
		when(plugin.getLogger()).thenReturn(quiet);

		store = new CommissionStore(plugin);
		final CommissionDesk desk = new CommissionDesk(store,
			mock(VillageWorksStore.class), null);
		commissions = new VillageCommissions(plugin, store, desk,
			new VillageCommissions.Parties(null, null, null, null));
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	/** Arrows: flint 16, feathers 16, logs 8. */
	private static void payFor(final PlayerMock player) {
		player.getInventory().addItem(new ItemStack(Material.FLINT, 16));
		player.getInventory().addItem(new ItemStack(Material.FEATHER, 16));
		player.getInventory().addItem(new ItemStack(Material.OAK_LOG, 8));
	}

	private void orderArrows(final PlayerMock player) {
		commissions.order(player, ROW, SMITH,
			Commission.QUIVER_OF_ARROWS.name());
	}

	private static int count(final PlayerMock player, final Material material) {
		int total = 0;
		for (final ItemStack item : player.getInventory().getContents()) {
			if (item != null && item.getType() == material) {
				total += item.getAmount();
			}
		}
		return total;
	}

	@Test
	void thePriceIsTakenAndTheWorkBegins() {
		final PlayerMock player = server.addPlayer();
		payFor(player);
		orderArrows(player);

		assertNotNull(store.get(ROW, player.getUniqueId()));
		assertEquals(0, count(player, Material.FLINT));
		assertEquals(0, count(player, Material.FEATHER));
		assertEquals(0, count(player, Material.OAK_LOG));
	}

	@Test
	void aPlayerWhoTurnsUpShortKeepsWhatTheyCarry() {
		final PlayerMock player = server.addPlayer();
		player.getInventory().addItem(new ItemStack(Material.FLINT, 16));
		player.getInventory().addItem(new ItemStack(Material.FEATHER, 4));
		orderArrows(player);

		assertNull(store.get(ROW, player.getUniqueId()),
			"no order should be taken that cannot be paid for");
		assertEquals(16, count(player, Material.FLINT));
		assertEquals(4, count(player, Material.FEATHER));
	}

	@Test
	void aSecondOrderIsRefusedWhileOneIsOnTheBench() {
		final PlayerMock player = server.addPlayer();
		payFor(player);
		orderArrows(player);
		payFor(player);
		orderArrows(player);

		assertEquals(16, count(player, Material.FLINT),
			"the second price should not have been taken");
	}

	@Test
	void anUnfinishedPieceIsNotHandedOver() {
		final PlayerMock player = server.addPlayer();
		payFor(player);
		orderArrows(player);
		commissions.collect(player, ROW);

		assertEquals(0, count(player, Material.ARROW));
		assertNotNull(store.get(ROW, player.getUniqueId()),
			"the bench should not be cleared before the piece is done");
	}

	@Test
	void aFinishedPieceIsHandedOverAndTheBenchCleared() {
		final PlayerMock player = server.addPlayer();
		store.place(ROW, player.getUniqueId(), new CommissionOrder(
			Commission.QUIVER_OF_ARROWS.name(), SMITH, 0L, 1L));

		commissions.collect(player, ROW);

		assertEquals(64, count(player, Material.ARROW));
		assertNull(store.get(ROW, player.getUniqueId()));
	}

	@Test
	void aPieceCannotBeCollectedTwice() {
		final PlayerMock player = server.addPlayer();
		store.place(ROW, player.getUniqueId(), new CommissionOrder(
			Commission.QUIVER_OF_ARROWS.name(), SMITH, 0L, 1L));

		commissions.collect(player, ROW);
		commissions.collect(player, ROW);

		assertEquals(64, count(player, Material.ARROW),
			"a stale screen should not pay out a second time");
	}
}
