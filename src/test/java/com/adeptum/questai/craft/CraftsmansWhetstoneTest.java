package com.adeptum.questai.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The stone must be recognised by its own mark rather than its name or its
 * base material, so an ordinary brick can neither pass for one nor be
 * mistaken for one.
 */
class CraftsmansWhetstoneTest {

	@BeforeEach
	void setUp() {
		MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void aFreshStoneCarriesItsMarkAndItsModel() {
		final ItemStack stone = CraftsmansWhetstone.create();
		assertEquals(Material.BRICK, stone.getType());
		assertEquals(CraftsmansWhetstone.CMD,
			stone.getItemMeta().getCustomModelData());
		assertTrue(CraftsmansWhetstone.isWhetstone(stone));
	}

	@Test
	void anOrdinaryBrickIsNotAStone() {
		assertFalse(CraftsmansWhetstone.isWhetstone(
			new ItemStack(Material.BRICK)));
	}

	@Test
	void theModelAloneDoesNotMakeAStone() {
		final ItemStack pretender = new ItemStack(Material.BRICK);
		final ItemMeta meta = pretender.getItemMeta();
		meta.setCustomModelData(CraftsmansWhetstone.CMD);
		pretender.setItemMeta(meta);

		assertFalse(CraftsmansWhetstone.isWhetstone(pretender),
			"a brick wearing the model should not hone anything");
	}

	@Test
	void nothingAtAllIsNotAStone() {
		assertFalse(CraftsmansWhetstone.isWhetstone(null));
		assertFalse(CraftsmansWhetstone.isWhetstone(
			new ItemStack(Material.STONE)));
	}
}
