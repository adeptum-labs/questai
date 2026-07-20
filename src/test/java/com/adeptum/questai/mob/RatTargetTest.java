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

package com.adeptum.questai.mob;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

/**
 * The rat's passivity rests on this handler: a silverfish retaliates when
 * struck, so cancelling its targeting is what makes "never attacks even if
 * you attack it" true.
 */
class RatTargetTest {

	private RatSpawner spawner() {
		final JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getConfig()).thenReturn(new YamlConfiguration());
		return new RatSpawner(plugin, null, null);
	}

	private LivingEntity entityTagged(final String id) {
		final LivingEntity entity = mock(LivingEntity.class);
		final PersistentDataContainer pdc = mock(PersistentDataContainer.class);
		when(entity.getPersistentDataContainer()).thenReturn(pdc);
		when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
			.thenReturn(id);
		return entity;
	}

	@Test
	void aRatNeverAcquiresATarget() {
		// Built before the stubbing below: entityTagged stubs internally,
		// and nested stubbing leaves the outer when() unfinished
		final LivingEntity rat = entityTagged("rat");
		final EntityTargetEvent event = mock(EntityTargetEvent.class);
		when(event.getEntity()).thenReturn(rat);

		spawner().onTarget(event);

		verify(event).setCancelled(true);
	}

	@Test
	void anOrdinarySilverfishStillFightsBack() {
		// Built before the stubbing below: entityTagged stubs internally,
		// and nested stubbing leaves the outer when() unfinished
		final LivingEntity plain = entityTagged(null);
		final EntityTargetEvent event = mock(EntityTargetEvent.class);
		when(event.getEntity()).thenReturn(plain);

		spawner().onTarget(event);

		verify(event, never()).setCancelled(anyBoolean());
	}

	@Test
	void otherForgedVariantsKeepTheirAggression() {
		// Built before the stubbing below: entityTagged stubs internally,
		// and nested stubbing leaves the outer when() unfinished
		final LivingEntity hulk = entityTagged("gravehulk");
		final EntityTargetEvent event = mock(EntityTargetEvent.class);
		when(event.getEntity()).thenReturn(hulk);

		spawner().onTarget(event);

		verify(event, never()).setCancelled(anyBoolean());
	}
}
