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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobVoiceTest {

	/** The client silently clamps outside this, hiding a mistuned sound. */
	private static final float MIN_PITCH = 0.5f;
	private static final float MAX_PITCH = 2.0f;

	private static List<MobVariant.Layer> allLayers(final MobVariant.Voice voice) {
		final List<MobVariant.Layer> layers = new ArrayList<>();
		layers.addAll(voice.ambient());
		layers.addAll(voice.hurt());
		layers.addAll(voice.death());
		if (voice.step() != null) {
			layers.add(voice.step());
		}
		return layers;
	}

	@Test
	void everyVoicedVariantCoversAmbientHurtAndDeath() {
		for (final MobVariant variant : MobVariant.values()) {
			final MobVariant.Voice voice = variant.getVoice();
			if (voice == null) {
				continue;
			}
			// A silenced mob with a gap here is simply mute for that event
			assertFalse(voice.ambient().isEmpty(), variant + " has no ambient");
			assertFalse(voice.hurt().isEmpty(), variant + " has no hurt");
			assertFalse(voice.death().isEmpty(), variant + " has no death");
		}
	}

	@Test
	void everyPitchStaysInsideTheClientRange() {
		for (final MobVariant variant : MobVariant.values()) {
			if (variant.getVoice() == null) {
				continue;
			}
			for (final MobVariant.Layer layer : allLayers(variant.getVoice())) {
				assertTrue(layer.pitch() >= MIN_PITCH && layer.pitch() <= MAX_PITCH,
					variant + " layer " + layer.sound() + " pitch " + layer.pitch()
						+ " would be clamped");
			}
		}
	}

	@Test
	void everyLayerIsAudibleAndNamed() {
		for (final MobVariant variant : MobVariant.values()) {
			if (variant.getVoice() == null) {
				continue;
			}
			for (final MobVariant.Layer layer : allLayers(variant.getVoice())) {
				assertTrue(layer.volume() > 0.0f,
					variant + " layer " + layer.sound() + " is silent");
				assertNotNull(layer.sound());
				assertFalse(layer.sound().isBlank());
			}
		}
	}

	@Test
	void theHulkSoundsLowAndTheGravelingHigh() {
		final float hulk = averagePitch(MobVariant.GRAVEHULK);
		final float graveling = averagePitch(MobVariant.GRAVELING);

		assertTrue(hulk < 1.0f, "the gravehulk should sit below a normal zombie");
		assertTrue(graveling > 1.0f, "the graveling should sit above one");
		assertTrue(graveling > hulk);
	}

	@Test
	void onlyTheHeavyVariantShakesTheGround() {
		assertNotNull(MobVariant.GRAVEHULK.getVoice().step());
		assertNull(MobVariant.GRAVELING.getVoice().step());
	}

	@Test
	void theCinderlingKeepsItsVanillaSpiderVoice() {
		assertNull(MobVariant.CINDERLING.getVoice());
	}

	private static float averagePitch(final MobVariant variant) {
		final List<MobVariant.Layer> layers = allLayers(variant.getVoice());
		float total = 0.0f;
		for (final MobVariant.Layer layer : layers) {
			total += layer.pitch();
		}
		return total / layers.size();
	}
}
