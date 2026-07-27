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
		TextureGenerator::relicBell,
		TextureGenerator::starFragment,
		TextureGenerator::whetstone);

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

	@Test
	void theWingIsBrightEnoughToReadAgainstTheSky() throws Exception {
		final BufferedImage img = ImageIO.read(
			new ByteArrayInputStream(TextureGenerator.pigWing()));

		assertEquals(16, img.getWidth());
		assertEquals(16, img.getHeight());
		// The flap has to carry from the ground, so the feathers stay pale
		// and the rows stay distinct from one another
		assertTrue(brightness(img, 0, 1) > 200, "the shoulder reads dark");
		assertTrue(brightness(img, 8, 1) > brightness(img, 8, 6),
			"the feather rows must shade apart to be legible");
	}

	private static int brightness(final BufferedImage img, final int x,
		final int y) {

		final int rgb = img.getRGB(x, y);
		return (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
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
