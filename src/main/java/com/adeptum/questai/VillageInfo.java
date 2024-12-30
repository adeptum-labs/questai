
package com.adeptum.questai;

public class VillageInfo {
	private final boolean isVillage;
	private final int bedCount;
	private final int workstationCount;
	private final int villagerCount;

	public VillageInfo(boolean isVillage, int bedCount, int workstationCount, int villagerCount) {
		this.isVillage = isVillage;
		this.bedCount = bedCount;
		this.workstationCount = workstationCount;
		this.villagerCount = villagerCount;
	}

	public boolean isVillage() {
		return isVillage;
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
