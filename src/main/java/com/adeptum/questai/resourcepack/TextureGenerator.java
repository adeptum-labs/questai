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

	private static final Color GOLD_LIGHT = new Color(0xF2D57A);
	private static final Color GOLD_MID = new Color(0xD4A017);
	private static final Color GOLD_DARK = new Color(0x8B6914);

	private static final Color BRASS_LIGHT = new Color(0xC9A86A);
	private static final Color BRASS_MID = new Color(0xA07D46);
	private static final Color BRASS_DARK = new Color(0x6E5426);

	private static final Color LEATHER = new Color(0x7A5230);
	private static final Color INK = new Color(0x202020);
	private static final Color SKIN = new Color(0xC8A878);

	private static final Color STAR_LIGHT = new Color(0xCFF6FF);
	private static final Color STAR_MID = new Color(0x7FD6E8);
	private static final Color STAR_DARK = new Color(0x3A8FA8);

	private static final Color FUR_LIGHT = new Color(0xA8A29A);
	private static final Color FUR_MID = new Color(0x847E76);
	private static final Color FUR_DARK = new Color(0x5C5650);
	private static final Color RAT_PINK = new Color(0xE0A0A8);
	private static final Color MEAT_RED = new Color(0xB84438);
	private static final Color MEAT_DARK = new Color(0x8A2E26);
	private static final Color BONE = new Color(0xEDE5D4);
	private static final Color COOKED_BROWN = new Color(0x9A6438);
	private static final Color COOKED_DARK = new Color(0x6E4424);
	private static final Color COOKED_SEAR = new Color(0x4A2C16);
	private static final Color COOKED_GLAZE = new Color(0xC08A50);

	private static final Color GRIT_LIGHT = new Color(0xBFB6A4);
	private static final Color GRIT_MID = new Color(0x8F877A);
	private static final Color GRIT_DARK = new Color(0x615B52);

	private static final Color FEATHER_LIGHT = new Color(0xFFFFFF);
	private static final Color FEATHER_MID = new Color(0xE4E8F0);
	private static final Color FEATHER_SHADE = new Color(0xC6CEDC);
	private static final Color FEATHER_QUILL = new Color(0xA8B2C4);

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

	/**
	 * 80x16 unfurled scroll banner rendered through the client's default font.
	 * Injected via a private-use-area codepoint at the start of the dialogue
	 * title so it overlays the chest inventory's title bar instead of the
	 * default "NPC Dialogue" plain text.
	 */
	static byte[] dialogueBanner() {
		final int w = 80;
		final int h = 16;
		final BufferedImage img = create(w, h);
		drawScrollRolls(img, w, h);
		drawParchment(img, w);
		drawQuillAndInk(img);
		drawInkLines(img, w);
		drawWaxSeal(img, w);
		return encode(img);
	}

	private static void drawScrollRolls(final BufferedImage img,
		final int w, final int h) {

		// Left wooden roll — x=0..5
		fillRect(img, 1, 2, 5, h - 4, WOOD_MID);
		drawRect(img, 1, 2, 5, h - 4, WOOD_DARK);
		fillRect(img, 2, 3, 3, 1, WOOD_LIGHT);
		fillRect(img, 2, h - 4, 3, 1, WOOD_GRAIN);
		// Roll caps (top + bottom flares)
		fillRect(img, 0, 3, 1, h - 6, WOOD_DARK);
		fillRect(img, 1, 1, 5, 1, WOOD_DARK);
		fillRect(img, 1, h - 2, 5, 1, WOOD_DARK);
		// Right wooden roll — x=(w-6)..(w-1)
		fillRect(img, w - 6, 2, 5, h - 4, WOOD_MID);
		drawRect(img, w - 6, 2, 5, h - 4, WOOD_DARK);
		fillRect(img, w - 5, 3, 3, 1, WOOD_LIGHT);
		fillRect(img, w - 5, h - 4, 3, 1, WOOD_GRAIN);
		fillRect(img, w - 1, 3, 1, h - 6, WOOD_DARK);
		fillRect(img, w - 6, 1, 5, 1, WOOD_DARK);
		fillRect(img, w - 6, h - 2, 5, 1, WOOD_DARK);
	}

	private static void drawParchment(final BufferedImage img, final int w) {
		// Unfurled parchment between the two rolls
		fillRect(img, 6, 3, w - 12, 10, PARCHMENT);
		// Aged top/bottom edges
		fillRect(img, 6, 3, w - 12, 1, PARCHMENT_EDGE);
		fillRect(img, 6, 12, w - 12, 1, PARCHMENT_EDGE);
		// Subtle shadow under each roll so the parchment reads as tucked in
		fillRect(img, 6, 4, 1, 8, PARCHMENT_DARK);
		fillRect(img, w - 7, 4, 1, 8, PARCHMENT_DARK);
	}

	private static void drawQuillAndInk(final BufferedImage img) {
		// Quill feather on the left side of the parchment
		final Color featherLight = new Color(0xE8D878);
		final Color featherMid = new Color(0xB89840);
		final Color featherDark = new Color(0x705820);
		// Shaft
		fillRect(img, 10, 5, 1, 7, featherDark);
		// Feather body
		fillRect(img, 8, 5, 2, 2, featherLight);
		fillRect(img, 9, 7, 2, 1, featherMid);
		fillRect(img, 8, 8, 2, 1, featherLight);
		fillRect(img, 9, 9, 2, 1, featherMid);
		fillRect(img, 8, 10, 2, 1, featherLight);
		// Nib + ink dot
		setPixel(img, 10, 12, INK);
		setPixel(img, 11, 11, INK);
	}

	private static void drawInkLines(final BufferedImage img, final int w) {
		// Horizontal ink strokes suggesting handwritten text
		final int textStart = 14;
		final int textEnd = w - 14;
		fillRect(img, textStart, 6, textEnd - textStart - 6, 1, PARCHMENT_DARK);
		fillRect(img, textStart, 9, textEnd - textStart, 1, PARCHMENT_DARK);
		fillRect(img, textStart, 11, textEnd - textStart - 10, 1, PARCHMENT_DARK);
	}

	private static void drawWaxSeal(final BufferedImage img, final int w) {
		// Round red wax seal on the right edge of the parchment
		final Color sealDark = new Color(0x6B1818);
		final Color sealMid = new Color(0x8B2020);
		final Color sealLight = new Color(0xC83838);
		final int cx = w - 10;
		fillRect(img, cx - 2, 6, 4, 4, sealMid);
		fillRect(img, cx - 1, 5, 2, 6, sealMid);
		fillRect(img, cx - 3, 7, 1, 2, sealMid);
		fillRect(img, cx + 2, 7, 1, 2, sealMid);
		setPixel(img, cx - 1, 6, sealLight);
		setPixel(img, cx, 5, sealLight);
		setPixel(img, cx + 1, 9, sealDark);
		setPixel(img, cx, 10, sealDark);
		// Pressed "star" in the seal
		setPixel(img, cx, 7, sealDark);
		setPixel(img, cx, 8, sealDark);
		setPixel(img, cx - 1, 8, sealDark);
		setPixel(img, cx + 1, 8, sealDark);
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

	// Relic item textures — 16x16 pixel art on transparency

	/** A golden quill resting on a parchment scrap. */
	/* default */ static byte[] relicQuill() {
		final BufferedImage img = create();
		drawParchmentScrap(img);
		drawGoldenFeather(img);
		return encode(img);
	}

	private static void drawParchmentScrap(final BufferedImage img) {
		fillRect(img, 1, 10, 9, 5, PARCHMENT);
		fillRect(img, 1, 10, 9, 1, PARCHMENT_EDGE);
		fillRect(img, 1, 14, 9, 1, PARCHMENT_EDGE);
		fillRect(img, 3, 12, 5, 1, PARCHMENT_DARK);
	}

	private static void drawGoldenFeather(final BufferedImage img) {
		// Shaft running diagonally from tip (13,1) down to the nib (4,10)
		for (int i = 0; i <= 9; i++) {
			setPixel(img, 13 - i, 1 + i, GOLD_DARK);
		}
		// Barbs above-left of the shaft, alternating light and mid gold
		fillRect(img, 11, 2, 2, 1, GOLD_LIGHT);
		fillRect(img, 10, 3, 2, 1, GOLD_MID);
		fillRect(img, 9, 4, 2, 1, GOLD_LIGHT);
		fillRect(img, 8, 5, 2, 1, GOLD_MID);
		fillRect(img, 7, 6, 2, 1, GOLD_LIGHT);
		fillRect(img, 6, 7, 2, 1, GOLD_MID);
		// Sparser barbs below-right
		setPixel(img, 13, 3, GOLD_MID);
		setPixel(img, 12, 4, GOLD_LIGHT);
		setPixel(img, 11, 5, GOLD_MID);
		setPixel(img, 10, 6, GOLD_LIGHT);
		// Nib and an ink drop on the scrap
		setPixel(img, 4, 10, INK);
		setPixel(img, 3, 11, INK);
		setPixel(img, 2, 12, INK);
	}

	/** A lucky gold nugget hanging from a leather cord. */
	/* default */ static byte[] relicCharm() {
		final BufferedImage img = create();
		drawCord(img);
		drawNugget(img);
		return encode(img);
	}

	private static void drawCord(final BufferedImage img) {
		setPixel(img, 4, 3, LEATHER);
		setPixel(img, 5, 2, LEATHER);
		fillRect(img, 6, 1, 4, 1, LEATHER);
		setPixel(img, 10, 2, LEATHER);
		setPixel(img, 11, 3, LEATHER);
		// Strands converging to the knot
		setPixel(img, 5, 4, LEATHER);
		setPixel(img, 10, 4, LEATHER);
		setPixel(img, 6, 5, LEATHER);
		setPixel(img, 9, 5, LEATHER);
		fillRect(img, 7, 5, 2, 2, WOOD_DARK);
	}

	private static void drawNugget(final BufferedImage img) {
		// Irregular blob
		fillRect(img, 6, 7, 4, 1, GOLD_MID);
		fillRect(img, 5, 8, 6, 4, GOLD_MID);
		fillRect(img, 6, 12, 4, 1, GOLD_MID);
		// Facets: highlight upper-left, shadow lower-right, one sparkle
		fillRect(img, 6, 8, 2, 1, GOLD_LIGHT);
		setPixel(img, 6, 9, GOLD_LIGHT);
		fillRect(img, 9, 11, 2, 1, GOLD_DARK);
		setPixel(img, 10, 10, GOLD_DARK);
		setPixel(img, 11, 7, ICON_WHITE);
	}

	/** A brass compass whose needle seeks people, on a parchment corner. */
	/* default */ static byte[] relicCompass() {
		final BufferedImage img = create();
		// Parchment scrap peeking out bottom-right, drawn under the dial
		fillRect(img, 9, 9, 6, 6, PARCHMENT);
		drawRect(img, 9, 9, 6, 6, PARCHMENT_EDGE);
		drawCompassRing(img);
		drawCompassFace(img);
		return encode(img);
	}

	private static void drawCompassRing(final BufferedImage img) {
		fillRect(img, 4, 1, 6, 1, BRASS_MID);
		fillRect(img, 2, 2, 2, 1, BRASS_MID);
		fillRect(img, 10, 2, 2, 1, BRASS_MID);
		fillRect(img, 1, 3, 1, 2, BRASS_MID);
		fillRect(img, 12, 3, 1, 2, BRASS_MID);
		fillRect(img, 1, 5, 1, 4, BRASS_MID);
		fillRect(img, 12, 5, 1, 4, BRASS_MID);
		fillRect(img, 1, 9, 1, 2, BRASS_MID);
		fillRect(img, 12, 9, 1, 2, BRASS_MID);
		fillRect(img, 2, 11, 2, 1, BRASS_MID);
		fillRect(img, 10, 11, 2, 1, BRASS_MID);
		fillRect(img, 4, 12, 6, 1, BRASS_MID);
		// Light catches the upper-left arc, shadow on the lower-right
		setPixel(img, 4, 1, BRASS_LIGHT);
		setPixel(img, 2, 2, BRASS_LIGHT);
		setPixel(img, 1, 3, BRASS_LIGHT);
		setPixel(img, 12, 10, BRASS_DARK);
		setPixel(img, 11, 11, BRASS_DARK);
		setPixel(img, 9, 12, BRASS_DARK);
	}

	private static void drawCompassFace(final BufferedImage img) {
		fillRect(img, 4, 2, 6, 1, PARCHMENT);
		fillRect(img, 2, 3, 10, 2, PARCHMENT);
		fillRect(img, 2, 5, 10, 4, PARCHMENT);
		fillRect(img, 2, 9, 10, 2, PARCHMENT);
		fillRect(img, 4, 11, 6, 1, PARCHMENT);
		// Cardinal ticks
		fillRect(img, 6, 2, 2, 1, PARCHMENT_DARK);
		fillRect(img, 6, 11, 2, 1, PARCHMENT_DARK);
		fillRect(img, 2, 6, 1, 2, PARCHMENT_DARK);
		fillRect(img, 11, 6, 1, 2, PARCHMENT_DARK);
		// Needle: dark hub, red arm pointing NNE, pale tail
		setPixel(img, 7, 7, INK);
		setPixel(img, 6, 7, INK);
		setPixel(img, 8, 4, RED_BG);
		setPixel(img, 8, 5, RED_BG);
		setPixel(img, 7, 6, RED_HIGHLIGHT);
		setPixel(img, 6, 8, ICON_WHITE);
		setPixel(img, 5, 9, ICON_WHITE);
	}

	/** A golden locket holding a tiny villager portrait. */
	/* default */ static byte[] relicLocket() {
		final BufferedImage img = create();
		drawLocketFrame(img);
		drawLocketFace(img);
		return encode(img);
	}

	private static void drawLocketFrame(final BufferedImage img) {
		// Bail and hinge
		setPixel(img, 7, 0, GOLD_DARK);
		setPixel(img, 8, 0, GOLD_DARK);
		setPixel(img, 6, 1, GOLD_DARK);
		setPixel(img, 9, 1, GOLD_DARK);
		fillRect(img, 7, 2, 2, 1, GOLD_MID);
		// Oval body
		fillRect(img, 6, 3, 4, 1, GOLD_MID);
		fillRect(img, 5, 4, 6, 1, GOLD_MID);
		fillRect(img, 4, 5, 8, 7, GOLD_MID);
		fillRect(img, 5, 12, 6, 1, GOLD_MID);
		fillRect(img, 6, 13, 4, 1, GOLD_MID);
		// Rim light and shadow
		setPixel(img, 6, 3, GOLD_LIGHT);
		setPixel(img, 5, 4, GOLD_LIGHT);
		setPixel(img, 4, 5, GOLD_LIGHT);
		setPixel(img, 4, 6, GOLD_LIGHT);
		setPixel(img, 11, 10, GOLD_DARK);
		setPixel(img, 10, 12, GOLD_DARK);
		setPixel(img, 9, 13, GOLD_DARK);
	}

	private static void drawLocketFace(final BufferedImage img) {
		fillRect(img, 5, 5, 6, 7, SKIN);
		// Unibrow, eyes and the long villager nose
		fillRect(img, 6, 6, 4, 1, WOOD_GRAIN);
		setPixel(img, 6, 7, WOOD_DARK);
		setPixel(img, 9, 7, WOOD_DARK);
		fillRect(img, 7, 8, 2, 2, new Color(0xB09060));
	}

	/** A brass hand-bell with a wooden handle. */
	/* default */ static byte[] relicBell() {
		final BufferedImage img = create();
		drawBellHandle(img);
		drawBellBody(img);
		return encode(img);
	}

	private static void drawBellHandle(final BufferedImage img) {
		fillRect(img, 6, 0, 4, 1, WOOD_DARK);
		fillRect(img, 7, 1, 2, 3, WOOD_MID);
		fillRect(img, 6, 1, 1, 3, WOOD_DARK);
		fillRect(img, 9, 1, 1, 3, WOOD_DARK);
		setPixel(img, 7, 2, WOOD_GRAIN);
	}

	private static void drawBellBody(final BufferedImage img) {
		// Flaring brass body with skirt and darker rim
		fillRect(img, 6, 4, 4, 2, BRASS_MID);
		fillRect(img, 5, 6, 6, 2, BRASS_MID);
		fillRect(img, 4, 8, 8, 2, BRASS_MID);
		fillRect(img, 3, 10, 10, 1, BRASS_MID);
		fillRect(img, 3, 11, 10, 1, BRASS_DARK);
		// Shading along the edges and the clapper below the rim
		fillRect(img, 6, 5, 1, 5, BRASS_LIGHT);
		fillRect(img, 10, 6, 1, 2, BRASS_DARK);
		fillRect(img, 11, 8, 1, 2, BRASS_DARK);
		fillRect(img, 7, 12, 2, 2, INK);
		// Motion strokes
		setPixel(img, 1, 7, ICON_SHADOW);
		setPixel(img, 2, 9, ICON_SHADOW);
		setPixel(img, 14, 7, ICON_SHADOW);
		setPixel(img, 13, 9, ICON_SHADOW);
	}

	/** The fallen star's prize: a pale glowing crystal shard. */
	/* default */ static byte[] starFragment() {
		final BufferedImage img = create();
		drawShardBody(img);
		drawShardSparkles(img);
		return encode(img);
	}

	private static void drawShardBody(final BufferedImage img) {
		// Faceted shard leaning from upper-right tip to lower-left base
		setPixel(img, 10, 2, STAR_LIGHT);
		fillRect(img, 9, 3, 2, 1, STAR_MID);
		fillRect(img, 8, 4, 3, 1, STAR_MID);
		fillRect(img, 6, 5, 5, 3, STAR_MID);
		fillRect(img, 5, 8, 5, 2, STAR_MID);
		fillRect(img, 4, 10, 4, 2, STAR_MID);
		fillRect(img, 4, 12, 2, 1, STAR_MID);
		// Highlight along the upper-left edge, shade lower-right
		fillRect(img, 6, 5, 1, 3, STAR_LIGHT);
		fillRect(img, 5, 8, 1, 2, STAR_LIGHT);
		fillRect(img, 9, 9, 1, 1, STAR_DARK);
		fillRect(img, 7, 11, 1, 1, STAR_DARK);
		setPixel(img, 5, 12, STAR_DARK);
		// Internal facet line running tip to base
		for (int i = 0; i <= 8; i++) {
			setPixel(img, 9 - i * 5 / 8, 4 + i, STAR_DARK);
		}
	}

	private static void drawShardSparkles(final BufferedImage img) {
		setPixel(img, 13, 4, ICON_WHITE);
		setPixel(img, 2, 6, ICON_WHITE);
		setPixel(img, 12, 11, ICON_WHITE);
		setPixel(img, 7, 1, ICON_WHITE);
		// A soft glint beside each sparkle
		setPixel(img, 14, 5, STAR_LIGHT);
		setPixel(img, 3, 7, STAR_LIGHT);
		setPixel(img, 13, 12, STAR_LIGHT);
		setPixel(img, 8, 0, STAR_LIGHT);
	}

	/** A standing rune stone with a glowing portal carved into its face. */
	/* default */ static byte[] teleportStone() {
		final BufferedImage img = create();
		drawStandingStone(img);
		drawStoneGateway(img);
		return encode(img);
	}

	private static void drawStandingStone(final BufferedImage img) {
		// A rounded menhir, wider at the base than the crown
		fillRect(img, 6, 1, 4, 1, FUR_MID);
		fillRect(img, 5, 2, 6, 1, FUR_MID);
		fillRect(img, 4, 3, 8, 11, FUR_MID);
		fillRect(img, 5, 14, 6, 1, FUR_MID);
		// Light catches the left flank, shadow gathers on the right and foot
		fillRect(img, 4, 3, 1, 11, FUR_LIGHT);
		setPixel(img, 5, 2, FUR_LIGHT);
		fillRect(img, 11, 3, 1, 11, FUR_DARK);
		setPixel(img, 10, 2, FUR_DARK);
		fillRect(img, 5, 14, 6, 1, FUR_DARK);
	}

	private static void drawStoneGateway(final BufferedImage img) {
		// A glowing oval doorway sunk into the stone
		fillRect(img, 7, 4, 2, 8, STAR_DARK);
		fillRect(img, 6, 6, 4, 4, STAR_DARK);
		fillRect(img, 7, 5, 2, 6, STAR_MID);
		fillRect(img, 7, 7, 2, 2, STAR_LIGHT);
		setPixel(img, 8, 3, STAR_MID);
		setPixel(img, 8, 12, STAR_MID);
	}

	/* default */ static byte[] whetstone() {
		final BufferedImage img = create();
		drawWhetstoneHaft(img);
		drawWhetstoneGrit(img);
		return encode(img);
	}

	private static void drawWhetstoneHaft(final BufferedImage img) {
		// A short wooden haft at the near end, ringed with brass
		fillRect(img, 2, 10, 4, 4, LEATHER);
		fillRect(img, 2, 10, 4, 1, BRASS_LIGHT);
		fillRect(img, 2, 13, 4, 1, BRASS_DARK);
		fillRect(img, 5, 10, 1, 4, BRASS_MID);
	}

	private static void drawWhetstoneGrit(final BufferedImage img) {
		// A bevelled block of grit rising away from the haft, lit along the
		// working face and shadowed on the far side and foot
		fillRect(img, 6, 6, 8, 6, GRIT_MID);
		fillRect(img, 6, 5, 6, 1, GRIT_LIGHT);
		fillRect(img, 6, 6, 8, 1, GRIT_LIGHT);
		fillRect(img, 6, 11, 8, 1, GRIT_DARK);
		fillRect(img, 13, 6, 1, 5, GRIT_DARK);
		fillRect(img, 7, 8, 4, 1, GRIT_LIGHT);
	}

	/* default */ static byte[] ratMeat() {
		final BufferedImage img = create();
		// A small haunch: rounded red flesh tapering toward a bone stub
		fillRect(img, 4, 6, 8, 6, MEAT_RED);
		fillRect(img, 5, 5, 6, 1, MEAT_RED);
		fillRect(img, 5, 12, 6, 1, MEAT_RED);
		fillRect(img, 3, 7, 1, 4, MEAT_RED);
		// Shading along the lower edge, highlight on top
		fillRect(img, 5, 11, 6, 2, MEAT_DARK);
		fillRect(img, 4, 10, 1, 2, MEAT_DARK);
		fillRect(img, 5, 5, 3, 1, RAT_PINK);
		fillRect(img, 4, 6, 2, 2, RAT_PINK);
		// The bone, poking out to the upper right
		fillRect(img, 11, 4, 2, 2, BONE);
		setPixel(img, 12, 3, BONE);
		setPixel(img, 13, 3, BONE);
		setPixel(img, 13, 5, BONE);
		return encode(img);
	}

	/** The raw haunch's silhouette in browned, seared tones. */
	/* default */ static byte[] ratMeatCooked() {
		final BufferedImage img = create();
		// Same haunch as the raw sprite, browned through
		fillRect(img, 4, 6, 8, 6, COOKED_BROWN);
		fillRect(img, 5, 5, 6, 1, COOKED_BROWN);
		fillRect(img, 5, 12, 6, 1, COOKED_BROWN);
		fillRect(img, 3, 7, 1, 4, COOKED_BROWN);
		// Sear underneath, glaze highlight on top
		fillRect(img, 5, 11, 6, 2, COOKED_DARK);
		fillRect(img, 4, 10, 1, 2, COOKED_DARK);
		fillRect(img, 5, 5, 3, 1, COOKED_GLAZE);
		fillRect(img, 4, 6, 2, 2, COOKED_GLAZE);
		// Grill marks
		fillRect(img, 6, 7, 1, 4, COOKED_SEAR);
		fillRect(img, 9, 7, 1, 4, COOKED_SEAR);
		// The bone, scorched at the joint
		fillRect(img, 11, 4, 2, 2, BONE);
		setPixel(img, 12, 3, BONE);
		setPixel(img, 13, 3, BONE);
		setPixel(img, 13, 5, BONE);
		setPixel(img, 11, 5, COOKED_SEAR);
		return encode(img);
	}

	/**
	 * The 16x16 atlas the 3D rat model maps its cube faces onto: fur in the
	 * upper region, a face patch with eyes and nose lower-left, bright pink
	 * for ears and tail lower-right.
	 */
	/* default */ static byte[] ratBody() {
		final BufferedImage img = create();
		// Fur field with mottling so large faces do not read as flat
		fillRect(img, 0, 0, 16, 11, FUR_MID);
		for (int y = 0; y < 11; y += 2) {
			for (int x = y % 4 / 2; x < 16; x += 4) {
				setPixel(img, x, y, FUR_DARK);
				setPixel(img, (x + 2) % 16, y + 1, FUR_LIGHT);
			}
		}
		// Belly strip
		fillRect(img, 0, 9, 16, 2, FUR_LIGHT);
		// Face patch: fur with two dark eyes and a pink nose
		fillRect(img, 0, 12, 6, 4, FUR_MID);
		setPixel(img, 1, 13, FUR_DARK);
		setPixel(img, 4, 13, FUR_DARK);
		fillRect(img, 2, 15, 2, 1, RAT_PINK);
		// Pink block for ears and tail
		fillRect(img, 8, 12, 8, 4, RAT_PINK);
		fillRect(img, 8, 12, 8, 1, FUR_MID);
		return encode(img);
	}

	/**
	 * A feathered wing: three bands of white running from the shoulder out
	 * to the tips, each darker than the last so the layers read as separate
	 * rows of feathers rather than one white slab. Kept bright, because
	 * these fly high enough that the flap has to carry at distance.
	 */
	/* default */ static byte[] pigWing() {
		final BufferedImage img = create();
		// The membrane the feathers are laid over
		fillRect(img, 0, 0, 16, 8, FEATHER_LIGHT);
		// Three rows of feather tips, shading outward from the shoulder
		fillRect(img, 0, 3, 16, 2, FEATHER_MID);
		fillRect(img, 0, 5, 16, 3, FEATHER_SHADE);
		for (int x = 1; x < 16; x += 3) {
			// The quill dividing one feather from the next
			fillRect(img, x, 2, 1, 6, FEATHER_QUILL);
			setPixel(img, x, 7, FEATHER_SHADE);
		}
		// The shoulder end stays plain: it sits against the pig
		fillRect(img, 0, 0, 3, 8, FEATHER_LIGHT);
		// Edge strip, used by the wing's thin sides
		fillRect(img, 0, 8, 16, 2, FEATHER_MID);
		return encode(img);
	}

	// Drawing primitives

	private static BufferedImage create() {
		return create(16, 16);
	}

	private static BufferedImage create(final int w, final int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	private static void fillRect(final BufferedImage img,
		final int x, final int y, final int w, final int h, final Color c) {

		final int rgb = c.getRGB();
		final int imgW = img.getWidth();
		final int imgH = img.getHeight();
		for (int dy = 0; dy < h; dy++) {
			for (int dx = 0; dx < w; dx++) {
				final int px = x + dx;
				final int py = y + dy;
				if (px >= 0 && px < imgW && py >= 0 && py < imgH) {
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

		if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) {
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
