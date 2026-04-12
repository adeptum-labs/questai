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
