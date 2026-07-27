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

package com.adeptum.questai.craft;

import com.adeptum.questai.SubPlugin;
import com.adeptum.questai.fortify.MaterialTally;
import com.adeptum.questai.mob.MobDrops;
import com.adeptum.questai.mob.MobForge;
import com.adeptum.questai.reputation.Standings;
import com.adeptum.questai.village.NamedVillage;
import com.adeptum.questai.village.VillageRegistry;
import com.adeptum.questai.villager.MemoryEvent;
import com.adeptum.questai.villager.VillagerProfileStore;
import java.util.UUID;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Takes payment for a commission, puts it on the village's bench, and
 * hands the finished piece over when the player comes back for it.
 *
 * <p>The price is only taken when all of it is there, so a player who
 * turns up short keeps what they carry.
 */
public class VillageCommissions implements SubPlugin {

	/** What filling a commission is worth to the village's opinion. */
	private static final int STANDING_REWARD = 3;

	private final CommissionStore store;
	private final CommissionDesk desk;
	private final VillageRegistry registry;
	private final VillagerProfileStore profileStore;
	private final MobForge forge;
	private final Standings standings;
	private final boolean enabled;
	private final double speed;

	public VillageCommissions(final JavaPlugin plugin,
		final CommissionStore store, final CommissionDesk desk,
		final Parties parties) {

		this.store = store;
		this.desk = desk;
		this.registry = parties.registry();
		this.profileStore = parties.profileStore();
		this.forge = parties.forge();
		this.standings = parties.standings();
		this.enabled = plugin.getConfig()
			.getBoolean("villages.commissions.enabled", true);
		this.speed = plugin.getConfig()
			.getDouble("villages.commissions.speed", 1.0);
	}

	/** False keeps the craftsman's entry out of the dialogue GUI. */
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void onEnable() {
		// Event registration is handled by the plugin loader
	}

	@Override
	public void onDisable() {
		// Orders live in the ledger; nothing is held in memory
	}

	/**
	 * Takes the price and starts the work. Re-checks what the player
	 * carries, since the offer screen was drawn before they clicked it.
	 */
	public void order(final Player player, final String rowId,
		final UUID craftsman, final String name) {

		final Commission commission = Commission.byName(name);
		if (commission == null || store.get(rowId, player.getUniqueId()) != null) {
			return;
		}
		if (!canPay(player, commission)) {
			player.sendMessage(
				"\u00a77You are short of what the work would take.");
			return;
		}

		commission.getRequirements().forEach((role, amount) -> MaterialTally
			.consume(player.getInventory(), commission.accepted(role), amount));

		final long now = System.currentTimeMillis();
		store.place(rowId, player.getUniqueId(), new CommissionOrder(
			commission.name(), craftsman, now,
			CommissionGate.readyAt(now, commission.getGate().minutes(), speed)));

		player.sendMessage("\u00a76Work begins on " + commission.getDisplayName()
			+ "\u00a77. Come back for it.");
		player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f,
			1.2f);
	}

	/** Hands over a finished piece, clears the bench and records the deed. */
	public void collect(final Player player, final String rowId) {
		final CommissionOrder order = store.get(rowId, player.getUniqueId());
		final Commission commission =
			order == null ? null : Commission.byName(order.commission());
		if (commission == null
			|| !CommissionGate.ready(order.readyAt(), System.currentTimeMillis())) {
			return;
		}

		for (final CommissionOutput output : commission.getOutputs()) {
			give(player, build(output));
		}
		store.clear(rowId, player.getUniqueId());
		remember(player, order, commission, rowId);

		player.sendMessage("\u00a7b" + commission.getDisplayName()
			+ "\u00a77 is yours.");
		player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f,
			1.1f);
	}

	/** Whether every role of the price is covered by what is carried. */
	private boolean canPay(final Player player, final Commission commission) {
		final ItemStack[] contents = player.getInventory().getContents();
		return commission.getRequirements().entrySet().stream().allMatch(
			need -> desk.carried(contents, commission, need.getKey())
				>= need.getValue());
	}

	/** Turns an output into a real stack, naming it where it has a name. */
	private ItemStack build(final CommissionOutput output) {
		final ItemStack item = switch (output.kind()) {
			case GEAR -> forge.buildGear(
				new MobDrops.GearRoll(output.gear(), output.enchants()));
			case MASTERWORK -> CraftsmansWhetstone.create();
			case SUPPLY -> new ItemStack(output.supply(), output.amount());
		};
		if (output.kind() == CommissionOutput.Kind.GEAR) {
			named(item, output.displayName());
		}
		return item;
	}

	private static void named(final ItemStack item, final String name) {
		final ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(name);
			item.setItemMeta(meta);
		}
	}

	/** Anything the pack will not hold is set down rather than lost. */
	private static void give(final Player player, final ItemStack item) {
		player.getInventory().addItem(item).values().forEach(rest ->
			player.getWorld().dropItemNaturally(player.getLocation(), rest));
	}

	/**
	 * The craftsman remembers the work, and the village thinks a little
	 * better of the player for having brought it.
	 */
	private void remember(final Player player, final CommissionOrder order,
		final Commission commission, final String rowId) {

		if (profileStore != null) {
			profileStore.recordEvent(order.craftsman(), player.getUniqueId(),
				MemoryEvent.Type.COMMISSION_FILLED,
				commission.getMemoryTitle());
		}
		if (standings != null && registry != null) {
			final NamedVillage village = registry.byRowId(rowId);
			standings.change(player, village, STANDING_REWARD);
		}
	}

	/**
	 * The collaborators the payout needs, bundled so the constructor stays
	 * inside the parameter limit.
	 *
	 * @param registry names the village whose opinion moves
	 * @param profileStore keeps the craftsman's memory of the work
	 * @param forge builds enchanted gear without exposing the registry
	 * @param standings records what filling the order was worth
	 */
	public record Parties(VillageRegistry registry,
		VillagerProfileStore profileStore, MobForge forge,
		Standings standings) {
	}
}
