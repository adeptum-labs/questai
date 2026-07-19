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

package com.adeptum.questai.mob;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Tags variant mobs in their persistent data container, so identity
 * survives renames and chunk reloads. Helpers take the container itself
 * to stay mockable; callers pass entity.getPersistentDataContainer().
 */
public final class MobTags {

	private static final NamespacedKey KEY = new NamespacedKey("questai", "mob");

	private MobTags() {
	}

	public static void tag(final PersistentDataContainer pdc,
		final MobVariant variant) {

		pdc.set(KEY, PersistentDataType.STRING, variant.getId());
	}

	/** The variant a mob was forged into, or null for ordinary mobs. */
	public static MobVariant variantOf(final PersistentDataContainer pdc) {
		final String id = pdc.get(KEY, PersistentDataType.STRING);
		return id == null ? null : MobVariant.fromId(id);
	}
}
