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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Generates themed 16x16 pixel textures for the dialogue GUI resource pack.
 * Uses a warm parchment/wood colour palette.
 */
final class TextureGenerator {

	private static final Color WOOD_DARK = new Color(0x3C2A1A);
	private static final Color WOOD_MID = new Color(0x5C4033);
	private static final Color WOOD_LIGHT = new Color(0x6B4D2D);
	private static final Color WOOD_GRAIN = new Color(0x4A3520);

	private static final Color PARCHMENT = new Color(0xD4C4A0);
	private static final Color PARCHMENT_DARK = new Color(0xB8A47C);
	private static final Color PARCHMENT_EDGE = new Color(0x9C8860);

	private static final Color GREEN_BG = new Color(0x2D7E3D);
	private static final Color GREEN_BORDER = new Color(0x1A5C28);
	private static final Color GREEN_HIGHLIGHT = new Color(0x44A858);

	private static final Color YELLOW_BG = new Color(0xC8960F);
	private static final Color YELLOW_BORDER = new Color(0x8B6914);
	private static final Color YELLOW_HIGHLIGHT = new Color(0xE0B040);

	private static final Color RED_BG = new Color(0xB03030);
	private static final Color RED_BORDER = new Color(0x7A1A1A);
	private static final Color RED_HIGHLIGHT = new Color(0xD04848);

	private static final Color EMERALD_BG = new Color(0x2A9D5C);
	private static final Color EMERALD_BORDER = new Color(0x1A7040);
	private static final Color EMERALD_HIGHLIGHT = new Color(0x40C878);

	private static final Color ICON_WHITE = new Color(0xFFFFFF);
	private static final Color ICON_SHADOW = new Color(0x00000040, true);

	private TextureGenerator() {
	}

	static byte[] fillerPane() {
		final BufferedImage img = create();
		fillRect(img, 0, 0, 16, 16, WOOD_DARK);
		// Wood grain lines
		for (int y = 2; y < 16; y += 4) {
			fillRect(img, 0, y, 16, 1, WOOD_GRAIN);
		}
		// Subtle border
		drawRect(img, 0, 0, 16, 16, WOOD_MID);
		// Nail/stud details in corners
		setPixel(img, 2, 2, WOOD_LIGHT);
		setPixel(img, 13, 2, WOOD_LIGHT);
		setPixel(img, 2, 13, WOOD_LIGHT);
		setPixel(img, 13, 13, WOOD_LIGHT);
		return encode(img);
	}

	static byte[] dialoguePaper() {
		final BufferedImage img = create();
		fillRect(img, 0, 0, 16, 16, PARCHMENT);
		// Aged edge effect
		fillRect(img, 0, 0, 16, 1, PARCHMENT_EDGE);
		fillRect(img, 0, 15, 16, 1, PARCHMENT_EDGE);
		fillRect(img, 0, 0, 1, 16, PARCHMENT_EDGE);
		fillRect(img, 15, 0, 1, 16, PARCHMENT_EDGE);
		// Text lines
		for (int y = 4; y <= 12; y += 2) {
			fillRect(img, 3, y, 10, 1, PARCHMENT_DARK);
		}
		// Seal/stamp in corner
		fillRect(img, 11, 11, 3, 3, new Color(0x8B2020));
		return encode(img);
	}

	static byte[] npcHead() {
		final BufferedImage img = create();
		// Simple villager-like face on parchment bg
		fillRect(img, 0, 0, 16, 16, PARCHMENT);
		drawRect(img, 0, 0, 16, 16, PARCHMENT_EDGE);
		// Face outline
		fillRect(img, 4, 3, 8, 10, new Color(0xC8A878));
		// Eyes
		fillRect(img, 5, 6, 2, 2, new Color(0x3C2A1A));
		fillRect(img, 9, 6, 2, 2, new Color(0x3C2A1A));
		// Nose
		fillRect(img, 7, 8, 2, 3, new Color(0xB09060));
		return encode(img);
	}

	static byte[] chatButton() {
		return makeButton(YELLOW_BG, YELLOW_BORDER, YELLOW_HIGHLIGHT,
			TextureGenerator::drawSpeechBubble);
	}

	static byte[] helpButton() {
		return makeButton(GREEN_BG, GREEN_BORDER, GREEN_HIGHLIGHT,
			TextureGenerator::drawQuestionMark);
	}

	static byte[] continueButton() {
		return makeButton(GREEN_BG, GREEN_BORDER, GREEN_HIGHLIGHT,
			TextureGenerator::drawArrowRight);
	}

