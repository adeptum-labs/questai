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

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Builds the QuestAI resource pack at runtime, serves it over HTTP,
 * and sends it to players on join.
 */
public class ResourcePackManager {

	/** CustomModelData value applied to all QuestAI GUI items. */
	public static final int CMD = 100001;

	private static final int PACK_FORMAT = 34; // MC 1.21

	private byte[] packBytes;
	private byte[] packHash;
	private HttpServer server;
	private int port;
	private String configuredHostname;

	public void initialize(final JavaPlugin plugin) {
		final var config = plugin.getConfig();
		final boolean enabled = config.getBoolean("resourcepack.enabled", true);
		if (!enabled) {
			plugin.getLogger().info("[ResourcePack] Disabled in config.");
			return;
		}

		port = config.getInt("resourcepack.port", 8163);
		configuredHostname = config.getString("resourcepack.hostname", "");

		packBytes = buildPack(plugin.getLogger());
		packHash = sha1(packBytes);

		// Save a copy for debugging / manual hosting
		try {
			final var packFile = new java.io.File(
				plugin.getDataFolder(), "questai-pack.zip");
			java.nio.file.Files.write(packFile.toPath(), packBytes);
			plugin.getLogger().info("[ResourcePack] Saved pack to "
				+ packFile.getAbsolutePath());
		} catch (final IOException e) {
			plugin.getLogger().log(Level.WARNING,
				"[ResourcePack] Could not save pack to disk", e);
		}

		try {
			server = HttpServer.create(new InetSocketAddress(port), 0);
			server.createContext("/questai-pack.zip", exchange -> {
				exchange.getResponseHeaders().set("Content-Type", "application/zip");
				exchange.sendResponseHeaders(200, packBytes.length);
				exchange.getResponseBody().write(packBytes);
				exchange.getResponseBody().close();
			});
			server.setExecutor(null);
			server.start();
			plugin.getLogger().info("[ResourcePack] HTTP server started on port "
				+ port + " (" + packBytes.length / 1024 + " KB, hash="
				+ HexFormat.of().formatHex(packHash) + ")");
		} catch (final IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[ResourcePack] Failed to start HTTP server on port " + port, e);
		}
	}

	/**
	 * Sends the resource pack to a player. The download URL is built using
	 * the configured hostname, or if not set, the address the player
	 * connected to (works for direct connections and most setups).
	 */
	public void sendToPlayer(final Player player) {
		if (packBytes == null || server == null) {
			return;
		}

		final String host = resolveHostname(player);
		final String url = "http://" + host + ":" + port + "/questai-pack.zip";
		player.setResourcePack(url, packHash,
			Component.text(
				"\u00a76QuestAI requires a resource pack for the best experience."),
			false);
	}

	private String resolveHostname(final Player player) {
		if (configuredHostname != null && !configuredHostname.isBlank()) {
			return configuredHostname;
		}
		// Use the address the player connected to — works for direct
		// connections and most reverse-proxy setups.
		final var virtualHost = player.getVirtualHost();
		if (virtualHost != null) {
			return virtualHost.getHostString();
		}
		// Last resort: server bind address
		final String serverIp = server.getAddress().getAddress().getHostAddress();
		return "0.0.0.0".equals(serverIp) ? "127.0.0.1" : serverIp;
	}

	public boolean isEnabled() {
		return packBytes != null && server != null;
	}

	public void shutdown() {
		if (server != null) {
			server.stop(0);
		}
	}

	private byte[] buildPack(final Logger logger) {
		final Map<String, byte[]> files = new LinkedHashMap<>();

		files.put("pack.mcmeta", utf8(
			"{\"pack\":{\"pack_format\":" + PACK_FORMAT
				+ ",\"description\":\"QuestAI dialogue UI\"}}"));

		// Vanilla model overrides — texture path must match the real vanilla
		// texture location (block/ for blocks, item/ for items).
		vanillaOverride(files, "yellow_dye", "item/yellow_dye",
			CMD, "questai:item/btn_chat");
		vanillaOverride(files, "green_dye", "item/green_dye",
			CMD, "questai:item/btn_help",
			CMD + 1, "questai:item/btn_continue");
		vanillaOverride(files, "red_dye", "item/red_dye",
			CMD, "questai:item/btn_goodbye");
		vanillaOverride(files, "emerald", "item/emerald",
			CMD, "questai:item/btn_trade");
		vanillaOverride(files, "green_wool", "block/green_wool",
			CMD, "questai:item/btn_accept");
		vanillaOverride(files, "red_wool", "block/red_wool",
			CMD, "questai:item/btn_reject");
		vanillaOverride(files, "clock", "item/clock_00",
			CMD, "questai:item/btn_wait");
		vanillaOverride(files, "gray_stained_glass_pane",
			"block/gray_stained_glass_pane_top",
			CMD, "questai:item/filler_pane");
		vanillaOverride(files, "paper", "item/paper",
			CMD, "questai:item/dialogue_paper");

		// Custom models + textures
		customItem(files, "btn_chat", TextureGenerator.chatButton());
		customItem(files, "btn_help", TextureGenerator.helpButton());
		customItem(files, "btn_continue", TextureGenerator.continueButton());
		customItem(files, "btn_goodbye", TextureGenerator.goodbyeButton());
		customItem(files, "btn_trade", TextureGenerator.tradeButton());
		customItem(files, "btn_accept", TextureGenerator.acceptButton());
		customItem(files, "btn_reject", TextureGenerator.rejectButton());
		customItem(files, "btn_wait", TextureGenerator.waitButton());
		customItem(files, "filler_pane", TextureGenerator.fillerPane());
		customItem(files, "dialogue_paper", TextureGenerator.dialoguePaper());

		logger.info("[ResourcePack] Built pack with " + files.size() + " entries.");
		return zip(files);
	}

	private void vanillaOverride(final Map<String, byte[]> files,
		final String itemName, final String vanillaTexture,
		final Object... cmdModelPairs) {

		final StringBuilder sb = new StringBuilder();
		sb.append("{\"parent\":\"minecraft:item/generated\",");
		sb.append("\"textures\":{\"layer0\":\"minecraft:")
			.append(vanillaTexture).append("\"},");
		sb.append("\"overrides\":[");
		for (int i = 0; i < cmdModelPairs.length; i += 2) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append("{\"predicate\":{\"custom_model_data\":")
				.append(cmdModelPairs[i])
				.append("},\"model\":\"")
				.append(cmdModelPairs[i + 1])
				.append("\"}");
		}
		sb.append("]}");

		files.put("assets/minecraft/models/item/" + itemName + ".json",
			utf8(sb.toString()));
	}

	private void customItem(final Map<String, byte[]> files,
		final String name, final byte[] texture) {

		final String model = "{\"parent\":\"minecraft:item/generated\","
			+ "\"textures\":{\"layer0\":\"questai:item/" + name + "\"}}";
		files.put("assets/questai/models/item/" + name + ".json", utf8(model));
		files.put("assets/questai/textures/item/" + name + ".png", texture);
	}

	private static byte[] utf8(final String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] zip(final Map<String, byte[]> files) {
		try {
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try (ZipOutputStream zos = new ZipOutputStream(bos)) {
				for (final var entry : files.entrySet()) {
					zos.putNextEntry(new ZipEntry(entry.getKey()));
					zos.write(entry.getValue());
					zos.closeEntry();
				}
			}
			return bos.toByteArray();
		} catch (final IOException e) {
			throw new IllegalStateException("Failed to build resource pack ZIP", e);
		}
	}

	private static byte[] sha1(final byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-1").digest(data);
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-1 not available", e);
		}
	}
}
