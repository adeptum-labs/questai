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

package com.adeptum.questai.quest;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class DestinationMarkerRenderer extends MapRenderer {
	private final Location destination;
	private boolean hasRendered;

	public DestinationMarkerRenderer(Location destination) {
		super();
		this.destination = destination;
	}

	@Override
	public void render(MapView mapView, MapCanvas mapCanvas, Player player) {
		if (hasRendered) {
			return;
		}
		hasRendered = true;

		final int dx = destination.getBlockX() - mapView.getCenterX();
		final int dz = destination.getBlockZ() - mapView.getCenterZ();
		final int scaleVal = 1 << mapView.getScale().getValue();
		final int markerX = 64 + dx / scaleVal;
		final int markerZ = 64 + dz / scaleVal;

		final byte color = (byte) 116; // bright red
		if (markerX >= 1 && markerX < 127 && markerZ >= 1 && markerZ < 127) {
			mapCanvas.setPixel(markerX, markerZ, color);
			mapCanvas.setPixel(markerX - 1, markerZ, color);
			mapCanvas.setPixel(markerX + 1, markerZ, color);
			mapCanvas.setPixel(markerX, markerZ - 1, color);
			mapCanvas.setPixel(markerX, markerZ + 1, color);
		}
	}
}
