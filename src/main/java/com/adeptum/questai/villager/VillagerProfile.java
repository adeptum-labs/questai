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

package com.adeptum.questai.villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Persistent character sheet for a named villager: identity, personality
 * and what the villager remembers about individual players.
 */
@Data
@Builder
public class VillagerProfile {
	private String name;
	private String profession;
	@Builder.Default
	private List<String> traits = List.of();
	private String greeting;
	private StoredLocation location;
	@Builder.Default
	private List<Relationship> relationships = new ArrayList<>();
	@Builder.Default
	private Map<UUID, PlayerMemory> players = new HashMap<>();
}
