package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.adeptum.questai.villager.StoredLocation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuiltBlocksTest {

	private static final UUID WORLD = UUID.randomUUID();
	private static final StoredLocation CENTRE =
		new StoredLocation(WORLD, 0, 64, 0);

	private static WorkState withWatchtower(final int rotation) {
		final WorkState state = new WorkState();
		state.getBuiltSites().put(VillageWork.WATCHTOWER.ordinal(),
			new WorkState.BuiltSite(
				new StoredLocation(WORLD, 40, 64, 40), rotation));
		return state;
	}

	@Test
	void aBlockInsideTheWatchtowerIsTheVillages() {
		// Layer 0 wall course: local (2,0,2) is a placed cell at rotation 0
		assertEquals(BuiltBlocks.Hit.STRUCTURE,
			BuiltBlocks.hit(withWatchtower(0), CENTRE, 42, 64, 42));
	}

	@Test
	void theSurveyStakesAndApronAreNobodys() {
		// Local (0,0,0) held a survey stake; stakes and scaffolding come
		// down at the finish, so their cells are not the standing works
		assertEquals(BuiltBlocks.Hit.NONE,
			BuiltBlocks.hit(withWatchtower(0), CENTRE, 40, 64, 40));
	}

	@Test
	void outsideTheBoundingBoxIsCheapNo() {
		assertEquals(BuiltBlocks.Hit.NONE,
			BuiltBlocks.hit(withWatchtower(0), CENTRE, 60, 64, 60));
		assertEquals(BuiltBlocks.Hit.NONE,
			BuiltBlocks.hit(withWatchtower(0), CENTRE, 42, 200, 42));
	}

	@Test
	void rotationCarriesTheCellsAround() {
		// The same wall cell found through a quarter-turned site
		final WorkState turned = withWatchtower(1);
		final WorkSchematic tower =
			WorkSchematic.load("structures/watchtower.txt");
		final SchematicEntry sample = tower.stage(BuildStage.SHELL).get(0);
		final SchematicEntry rotated = WorkSchematic.rotate(sample, 1,
			tower.getWidth(), tower.getDepth());

		assertEquals(BuiltBlocks.Hit.STRUCTURE, BuiltBlocks.hit(turned, CENTRE,
			40 + rotated.x(), 64 + rotated.y(), 40 + rotated.z()));
	}

	@Test
	void theRingBandIsTheVillagesOnceThePalisadeStands() {
		final WorkState state = new WorkState();
		state.getBuiltSites().put(VillageWork.PALISADE.ordinal(),
			new WorkState.BuiltSite(CENTRE, 0));

		assertEquals(BuiltBlocks.Hit.RING,
			BuiltBlocks.hit(state, CENTRE, PalisadeRing.RADIUS, 66, 10));
		assertEquals(BuiltBlocks.Hit.RING,
			BuiltBlocks.hit(state, CENTRE, -(PalisadeRing.RADIUS - 1), 66, 3));
		assertEquals(BuiltBlocks.Hit.NONE,
			BuiltBlocks.hit(state, CENTRE, PalisadeRing.RADIUS - 5, 66, 10));
		assertEquals(BuiltBlocks.Hit.NONE,
			BuiltBlocks.hit(state, CENTRE, PalisadeRing.RADIUS, 20, 10));
	}

	@Test
	void noRingClaimBeforeThePalisadeIsBuilt() {
		assertEquals(BuiltBlocks.Hit.NONE,
			BuiltBlocks.hit(withWatchtower(0), CENTRE,
				PalisadeRing.RADIUS, 66, 10));
	}

	@Test
	void nullStateClaimsNothing() {
		assertEquals(BuiltBlocks.Hit.NONE,
			BuiltBlocks.hit(null, CENTRE, 0, 64, 0));
	}
}
