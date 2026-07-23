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
import com.adeptum.questai.event.VillageEventManager;
import com.adeptum.questai.fortify.VillageFortification;
import com.adeptum.questai.fortify.VillageWorksStore;
import com.adeptum.questai.village.VillageNameplate;
import com.adeptum.questai.village.VillageRegistry;
import com.adeptum.questai.mob.MobForge;
import com.adeptum.questai.mob.RatCooking;
import com.adeptum.questai.mob.RatSpawner;
import com.adeptum.questai.quest.QuestLogListener;
import com.adeptum.questai.relic.RelicListener;
import com.adeptum.questai.reputation.DemolitionWatch;
import com.adeptum.questai.reputation.Standings;
import com.adeptum.questai.reputation.VillageReputationStore;
import com.adeptum.questai.resourcepack.ResourcePackManager;
import com.adeptum.questai.star.StarfallManager;
import com.adeptum.questai.service.QuestGenerationService;
import com.adeptum.questai.villager.VillagerProfileStore;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Plugin extends JavaPlugin implements Listener {
	private final List<SubPlugin> plugins = new ArrayList<>();
	private ResourcePackManager resourcePackManager;

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

		final VillagerProfileStore profileStore = new VillagerProfileStore(this);
		final QuestGenerationService questService =
			new QuestGenerationService(this, chatModel, profileStore);
		final ConversationManager conversationManager =
			new ConversationManager(this, chatModel, profileStore);
		conversationManager.setQuestService(questService);

		final PluginManager pm = getServer().getPluginManager();

		// Wider than EVENT_RADIUS: the stored centre is wherever the
		// discoverer stood, so a tight radius greets big villages late
		final VillageRegistry registry = new VillageRegistry(this,
			config.getDouble("villages.nameplate.radius", 64.0));
		final VillageReputationStore reputationStore =
			new VillageReputationStore(this);
		final Standings standings = new Standings(registry, reputationStore);
		final VillageEventManager eventManager =
			new VillageEventManager(this, profileStore, standings);
		final MobForge mobForge = new MobForge(this);
		final VillageWorksStore worksStore = new VillageWorksStore(this);
		final VillageFortification fortification = new VillageFortification(
			this, registry, worksStore, profileStore, mobForge);
		conversationManager.setWorksStore(worksStore);
		conversationManager.setWorkDonationHandler(fortification::donate);
		conversationManager.setStandings(standings);
		final RandomQuestPlugin randomQuestPlugin =
			new RandomQuestPlugin(this, conversationManager, questService,
				chatModel, profileStore, eventManager, fortification);
		randomQuestPlugin.setStandings(standings);
		final WanderingPeasantPlugin peasantPlugin = new WanderingPeasantPlugin(
			this, conversationManager, questService, chatModel);
		final VillageNameplate nameplate = new VillageNameplate(this, chatModel,
			registry);
		final AutoVillagerPlugin autoVillager =
			new AutoVillagerPlugin(this, List.of(eventManager, nameplate));
		// The scanner also feeds the nameplate its village checks, so the
		// on-demand half is wired back in once both exist
		nameplate.setScanner(autoVillager);

		plugins.add(autoVillager);
		plugins.add(nameplate);
		plugins.add(randomQuestPlugin);
		plugins.add(peasantPlugin);
		plugins.add(new FlyingPigPlugin(this));
		plugins.add(new RelicListener(profileStore, peasantPlugin));
		plugins.add(mobForge);
		plugins.add(new RatSpawner(this, mobForge, autoVillager));
		plugins.add(new RatCooking());
		plugins.add(new StarfallManager(this, mobForge));
		plugins.add(eventManager);
		plugins.add(fortification);
		plugins.add(new DemolitionWatch(registry, worksStore, standings));

		plugins.forEach(p -> {
			pm.registerEvents(p, this);
			p.onEnable();
		});

		pm.registerEvents(
			new QuestLogListener(randomQuestPlugin.getQuestManager()), this);

		resourcePackManager = new ResourcePackManager();
		resourcePackManager.initialize(this);
		pm.registerEvents(this, this);

		// Send resource pack to already online players (plugin reload)
		for (final Player player : Bukkit.getOnlinePlayers()) {
			resourcePackManager.sendToPlayer(player);
		}
	}

	@EventHandler
	public void onPlayerJoin(final PlayerJoinEvent event) {
		if (resourcePackManager != null) {
			// Slight delay so the client is ready to receive the pack
			Bukkit.getScheduler().runTaskLater(this,
				() -> resourcePackManager.sendToPlayer(event.getPlayer()), 20L);
		}
	}

	@Override
	public void onDisable() {
		HandlerList.unregisterAll((Listener) this);
		plugins.forEach(SubPlugin::onDisable);
		if (resourcePackManager != null) {
			resourcePackManager.shutdown();
		}
	}
}
