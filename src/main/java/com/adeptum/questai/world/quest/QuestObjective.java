package com.adeptum.questai.world.quest;

import lombok.Data;

@Data
public class QuestObjective {
	public enum Type {
		KILL, COLLECT
	}

	private Type type;
	private String target;
	private int amount;
}
