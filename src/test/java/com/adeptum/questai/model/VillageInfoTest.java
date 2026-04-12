package com.adeptum.questai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VillageInfoTest {

	@Test
	void accessorsReturnConstructorValues() {
		final VillageInfo info = new VillageInfo(true, 5, 3, 7);

		assertTrue(info.village());
		assertEquals(5, info.bedCount());
		assertEquals(3, info.workstationCount());
		assertEquals(7, info.villagerCount());
	}

	@Test
	void equalsSameValuesAreEqual() {
		final VillageInfo a = new VillageInfo(true, 4, 4, 6);
		final VillageInfo b = new VillageInfo(true, 4, 4, 6);
		assertEquals(a, b);
	}

	@Test
	void equalsDifferentValuesAreNotEqual() {
		final VillageInfo a = new VillageInfo(true, 4, 4, 6);
		final VillageInfo b = new VillageInfo(false, 4, 4, 6);
		assertNotEquals(a, b);
	}

	@Test
	void toStringContainsFieldValues() {
		final VillageInfo info = new VillageInfo(true, 5, 3, 7);
		final String str = info.toString();
		assertTrue(str.contains("5"));
		assertTrue(str.contains("3"));
		assertTrue(str.contains("7"));
	}
}
