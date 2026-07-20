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

package com.adeptum.questai.resourcepack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the generated pack's structure: parseable JSON, matching
 * legacy and modern item formats, and no dangling model or texture
 * references. Gson is available at test time through the Paper API.
 */
class ResourcePackManagerTest {

	private static final Map<String, byte[]> ENTRIES =
		ResourcePackManager.packEntries();

	private static JsonObject json(final String key) {
		final byte[] bytes = ENTRIES.get(key);
		assertNotNull(bytes, "Missing pack entry: " + key);
		return JsonParser.parseString(
			new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void allJsonEntriesParse() {
		for (final Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
			if (entry.getKey().endsWith(".json")
				|| entry.getKey().endsWith(".mcmeta")) {
				assertDoesNotThrow(() -> json(entry.getKey()),
					"Unparseable JSON in " + entry.getKey());
			}
		}
	}

	@Test
	void packManifestDeclaresTheFullFormatRange() {
		final JsonObject pack = json("pack.mcmeta").getAsJsonObject("pack");

		assertEquals(34, pack.get("pack_format").getAsInt());
		final JsonObject supported =
			pack.getAsJsonObject("supported_formats");
		assertEquals(34, supported.get("min_inclusive").getAsInt());
		assertEquals(88, supported.get("max_inclusive").getAsInt());
		assertEquals(34,
			pack.getAsJsonArray("min_format").get(0).getAsInt());
		assertEquals(88,
			pack.getAsJsonArray("max_format").get(0).getAsInt());
	}

	@Test
	void legacyOverridesReferenceExistingModels() {
		for (final String key : ENTRIES.keySet()) {
			if (!key.startsWith("assets/minecraft/models/item/")) {
				continue;
			}
			final JsonArray overrides = json(key).getAsJsonArray("overrides");
			assertFalse(overrides.isEmpty(), "No overrides in " + key);
			for (final var override : overrides) {
				assertModelExists(
					override.getAsJsonObject().get("model").getAsString());
			}
		}
	}

	@Test
	void itemDefinitionsMirrorTheLegacyOverrides() {
		for (final String key : ENTRIES.keySet()) {
			if (!key.startsWith("assets/minecraft/models/item/")) {
				continue;
			}
			final String itemName = key.substring(
				"assets/minecraft/models/item/".length(),
				key.length() - ".json".length());
			assertDefinitionMatches(itemName, json(key));
		}
	}

	private void assertDefinitionMatches(final String itemName,
		final JsonObject legacy) {

		final JsonObject model =
			json("assets/minecraft/items/" + itemName + ".json")
				.getAsJsonObject("model");
		assertEquals("minecraft:range_dispatch",
			model.get("type").getAsString());
		assertEquals("minecraft:custom_model_data",
			model.get("property").getAsString());
		assertEquals("minecraft:item/" + itemName,
			model.getAsJsonObject("fallback").get("model").getAsString());

		final JsonArray entries = model.getAsJsonArray("entries");
		final JsonArray overrides = legacy.getAsJsonArray("overrides");
		assertEquals(overrides.size(), entries.size());
		for (int i = 0; i < overrides.size(); i++) {
			final JsonObject override = overrides.get(i).getAsJsonObject();
			final JsonObject entry = entries.get(i).getAsJsonObject();
			assertEquals(override.getAsJsonObject("predicate")
					.get("custom_model_data").getAsInt(),
				entry.get("threshold").getAsInt());
			assertEquals(override.get("model").getAsString(),
				entry.getAsJsonObject("model").get("model").getAsString());
		}
	}

	@Test
	void questaiModelsReferenceExistingTextures() {
		for (final String key : ENTRIES.keySet()) {
			if (!key.startsWith("assets/questai/models/item/")) {
				continue;
			}
			// Sprite models bind layer0; cube-built models bind numbered
			// keys and a particle. Every questai reference must resolve.
			for (final var entry
				: json(key).getAsJsonObject("textures").entrySet()) {
				final String texture = entry.getValue().getAsString();
				final String name =
					texture.substring("questai:item/".length());
				assertTrue(ENTRIES.containsKey(
					"assets/questai/textures/item/" + name + ".png"),
					"Missing texture " + texture + " for " + key);
			}
		}
	}

	@Test
	void dialogueFontReferencesItsBitmap() {
		final JsonObject provider = json("assets/questai/font/dialogue.json")
			.getAsJsonArray("providers").get(0).getAsJsonObject();
		assertEquals("questai:font/dialogue_banner.png",
			provider.get("file").getAsString());
		assertTrue(ENTRIES.containsKey(
			"assets/questai/textures/font/dialogue_banner.png"));
	}

	@Test
	void allPngEntriesDecode() throws Exception {
		for (final Map.Entry<String, byte[]> entry : ENTRIES.entrySet()) {
			if (!entry.getKey().endsWith(".png")) {
				continue;
			}
			final BufferedImage img = ImageIO.read(
				new ByteArrayInputStream(entry.getValue()));
			assertNotNull(img, "Undecodable PNG: " + entry.getKey());
			assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
		}
	}

	private void assertModelExists(final String model) {
		assertTrue(model.startsWith("questai:item/"),
			"Unexpected model namespace: " + model);
		final String name = model.substring("questai:item/".length());
		assertTrue(ENTRIES.containsKey(
			"assets/questai/models/item/" + name + ".json"),
			"Missing model entry for " + model);
	}
}
