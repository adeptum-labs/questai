package com.adeptum.questai.model.world;

import com.adeptum.questai.model.world.quest.Quest;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Npc {
	private Quest quest;
	private String nonsensePhrase;
	private long timestamp;

	public boolean isPhrase() {
		return nonsensePhrase != null;
	}

	public boolean isQuest() {
		return quest != null;
	}
}
