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

package com.adeptum.questai.teleport;

import com.adeptum.questai.event.VillageKey;
import com.adeptum.questai.village.NamedVillage;
import com.adeptum.questai.village.VillageRegistry;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A stone carries the village id it was bound to on the day it was handed
 * over, and never learns that its village has since been taken into another.
 * Every book the teleport package keeps has therefore to be filed under the
 * id that is still standing rather than the one on the item, or a stone in a
 * pocket writes a row nothing will ever look at again while the living
 * village goes on believing its own stone is out there.
 */
class VillageTeleportStonesTest {

	private static final double RADIUS = 64.0;

	@TempDir
	private Path tempDir;

	private ServerMock server;
	private WorldMock world;
	private VillageRegistry registry;
	private TeleportStoneStore store;
	private VillageTeleportStones stones;

	/** The row that kept its name, and the one it took in. */
	private NamedVillage survivor;
	private String absorbedId;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("stone-test");

		final JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		final Logger quiet = Logger.getAnonymousLogger();
		quiet.setUseParentHandlers(false);
		when(plugin.getLogger()).thenReturn(quiet);
		when(plugin.getConfig()).thenReturn(new YamlConfiguration());

		registry = new VillageRegistry(plugin, RADIUS);
		survivor = registry.claim(VillageKey.from(world.getUID(), 0, 0),
			at(0, 0), "Woldmere Hamlets");
		final NamedVillage absorbed = registry.claim(
			VillageKey.from(world.getUID(), 30, 0), at(30, 0), "Larkspur Hollow");
		absorbedId = absorbed.id();
		registry.absorb(absorbedId, survivor.id());

		store = new TeleportStoneStore(plugin);
		stones = new VillageTeleportStones(plugin, registry, store);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	private Location at(final double x, final double z) {
		return new Location(world, x, 64, z);
	}

	/** A stone bound to the row that was taken in. */
	private ItemStack absorbedStone() {
		return VillageTeleportStone.create(absorbedId, "Larkspur Hollow", 120);
	}

	/** The removal of an item entity carrying this stone, for this reason. */
	private EntityRemoveEvent removalOf(final ItemStack stack,
		final EntityRemoveEvent.Cause cause) {

		final Item entity = mock(Item.class);
		when(entity.getItemStack()).thenReturn(stack);
		final EntityRemoveEvent event = mock(EntityRemoveEvent.class);
		when(event.getEntity()).thenReturn(entity);
		when(event.getCause()).thenReturn(cause);
		return event;
	}

	@Test
	void aStoneBoundToAnAbsorbedRowStillCountsAgainstItsVillage() {
		final PlayerMock player = server.addPlayer();
		store.issue(survivor.id(), player.getUniqueId());

		assertTrue(stones.stoneIssued(absorbedId),
			"the village whose stone this is must not be offered a second");
	}

	@Test
	void destroyingAStoneBoundToAnAbsorbedRowFreesItsVillage() {
		final PlayerMock player = server.addPlayer();
		store.issue(survivor.id(), player.getUniqueId());

		stones.onEntityRemove(
			removalOf(absorbedStone(), EntityRemoveEvent.Cause.DESPAWN));

		assertFalse(store.issued(survivor.id()),
			"a destroyed stone has to free the village that issued it");
		assertFalse(stones.stoneIssued(survivor.id()));
	}

	@Test
	void anItemPickedUpIsStillNotADestroyedStone() {
		final PlayerMock player = server.addPlayer();
		store.issue(survivor.id(), player.getUniqueId());

		stones.onEntityRemove(
			removalOf(absorbedStone(), EntityRemoveEvent.Cause.PICKUP));

		assertTrue(store.issued(survivor.id()));
	}

	@Test
	void aStoneFoundOnAJoiningPlayerIsNotFiledUnderARetiredId() {
		final PlayerMock player = server.addPlayer();
		player.getInventory().addItem(absorbedStone());
		final PlayerJoinEvent join = mock(PlayerJoinEvent.class);
		when(join.getPlayer()).thenReturn(player);

		stones.onPlayerJoin(join);

		assertTrue(store.issued(survivor.id()),
			"the living village has to be told its stone is out there");
		assertFalse(store.issued(absorbedId),
			"a row keyed to a retired id is one nothing will ever fold away");
		assertEquals(player.getUniqueId(), store.holderOf(survivor.id()));
	}

	@Test
	void aStoneHandedOutForAnAbsorbedRowIsBoundToTheLivingOne() {
		final PlayerMock player = server.addPlayer();

		stones.grant(player, absorbedId);

		final ItemStack granted = player.getInventory().getItem(0);
		assertEquals(survivor.id(), VillageTeleportStone.rowIdOf(granted));
		assertTrue(store.issued(survivor.id()));
		assertFalse(store.issued(absorbedId));
	}

	@Test
	void anIdThatWasNeverAbsorbedIsLeftExactlyAsItIs() {
		final PlayerMock player = server.addPlayer();

		stones.grant(player, survivor.id());

		assertEquals(survivor.id(),
			VillageTeleportStone.rowIdOf(player.getInventory().getItem(0)));
		assertTrue(stones.stoneIssued(survivor.id()));

		stones.onEntityRemove(removalOf(
			VillageTeleportStone.create(survivor.id(), survivor.name(), 120),
			EntityRemoveEvent.Cause.DISCARD));
		assertFalse(store.issued(survivor.id()));
	}
}
