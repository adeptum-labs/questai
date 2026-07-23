package com.adeptum.questai.fortify;

/**
 * One block of a structure in local coordinates, with the block state suffix
 * it needs and the stage that places it.
 */
public record SchematicEntry(int x, int y, int z, PaletteRole role,
	String state, BuildStage stage) {
}
