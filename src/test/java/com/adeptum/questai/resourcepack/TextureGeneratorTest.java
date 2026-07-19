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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextureGeneratorTest {

	private static final List<Supplier<byte[]>> RELIC_TEXTURES = List.of(
		TextureGenerator::relicQuill,
		TextureGenerator::relicCharm,
		TextureGenerator::relicCompass,
		TextureGenerator::relicLocket,
		TextureGenerator::relicBell);

	@Test
	void relicTexturesDecodeAsSixteenPixelSprites() throws Exception {
		for (final Supplier<byte[]> texture : RELIC_TEXTURES) {
			final byte[] bytes = texture.get();
			assertTrue(bytes.length > 0);

			final BufferedImage img =
				ImageIO.read(new ByteArrayInputStream(bytes));
			assertNotNull(img);
			assertEquals(16, img.getWidth());
			assertEquals(16, img.getHeight());
			assertTrue(hasVisiblePixels(img));
		}
	}

	private static boolean hasVisiblePixels(final BufferedImage img) {
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if ((img.getRGB(x, y) >>> 24) != 0) {
					return true;
				}
			}
		}
		return false;
	}
}
