package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class MaterialTallyTest {

	private static final Set<Material> LOGS =
		Set.of(Material.OAK_LOG, Material.SPRUCE_LOG);

	@BeforeEach
	void setUp() {
		MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	/** A stub inventory backed by a plain array, so no server is needed. */
	private static Inventory inventoryOf(final ItemStack... items) {
		final ItemStack[] slots = items.clone();
		final Inventory inventory = mock(Inventory.class);
		when(inventory.getSize()).thenReturn(slots.length);
		when(inventory.getItem(anyInt())).thenAnswer(call ->
			slots[call.getArgument(0, Integer.class)]);
		when(inventory.getContents()).thenReturn(slots);
		org.mockito.Mockito.doAnswer(call -> {
			slots[call.getArgument(0, Integer.class)] =
				call.getArgument(1, ItemStack.class);
			return null;
		}).when(inventory).setItem(anyInt(), org.mockito.ArgumentMatchers.any());
		return inventory;
	}

	@Test
	void countsAcrossScatteredPartialStacks() {
		final ItemStack[] contents = {
			new ItemStack(Material.OAK_LOG, 12),
			null,
			new ItemStack(Material.STONE, 64),
			new ItemStack(Material.SPRUCE_LOG, 5),
		};
		assertEquals(17, MaterialTally.count(contents, LOGS));
	}

	@Test
	void countsNothingInAnEmptyInventory() {
		assertEquals(0, MaterialTally.count(new ItemStack[] {null, null}, LOGS));
	}

	@Test
	void consumesOnlyWhatIsWanted() {
		final Inventory inventory =
			inventoryOf(new ItemStack(Material.OAK_LOG, 64));

		assertEquals(4, MaterialTally.consume(inventory, LOGS, 4));
		assertEquals(60, inventory.getItem(0).getAmount());
	}

	@Test
	void consumesAcrossSlotsAndClearsEmptiedOnes() {
		final Inventory inventory = inventoryOf(
			new ItemStack(Material.OAK_LOG, 10),
			new ItemStack(Material.SPRUCE_LOG, 10));

		assertEquals(15, MaterialTally.consume(inventory, LOGS, 15));
		assertNull(inventory.getItem(0));
		assertEquals(5, inventory.getItem(1).getAmount());
	}

	@Test
	void consumesWhatItCanWhenShort() {
		final Inventory inventory =
			inventoryOf(new ItemStack(Material.OAK_LOG, 3));

		assertEquals(3, MaterialTally.consume(inventory, LOGS, 99));
		assertNull(inventory.getItem(0));
	}

	@Test
	void leavesUnacceptedMaterialAlone() {
		final Inventory inventory =
			inventoryOf(new ItemStack(Material.STONE, 64));

		assertEquals(0, MaterialTally.consume(inventory, LOGS, 10));
		assertEquals(64, inventory.getItem(0).getAmount());
	}

	@Test
	void consumingZeroTakesNothing() {
		final Inventory inventory =
			inventoryOf(new ItemStack(Material.OAK_LOG, 64));

		assertEquals(0, MaterialTally.consume(inventory, LOGS, 0));
		assertEquals(64, inventory.getItem(0).getAmount());
	}
}
