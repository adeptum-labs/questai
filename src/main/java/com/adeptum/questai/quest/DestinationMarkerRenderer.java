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
