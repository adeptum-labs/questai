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

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Plugin extends JavaPlugin implements Listener {
	private final List<SubPlugin> plugins = new ArrayList<>();

	@Override
	public void onEnable() {
		plugins.add(new AutoVillagerPlugin(this));
		plugins.add(new RandomQuestPlugin(this));

		PluginManager pm = getServer().getPluginManager();

		plugins.forEach(p -> {
			pm.registerEvents(p, this);
			p.onEnable();
		});
	}

	@Override
	public void onDisable() {
		HandlerList.unregisterAll((Listener) this);

		plugins.forEach(p -> {
			p.onDisable();
		});
	}
}
