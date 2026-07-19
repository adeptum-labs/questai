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

package com.adeptum.questai.quest;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryPackageTest {

	private ItemStack packageFor(final String recipientName) {
		final ItemStack item = mock(ItemStack.class);
		final ItemMeta meta = mock(ItemMeta.class);
		when(item.getType()).thenReturn(Material.BUNDLE);
		when(item.getItemMeta()).thenReturn(meta);
		when(meta.displayName()).thenReturn(
			Component.text("§6Sealed Package for " + recipientName));
		return item;
	}

	@Test
	void matchesTheRightRecipient() {
		assertTrue(DeliveryPackage.matches(
			packageFor("Mira Bloom"), "Mira Bloom"));
	}

	@Test
	void rejectsOtherRecipientsAndItems() {
		assertFalse(DeliveryPackage.matches(
			packageFor("Mira Bloom"), "Edric Stone"));
		assertFalse(DeliveryPackage.matches(null, "Mira Bloom"));

		final ItemStack sword = mock(ItemStack.class);
		when(sword.getType()).thenReturn(Material.IRON_SWORD);
		assertFalse(DeliveryPackage.matches(sword, "Mira Bloom"));

		final ItemStack bareBundle = mock(ItemStack.class);
		when(bareBundle.getType()).thenReturn(Material.BUNDLE);
		assertFalse(DeliveryPackage.matches(bareBundle, "Mira Bloom"));
	}

	@Test
	void removeOneClearsExactlyOneMatchingSlot() {
		final ItemStack first = packageFor("Mira Bloom");
		final ItemStack second = packageFor("Mira Bloom");
		final Inventory inventory = mock(Inventory.class);
		when(inventory.getSize()).thenReturn(3);
		when(inventory.getItem(0)).thenReturn(null);
		when(inventory.getItem(1)).thenReturn(first);
		when(inventory.getItem(2)).thenReturn(second);

		assertTrue(DeliveryPackage.removeOne(inventory, "Mira Bloom"));
		verify(inventory).setItem(1, null);
		verify(inventory, never()).setItem(eq(2), any());
	}

	@Test
	void removeOneReturnsFalseWithoutPackage() {
		final Inventory inventory = mock(Inventory.class);
		when(inventory.getSize()).thenReturn(2);

		assertFalse(DeliveryPackage.removeOne(inventory, "Mira Bloom"));
		verify(inventory, never()).setItem(anyInt(), any());
	}
}
