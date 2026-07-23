package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Reads a mock world the way the crew does, because the walk down through a
 * canopy is the one rule here that only a real column can prove.
 */
class SiteGroundTest {

	private static final int GROUND_Y = 64;

	private World world;

	@BeforeEach
	void setUp() {
		final ServerMock server = MockBukkit.mock();
		world = server.addSimpleWorld("ground-test");
		// Ground nobody has walked to is never read, mock world or not
		world.loadChunk(0, 0);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	/** Lays a patch of grass, the plain ground everything else stands on. */
	private void pasture(final int x, final int z, final int size) {
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				world.getBlockAt(x + dx, GROUND_Y, z + dz)
					.setType(Material.GRASS_BLOCK);
			}
		}
	}

	@Test
	void plainGroundIsFoundAtItsSurface() {
		pasture(0, 0, 1);
		final SiteGround.Column column = new SiteGround(world).columnAt(0, 0);

		assertNotNull(column);
		assertTrue(column.usable());
		assertEquals(GROUND_Y, column.groundY());
		assertEquals(0, column.cover());
	}

	@Test
	void aTreeIsWalkedThroughToTheGroundBeneath() {
		pasture(0, 0, 1);
		world.getBlockAt(0, GROUND_Y + 1, 0).setType(Material.OAK_LOG);
		world.getBlockAt(0, GROUND_Y + 2, 0).setType(Material.OAK_LOG);
		world.getBlockAt(0, GROUND_Y + 3, 0).setType(Material.OAK_LEAVES);

		final SiteGround.Column column = new SiteGround(world).columnAt(0, 0);

		assertTrue(column.usable(), "the crew refused a tree");
		assertEquals(GROUND_Y, column.groundY());
		assertEquals(3, column.cover());
	}

	@Test
	void aBareLogWallTurnsTheColumnDown() {
		pasture(0, 0, 1);
		world.getBlockAt(0, GROUND_Y + 1, 0).setType(Material.SPRUCE_LOG);
		world.getBlockAt(0, GROUND_Y + 2, 0).setType(Material.SPRUCE_LOG);

		assertEquals(GroundCover.Verdict.BUILT,
			new SiteGround(world).columnAt(0, 0).verdict());
	}

	@Test
	void waterIsNeverBuiltOn() {
		world.getBlockAt(0, GROUND_Y, 0).setType(Material.WATER);
		assertEquals(GroundCover.Verdict.BLOCKED,
			new SiteGround(world).columnAt(0, 0).verdict());
	}

	@Test
	void unloadedGroundIsUnknownRatherThanRefused() {
		final SiteGround ground = new SiteGround(world);
		assertEquals(null, ground.columnAt(5_000_000, 5_000_000));
	}

	@Test
	void preparingCutsTheRiseAndFillsTheDip() {
		pasture(0, 0, 3);
		// A boulder standing proud of the pad, and a hollow beside it
		world.getBlockAt(1, GROUND_Y + 1, 1).setType(Material.STONE);
		world.getBlockAt(2, GROUND_Y, 2).setType(Material.AIR);
		world.getBlockAt(2, GROUND_Y - 2, 2).setType(Material.GRASS_BLOCK);

		new SiteGround(world).prepare(
			new Location(world, 0, GROUND_Y + 1, 0), 3, 3, 4);

		assertTrue(world.getBlockAt(1, GROUND_Y + 1, 1).getType().isAir(),
			"the rise was left standing");
		assertEquals(Material.COARSE_DIRT,
			world.getBlockAt(2, GROUND_Y - 1, 2).getType());
	}

	@Test
	void preparingLeavesSomebodysWorkAlone() {
		pasture(0, 0, 3);
		world.getBlockAt(1, GROUND_Y + 1, 1).setType(Material.OAK_PLANKS);

		new SiteGround(world).prepare(
			new Location(world, 0, GROUND_Y + 1, 0), 3, 3, 4);

		assertEquals(Material.OAK_PLANKS,
			world.getBlockAt(1, GROUND_Y + 1, 1).getType());
	}
}
