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

package com.adeptum.questai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Pins down how the model settings are read out of config.yml.
 *
 * <p>The api key is written as a flat "openai.api-key" key rather than
 * inside an "openai:" block, and the settings beside it have to be written
 * the same way: a real block at that path shadows the flat key and reads
 * back as unset, which shuts the plugin down on start with no key.
 */
class PluginConfigTest {

	private static YamlConfiguration read(final String yaml) {
		return YamlConfiguration.loadConfiguration(new StringReader(yaml));
	}

	@Test
	void flatKeysBesideTheApiKeyAllRead() {
		final YamlConfiguration config = read("""
			openai.api-key: "KEY"
			openai.timeout-seconds: 10
			openai.max-retries: 1
			""");

		assertEquals("KEY", config.getString("openai.api-key"));
		assertEquals(10, config.getLong("openai.timeout-seconds", 30));
		assertEquals(1, config.getInt("openai.max-retries", 9));
	}

	@Test
	void settingsLeftOutFallBackToTheirDefaults() {
		final YamlConfiguration config = read("openai.api-key: \"KEY\"\n");

		assertEquals("KEY", config.getString("openai.api-key"));
		assertEquals(30, config.getLong("openai.timeout-seconds", 30));
		assertEquals(9, config.getInt("openai.max-retries", 9));
	}

	/** The trap: a block at "openai" hides the flat key sitting beside it. */
	@Test
	void aBlockAtOpenaiWouldHideTheApiKey() {
		final YamlConfiguration config = read("""
			openai.api-key: "KEY"
			openai:
			  timeout-seconds: 10
			""");

		assertNull(config.getString("openai.api-key"));
	}
}
