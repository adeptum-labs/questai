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

package com.adeptum.questai.relic;

import java.util.List;
import lombok.Getter;
import org.bukkit.Material;

/**
 * The rare quest-exclusive relic items: identity, base material, resource
 * pack model id and presentation. CustomModelData values start at 100010,
 * above the dialogue GUI range that begins at 100001.
 */
@Getter
public enum QuestRelic {

	ELDERS_QUILL("elders_quill", Material.FEATHER, 100010,
		"\u00a76Elder's Quill", List.of(
			"\u00a77A feather that has signed a",
			"\u00a77hundred village decrees.",
			"\u00a7aQuest rewards grant 25% more XP.")),

	PROSPECTORS_CHARM("prospectors_charm", Material.RABBIT_FOOT, 100011,
		"\u00a76Prospector's Charm", List.of(
			"\u00a77A lucky nugget on a worn",
			"\u00a77leather cord.",
			"\u00a7aYour treasure chests hide finer loot.")),

	WAYFARERS_COMPASS("wayfarers_compass", Material.AMETHYST_SHARD, 100012,
		"\u00a76Wayfarer's Compass", List.of(
			"\u00a77Its needle ignores north and",
			"\u00a77seeks people instead.",
			"\u00a7aPoints to the nearest named villager.",
			"\u00a78Right-click while held")),

	WHISPERING_LOCKET("whispering_locket", Material.GOLD_NUGGET, 100013,
		"\u00a76Whispering Locket", List.of(
			"\u00a77Villagers lean closer when",
			"\u00a77they glimpse it.",
			"\u00a7aVillagers offer quests more readily.")),

	PEDDLERS_BELL("peddlers_bell", Material.GOLD_INGOT, 100014,
		"\u00a76Peddler's Bell", List.of(
			"\u00a77Its chime carries further",
			"\u00a77than any voice.",
			"\u00a7aCalls a wandering peasant to you.",
			"\u00a78Right-click while held"));

	private final String id;
	private final Material material;
	private final int customModelData;
	private final String displayName;
	private final List<String> lore;

	QuestRelic(final String id, final Material material,
		final int customModelData, final String displayName,
		final List<String> lore) {

		this.id = id;
		this.material = material;
		this.customModelData = customModelData;
		this.displayName = displayName;
		this.lore = lore;
	}

	/** Resolves a stored relic id, or null when unknown. */
	public static QuestRelic fromId(final String id) {
		for (final QuestRelic relic : values()) {
			if (relic.id.equals(id)) {
				return relic;
			}
		}
		return null;
	}
}
