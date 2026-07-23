package com.adeptum.questai.fortify;

/**
 * The visible steps a structure passes through. Each one must look like a
 * deliberate state on its own, because players see it for a long while.
 */
public enum BuildStage {
	/** Stakes and a materials dump, placed the moment the project is funded. */
	SURVEY,
	/** Foundation, ground course and corner posts, plus scaffolding. */
	FRAME,
	/** The remaining walls and floors up to the platform. */
	SHELL,
	/** Parapets, roof and crenellations. */
	ROOF,
	/** Fittings and furniture; scaffolding comes down here. */
	DETAIL
}
