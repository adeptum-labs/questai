/*
 * Copyright (C) 2026 Adeptum AB, org nr. 559494-1824
 *
 * This file is part of QuestAI.
 *
 * QuestAI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * QuestAI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with QuestAI. If not, see
 * <https://www.gnu.org/licenses/>.
 */

package com.adeptum.questai.utility;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;

/**
 * The block types that count as ordinary, untouched ground. Used wherever
 * something is placed on the world surface, so that craters, raiders and
 * guards land on real terrain instead of on rooftops, leaves or water.
 *
 * <p>Everything player-made, living, liquid or tile-entity based is
 * excluded by construction.
 */
public final class NaturalTerrain {

	private static final Set<Material> SURFACES = buildSurfaces();

	private NaturalTerrain() {
	}

	/** True when this block type is natural ground worth standing on. */
	public static boolean isSurface(final Material material) {
		return SURFACES.contains(material);
	}

	private static Set<Material> buildSurfaces() {
		final Set<Material> materials = EnumSet.of(Material.GRASS_BLOCK,
			Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT,
			Material.PODZOL, Material.MYCELIUM, Material.STONE,
			Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE,
			Material.GRANITE, Material.TUFF, Material.CALCITE, Material.SAND,
			Material.RED_SAND, Material.SANDSTONE, Material.RED_SANDSTONE,
			Material.GRAVEL, Material.CLAY, Material.SNOW,
			Material.SNOW_BLOCK, Material.MOSS_BLOCK);
		for (final Material material : Material.values()) {
			// Plain and colored terracotta are natural badlands surfaces;
			// glazed terracotta is always player-crafted
			if (material.name().endsWith("TERRACOTTA")
				&& !material.name().contains("GLAZED")) {
				materials.add(material);
			}
		}
		return materials;
	}
}
