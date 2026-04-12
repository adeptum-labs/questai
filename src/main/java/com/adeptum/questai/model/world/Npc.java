package com.adeptum.questai.model.world;

import com.adeptum.questai.model.world.quest.Quest;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Data
@Builder
public class Npc {
	@RequiredArgsConstructor
	public enum Mood {
		BOOKISH("Nerdy & bookish"),
		BRASH("Brash"),
		CHATTY("Chatty"),
		ECCENTRIC("Extremely eccentric"),
		KINDLY("Very kindly"),
		MYSTIC("Dark & mysterious"),
		ROYAL("Snoby & royal"),
		SHY("Shy & careful"),
		STOIC("Stoic"),
		SURLY("Surly");

		@Getter
		private final String description;
	}

	@RequiredArgsConstructor
	public enum Personality {
		BOOKISH("Nerdy & bookish"),
		BRASH("Brash"),
		CHATTY("Chatty"),
		ECCENTRIC("Extremely eccentric"),
		KINDLY("Very kindly"),
		MYSTIC("Dark & mysterious"),
		ROYAL("Snoby & royal"),
		SHY("Shy & careful"),
		STOIC("Stoic"),
		SURLY("Surly");

		@Getter
		private final String description;
	}

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