	static byte[] tradeButton() {
		return makeButton(EMERALD_BG, EMERALD_BORDER, EMERALD_HIGHLIGHT,
			TextureGenerator::drawEmerald);
	}

	static byte[] goodbyeButton() {
		return makeButton(RED_BG, RED_BORDER, RED_HIGHLIGHT,
			TextureGenerator::drawWave);
	}

	static byte[] acceptButton() {
		return makeButton(GREEN_BG, GREEN_BORDER, GREEN_HIGHLIGHT,
			TextureGenerator::drawCheckmark);
	}

	static byte[] rejectButton() {
		return makeButton(RED_BG, RED_BORDER, RED_HIGHLIGHT,
			TextureGenerator::drawCross);
	}

	static byte[] waitButton() {
		final Color gray = new Color(0x706860);
		final Color grayBorder = new Color(0x504840);
		final Color grayHi = new Color(0x908880);
		return makeButton(gray, grayBorder, grayHi,
			TextureGenerator::drawHourglass);
	}

	private static byte[] makeButton(final Color bg, final Color border,
		final Color highlight, final IconDrawer icon) {

		final BufferedImage img = create();
		// Rounded button shape
		fillRect(img, 1, 0, 14, 16, bg);
		fillRect(img, 0, 1, 16, 14, bg);
		// Border
		fillRect(img, 1, 0, 14, 1, border);
		fillRect(img, 1, 15, 14, 1, border);
		fillRect(img, 0, 1, 1, 14, border);
		fillRect(img, 15, 1, 1, 14, border);
		// Top highlight for 3D effect
		fillRect(img, 2, 1, 12, 1, highlight);
		fillRect(img, 1, 2, 1, 2, highlight);
		// Bottom shadow
		fillRect(img, 2, 14, 12, 1, border);
		fillRect(img, 14, 12, 1, 2, border);
		// Draw icon
		icon.draw(img);
		return encode(img);
	}

	// Icon drawers — each renders a simple pixel-art icon centered on 16x16

	private static void drawSpeechBubble(final BufferedImage img) {
		// Bubble body
		fillRect(img, 3, 3, 10, 7, ICON_WHITE);
		fillRect(img, 4, 2, 8, 1, ICON_WHITE);
		fillRect(img, 4, 10, 8, 1, ICON_WHITE);
		// Tail
		setPixel(img, 5, 11, ICON_WHITE);
		setPixel(img, 4, 12, ICON_WHITE);
		// Dots inside (...)
		setPixel(img, 5, 6, ICON_SHADOW);
		setPixel(img, 7, 6, ICON_SHADOW);
		setPixel(img, 9, 6, ICON_SHADOW);
	}

	private static void drawQuestionMark(final BufferedImage img) {
		// Top curve of ?
		fillRect(img, 6, 3, 4, 1, ICON_WHITE);
		setPixel(img, 5, 4, ICON_WHITE);
		setPixel(img, 10, 4, ICON_WHITE);
		setPixel(img, 10, 5, ICON_WHITE);
		fillRect(img, 8, 6, 2, 1, ICON_WHITE);
		fillRect(img, 7, 7, 2, 1, ICON_WHITE);
		fillRect(img, 7, 8, 1, 2, ICON_WHITE);
		// Dot
		fillRect(img, 7, 11, 1, 2, ICON_WHITE);
	}

	private static void drawArrowRight(final BufferedImage img) {
		// Shaft
		fillRect(img, 3, 7, 8, 2, ICON_WHITE);
		// Arrow head
		fillRect(img, 10, 5, 1, 6, ICON_WHITE);
		fillRect(img, 11, 6, 1, 4, ICON_WHITE);
		fillRect(img, 12, 7, 1, 2, ICON_WHITE);
	}

	private static void drawEmerald(final BufferedImage img) {
		// Diamond/emerald shape
		fillRect(img, 7, 3, 2, 1, ICON_WHITE);
		fillRect(img, 6, 4, 4, 1, ICON_WHITE);
		fillRect(img, 5, 5, 6, 1, ICON_WHITE);
		fillRect(img, 4, 6, 8, 1, ICON_WHITE);
		fillRect(img, 4, 7, 8, 1, ICON_WHITE);
		fillRect(img, 4, 8, 8, 1, ICON_WHITE);
		fillRect(img, 5, 9, 6, 1, ICON_WHITE);
		fillRect(img, 6, 10, 4, 1, ICON_WHITE);
		fillRect(img, 7, 11, 2, 1, ICON_WHITE);
		// Facet line
		setPixel(img, 6, 6, ICON_SHADOW);
		setPixel(img, 7, 7, ICON_SHADOW);
		setPixel(img, 9, 6, ICON_SHADOW);
		setPixel(img, 8, 7, ICON_SHADOW);
	}

