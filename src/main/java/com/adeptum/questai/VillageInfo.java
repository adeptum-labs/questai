package com.adeptum.questai;

@SuppressWarnings("PMD.DataClass")
public class VillageInfo {
	private final boolean village;
	private final int bedCount;
	private final int workstationCount;
	private final int villagerCount;

	public VillageInfo(boolean village, int bedCount, int workstationCount, int villagerCount) {
		this.village = village;
		this.bedCount = bedCount;
		this.workstationCount = workstationCount;
		this.villagerCount = villagerCount;
	}

	public boolean isVillage() {
		return village;
	}

	public int getBedCount() {
		return bedCount;
	}

	public int getWorkstationCount() {
		return workstationCount;
	}

	public int getVillagerCount() {
		return villagerCount;
	}
}
