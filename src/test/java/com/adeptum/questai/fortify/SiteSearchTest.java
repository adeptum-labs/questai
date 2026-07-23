package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The search over a mock world, where the ground can be made as awkward as
 * the villages that first refused to build anything at all.
 */
class SiteSearchTest {

	private static final int GROUND_Y = 64;
	private static final int REACH = 16;
	private static final int FOOTPRINT = 3;
	private static final VillageExtent.Extent CENTRE =
		new VillageExtent.Extent(0, 0, 16);
	private static final int[] BAND = {8, 10};

	private World world;

	@BeforeEach
	void setUp() {
		final ServerMock server = MockBukkit.mock();
		world = server.addSimpleWorld("search-test");
		for (int cx = -2; cx <= 2; cx++) {
			for (int cz = -2; cz <= 2; cz++) {
				world.loadChunk(cx, cz);
			}
		}
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	/** Lays plain ground over everything the band can reach. */
	private void pasture() {
		forEachCell((x, z) -> world.getBlockAt(x, GROUND_Y, z)
			.setType(Material.GRASS_BLOCK));
	}

	/** Grows a wood over the cells the test asks for. */
	private void wood(final java.util.function.IntPredicate where) {
		forEachCell((x, z) -> {
			if (!where.test(x)) {
				return;
			}
			world.getBlockAt(x, GROUND_Y + 1, z).setType(Material.OAK_LOG);
			world.getBlockAt(x, GROUND_Y + 4, z).setType(Material.OAK_LEAVES);
		});
	}

	private void forEachCell(final Cells action) {
		for (int x = -REACH; x <= REACH; x++) {
			for (int z = -REACH; z <= REACH; z++) {
				action.at(x, z);
			}
		}
	}

	/** What to do at one column of the test world. */
	private interface Cells {
		void at(int x, int z);
	}

	@Test
	void openPastureOffersASite() {
		pasture();
		final SiteSearch search = new SiteSearch(world);
		final WorkSite.Candidate site = search.find(CENTRE, BAND, FOOTPRINT);

		assertNotNull(site, search.getReport().describe());
		final int distance = Math.max(Math.abs(site.x()), Math.abs(site.z()));
		assertTrue(distance >= BAND[0] - FOOTPRINT
			&& distance <= BAND[1] + FOOTPRINT,
			"the site landed outside the band at " + site);
	}

	@Test
	void aWoodIsClearedRatherThanRefused() {
		pasture();
		wood(x -> true);

		final SiteSearch search = new SiteSearch(world);
		assertNotNull(search.find(CENTRE, BAND, FOOTPRINT),
			"a wooded village was turned away: "
				+ search.getReport().describe());
	}

	@Test
	void openGroundIsPreferredToStandingTimber() {
		pasture();
		wood(x -> x < 0);

		final WorkSite.Candidate site =
			new SiteSearch(world).find(CENTRE, BAND, FOOTPRINT);

		assertNotNull(site);
		assertTrue(site.x() >= 0,
			"the crew felled a wood with pasture to hand: " + site);
	}

	@Test
	void waterEverywhereIsRefusedAndExplained() {
		forEachCell((x, z) -> world.getBlockAt(x, GROUND_Y, z)
			.setType(Material.WATER));

		final SiteSearch search = new SiteSearch(world);
		assertNull(search.find(CENTRE, BAND, FOOTPRINT));
		assertTrue(search.getReport().describe().contains("runs into water"),
			search.getReport().describe());
	}

	@Test
	void groundNobodyHasLoadedIsAdmittedTo() {
		final SiteSearch search = new SiteSearch(world);
		assertNull(search.find(CENTRE, new int[] {2000, 2002}, FOOTPRINT));
		assertTrue(search.getReport().describe().contains("walked far enough"),
			search.getReport().describe());
	}
}
