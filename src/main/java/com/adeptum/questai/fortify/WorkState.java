package com.adeptum.questai.fortify;

import com.adeptum.questai.villager.StoredLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * How far one village has got: which project is active, how much has been
 * donated toward it, and how much of it physically stands.
 */
@Data
public class WorkState {

	/** Index into the tier ladder; equal to the ladder size when finished. */
	private int tier;
	/** How many construction stages of the active project have been placed. */
	private int stage;
	/** When the last stage was placed, so a restart resumes rather than skips. */
	private long stageAt;
	/** Chosen origin of the active project, null until the build begins. */
	private StoredLocation site;
	/** Quarter turns applied to the structure at placement. */
	private int rotation;
	private final Map<String, Integer> tally = new LinkedHashMap<>();
	/** Where each finished tier stands, so later tiers can respect it. */
	private final Map<Integer, BuiltSite> builtSites = new LinkedHashMap<>();

	/** A completed structure's origin and the quarter turns it was given. */
	public record BuiltSite(StoredLocation origin, int rotation) {
	}
}
