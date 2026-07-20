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

package com.adeptum.questai.village;

import com.adeptum.questai.event.VillageKey;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Runs against a real server so the boss bar is the one a player would
 * actually be shown, rather than a mock that cannot prove it was removed.
 */
class VillageNameplateTest {

	private static final double RADIUS = 48.0;

	@TempDir
	private Path tempDir;

	private ServerMock server;
	private JavaPlugin plugin;
	private World world;
	private VillageRegistry registry;
	private VillageNameplate nameplate;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("village-test");

		plugin = mock(JavaPlugin.class);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
		when(plugin.getConfig()).thenReturn(new YamlConfiguration());

		registry = new VillageRegistry(plugin, RADIUS);
		nameplate = new VillageNameplate(plugin, null, registry);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	private Location at(final double x, final double z) {
		return new Location(world, x, 64, z);
	}

	private void claimRavenhollow() {
		final UUID worldId = world.getUID();
		registry.claim(VillageKey.from(worldId, 0, 0), at(0, 0), "Ravenhollow");
	}

	/** Drives one presence sweep without waiting on the scheduler. */
	private void sweep() {
		nameplate.tick();
	}

	private String barTitle(final PlayerMock player) {
		final BossBar bar = nameplate.barOf(player.getUniqueId());
		return bar == null ? null : bar.getTitle();
	}

	@Test
	void enteringAVillageRaisesABarNamedAfterIt() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(10, 10));

		sweep();

		assertEquals(1, nameplate.barCount());
		assertEquals("Ravenhollow", barTitle(player));
	}

	@Test
	void leavingTheVillageTakesTheBarDown() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(10, 10));
		sweep();
		assertNotNull(barTitle(player));

		player.setLocation(at(500, 500));
		sweep();

		assertNull(barTitle(player));
	}

	@Test
	void aPlayerOutsideAnyVillageNeverGetsABar() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(500, 500));

		sweep();

		assertNull(barTitle(player));
	}

	@Test
	void repeatedSweepsInsideOneVillageKeepASingleBar() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(10, 10));

		sweep();
		sweep();
		sweep();

		assertEquals("Ravenhollow", barTitle(player));
		assertEquals(1, nameplate.barCount());
	}

	@Test
	void crossingBetweenVillagesSwapsTheName() {
		claimRavenhollow();
		registry.claim(VillageKey.from(world.getUID(), 200, 0), at(200, 0),
			"Frostmere");
		final PlayerMock player = server.addPlayer();

		player.setLocation(at(0, 0));
		sweep();
		assertEquals("Ravenhollow", barTitle(player));

		player.setLocation(at(200, 0));
		sweep();
		assertEquals("Frostmere", barTitle(player));
		assertEquals(1, nameplate.barCount());
	}

	@Test
	void quittingReleasesTheBar() {
		claimRavenhollow();
		final PlayerMock player = server.addPlayer();
		player.setLocation(at(10, 10));
		sweep();
		assertEquals(1, nameplate.barCount());

		final PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
		when(quit.getPlayer()).thenReturn(player);
		nameplate.onPlayerQuit(quit);

		assertEquals(0, nameplate.barCount());
	}

	@Test
	void disablingClearsEveryBar() {
		claimRavenhollow();
		final PlayerMock first = server.addPlayer();
		final PlayerMock second = server.addPlayer();
		first.setLocation(at(0, 0));
		second.setLocation(at(5, 5));
		sweep();
		assertEquals(2, nameplate.barCount());

		nameplate.onDisable();

		assertEquals(0, nameplate.barCount());
	}
}
