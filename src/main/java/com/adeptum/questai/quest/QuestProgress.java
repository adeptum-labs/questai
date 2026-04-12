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

package com.adeptum.questai.quest;

import com.adeptum.questai.model.world.quest.Quest;
import lombok.Data;
import org.bukkit.boss.BossBar;

@Data
public class QuestProgress {
	private Quest quest;
	private int current;
	private long startTime;
	private BossBar objectiveBossBar;
	private BossBar timerBossBar;
	private double maxDistance;

	public QuestProgress(Quest quest) {
		this.quest = quest;
		this.current = 0;
		this.startTime = System.currentTimeMillis();
	}
}
