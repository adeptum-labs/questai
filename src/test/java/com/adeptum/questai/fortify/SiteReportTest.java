package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SiteReportTest {

	@Test
	void aSearchThatSawNothingSaysSoPlainly() {
		assertTrue(new SiteReport().describe()
			.endsWith("nowhere level to build."));
	}

	@Test
	void waterEverywhereIsNamedAsWater() {
		final SiteReport report = new SiteReport();
		for (int i = 0; i < 5; i++) {
			report.rejected(GroundCover.Verdict.BLOCKED);
		}
		report.rejected(GroundCover.Verdict.BUILT);
		assertTrue(report.describe().contains("runs into water"),
			report.describe());
	}

	@Test
	void anOccupiedRingIsNamedAsSuch() {
		final SiteReport report = new SiteReport();
		for (int i = 0; i < 5; i++) {
			report.rejected(GroundCover.Verdict.BUILT);
		}
		report.rejected(GroundCover.Verdict.BLOCKED);
		assertTrue(report.describe().contains("already spoken for"),
			report.describe());
	}

	@Test
	void groundNobodyHasSeenIsAdmittedTo() {
		final SiteReport report = new SiteReport();
		report.unscanned();
		assertTrue(report.describe().contains("walked far enough out"),
			report.describe());
	}

	@Test
	void aNearMissPointsTheDonorAtIt() {
		final SiteReport report = new SiteReport();
		report.rejected(GroundCover.Verdict.BLOCKED);
		report.tooSteep(9, 30, 0);
		report.tooSteep(6, 0, -20);
		report.tooSteep(11, -40, 0);

		final String message = report.describe();
		assertTrue(message.contains("lies N of here"), message);
		assertTrue(message.contains("Clear and level"), message);
	}
}
