package com.adeptum.questai.fortify;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Counting and taking donated material across a whole inventory.
 *
 * <p>The existing helpers cannot do this: a delivery package clears one slot
 * outright and a star fragment decrements the main hand. A donation is a
 * quantity spread over any number of partial stacks.
 */
public final class MaterialTally {

	private MaterialTally() {
	}

	/** How much accepted material the contents hold in total. */
	public static int count(final ItemStack[] contents,
		final Set<Material> accepted) {

		int total = 0;
		for (final ItemStack item : contents) {
			if (item != null && accepted.contains(item.getType())) {
				total += item.getAmount();
			}
		}
		return total;
	}

	/**
	 * Takes up to {@code wanted} accepted items and returns how many were
	 * actually taken. Never takes more than asked, so a full stack offered
	 * against a small shortfall only loses the shortfall.
	 */
	public static int consume(final Inventory inventory,
		final Set<Material> accepted, final int wanted) {

		int remaining = wanted;
		for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
			final ItemStack item = inventory.getItem(slot);
			if (item == null || !accepted.contains(item.getType())) {
				continue;
			}

			final int taken = Math.min(item.getAmount(), remaining);
			remaining -= taken;
			if (taken == item.getAmount()) {
				inventory.setItem(slot, null);
			} else {
				item.setAmount(item.getAmount() - taken);
				inventory.setItem(slot, item);
			}
		}
		return wanted - remaining;
	}
}
