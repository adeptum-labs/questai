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

import com.adeptum.questai.dialogue.ConversationManager;
import com.adeptum.questai.service.QuestGenerationService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Plugin extends JavaPlugin implements Listener {
	private final List<SubPlugin> plugins = new ArrayList<>();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		final FileConfiguration config = getConfig();

		final String apiKey = config.getString("openai.api-key");
		if (apiKey == null || apiKey.isEmpty()
			|| "YOUR_OPENAI_API_KEY_HERE".equals(apiKey)) {
			getLogger().severe("OpenAI API key is not set in config.yml!");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		final OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.apiKey(apiKey)
			.modelName("gpt-5.4-nano")
			.build();

		final QuestGenerationService questService =
			new QuestGenerationService(this, chatModel);
		final ConversationManager conversationManager =
			new ConversationManager(this, chatModel);
		conversationManager.setQuestService(questService);

		final PluginManager pm = getServer().getPluginManager();

		plugins.add(new AutoVillagerPlugin(this));
		plugins.add(new RandomQuestPlugin(this, conversationManager,
			questService, chatModel));
		plugins.add(new WanderingPeasantPlugin(this, conversationManager,
			questService, chatModel));

		plugins.forEach(p -> {
			pm.registerEvents(p, this);
			p.onEnable();
		});
	}

	@Override
	public void onDisable() {
		HandlerList.unregisterAll((Listener) this);

		plugins.forEach(SubPlugin::onDisable);
	}
}