	private static void drawWave(final BufferedImage img) {
		// Simple waving hand
		// Palm
		fillRect(img, 6, 7, 5, 5, ICON_WHITE);
		// Fingers spread
		fillRect(img, 5, 4, 1, 4, ICON_WHITE);
		fillRect(img, 7, 3, 1, 4, ICON_WHITE);
		fillRect(img, 9, 3, 1, 4, ICON_WHITE);
		fillRect(img, 11, 4, 1, 4, ICON_WHITE);
		// Thumb
		fillRect(img, 4, 8, 2, 1, ICON_WHITE);
		// Motion lines
		setPixel(img, 12, 3, ICON_WHITE);
		setPixel(img, 13, 5, ICON_WHITE);
	}

	private static void drawCheckmark(final BufferedImage img) {
		// Bold checkmark
		setPixel(img, 4, 8, ICON_WHITE);
		fillRect(img, 4, 9, 2, 1, ICON_WHITE);
		fillRect(img, 5, 10, 2, 1, ICON_WHITE);
		fillRect(img, 6, 9, 2, 1, ICON_WHITE);
		fillRect(img, 7, 8, 2, 1, ICON_WHITE);
		fillRect(img, 8, 7, 2, 1, ICON_WHITE);
		fillRect(img, 9, 6, 2, 1, ICON_WHITE);
		fillRect(img, 10, 5, 2, 1, ICON_WHITE);
		fillRect(img, 11, 4, 1, 1, ICON_WHITE);
	}

	private static void drawCross(final BufferedImage img) {
		// Bold X
		for (int i = 0; i < 8; i++) {
			fillRect(img, 4 + i, 4 + i, 2, 2, ICON_WHITE);
			fillRect(img, 10 - i, 4 + i, 2, 2, ICON_WHITE);
		}
	}

	private static void drawHourglass(final BufferedImage img) {
		// Top triangle
		fillRect(img, 4, 3, 8, 1, ICON_WHITE);
		fillRect(img, 5, 4, 6, 1, ICON_WHITE);
		fillRect(img, 6, 5, 4, 1, ICON_WHITE);
		fillRect(img, 7, 6, 2, 1, ICON_WHITE);
		// Middle
		fillRect(img, 7, 7, 2, 2, ICON_WHITE);
		// Bottom triangle
		fillRect(img, 7, 9, 2, 1, ICON_WHITE);
		fillRect(img, 6, 10, 4, 1, ICON_WHITE);
		fillRect(img, 5, 11, 6, 1, ICON_WHITE);
		fillRect(img, 4, 12, 8, 1, ICON_WHITE);
	}

	// Drawing primitives

	private static BufferedImage create() {
		return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
	}

	private static void fillRect(final BufferedImage img,
		final int x, final int y, final int w, final int h, final Color c) {

		final int rgb = c.getRGB();
		for (int dy = 0; dy < h; dy++) {
			for (int dx = 0; dx < w; dx++) {
				final int px = x + dx;
				final int py = y + dy;
				if (px >= 0 && px < 16 && py >= 0 && py < 16) {
					img.setRGB(px, py, rgb);
				}
			}
		}
	}

	private static void drawRect(final BufferedImage img,
		final int x, final int y, final int w, final int h, final Color c) {

		fillRect(img, x, y, w, 1, c);
		fillRect(img, x, y + h - 1, w, 1, c);
		fillRect(img, x, y, 1, h, c);
		fillRect(img, x + w - 1, y, 1, h, c);
	}

	private static void setPixel(final BufferedImage img,
		final int x, final int y, final Color c) {

		if (x >= 0 && x < 16 && y >= 0 && y < 16) {
			img.setRGB(x, y, c.getRGB());
		}
	}

	private static byte[] encode(final BufferedImage img) {
		try {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(img, "png", out);
			return out.toByteArray();
		} catch (final IOException e) {
			throw new IllegalStateException("Failed to encode texture", e);
		}
	}

	@FunctionalInterface
	private interface IconDrawer {
		void draw(BufferedImage img);
	}
}
