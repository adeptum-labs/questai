package com.adeptum.questai.fortify;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

/**
 * Resolves a plan's material roles into real blocks, so one layout reads as
 * oak on the plains and spruce in the taiga.
 *
 * <p>Touches only Material and BlockData, both of which resolve from the
 * server's registry, so this class cannot be constructed in a unit test.
 */
public final class BiomePalette {

	private final Map<PaletteRole, Material> materials;

	private BiomePalette(final Map<PaletteRole, Material> materials) {
		this.materials = materials;
	}

	/** The palette a village in this biome builds with. */
	public static BiomePalette forBiome(final Biome biome) {
		final String name = biome.getKey().getKey();
		if (containsAny(name, "desert", "badlands")) {
			return new BiomePalette(desert());
		}
		if (containsAny(name, "taiga", "snowy", "grove")) {
			return new BiomePalette(wood(Material.SPRUCE_LOG,
				Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS,
				Material.SPRUCE_SLAB, Material.SPRUCE_FENCE,
				Material.SPRUCE_TRAPDOOR, Material.SPRUCE_DOOR));
		}
		if (containsAny(name, "savanna")) {
			return new BiomePalette(wood(Material.ACACIA_LOG,
				Material.ACACIA_PLANKS, Material.ACACIA_STAIRS,
				Material.ACACIA_SLAB, Material.ACACIA_FENCE,
				Material.ACACIA_TRAPDOOR, Material.ACACIA_DOOR));
		}
		return new BiomePalette(wood(Material.OAK_LOG, Material.OAK_PLANKS,
			Material.OAK_STAIRS, Material.OAK_SLAB, Material.OAK_FENCE,
			Material.OAK_TRAPDOOR, Material.OAK_DOOR));
	}

	/** Whether the biome key contains any of the given keywords. */
	private static boolean containsAny(final String name,
		final String... keywords) {

		for (final String keyword : keywords) {
			if (name.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	/** The block to write for this role, with its state applied. */
	public BlockData resolve(final PaletteRole role, final String state) {
		final Material material = materials.get(role);
		if (material == null) {
			return null;
		}
		if (state == null || state.isEmpty()) {
			return material.createBlockData();
		}
		return Bukkit.createBlockData(
			"minecraft:" + material.getKey().getKey() + "[" + state + "]");
	}

	private static Map<PaletteRole, Material> wood(final Material log,
		final Material planks, final Material stairs, final Material slab,
		final Material fence, final Material trapdoor, final Material door) {

		final Map<PaletteRole, Material> map =
			new EnumMap<>(PaletteRole.class);
		map.put(PaletteRole.LOG_Y, log);
		map.put(PaletteRole.LOG_X, log);
		map.put(PaletteRole.STRIPPED_LOG, Material.STRIPPED_SPRUCE_LOG);
		map.put(PaletteRole.PLANKS, planks);
		map.put(PaletteRole.WOOD_STAIRS, stairs);
		map.put(PaletteRole.WOOD_SLAB, slab);
		map.put(PaletteRole.FENCE, fence);
		map.put(PaletteRole.TRAPDOOR, trapdoor);
		map.put(PaletteRole.DOOR, door);
		map.putAll(shared());
		map.put(PaletteRole.ROUGH_STONE, Material.COBBLESTONE);
		map.put(PaletteRole.MOSSY_STONE, Material.MOSSY_COBBLESTONE);
		map.put(PaletteRole.ANDESITE, Material.ANDESITE);
		map.put(PaletteRole.DRESSED_STONE, Material.STONE_BRICKS);
		map.put(PaletteRole.CRACKED_STONE, Material.CRACKED_STONE_BRICKS);
		map.put(PaletteRole.STONE_STAIRS, Material.COBBLESTONE_STAIRS);
		map.put(PaletteRole.STONE_WALL, Material.COBBLESTONE_WALL);
		return map;
	}

	private static Map<PaletteRole, Material> desert() {
		final Map<PaletteRole, Material> map = wood(Material.JUNGLE_LOG,
			Material.JUNGLE_PLANKS, Material.JUNGLE_STAIRS,
			Material.JUNGLE_SLAB, Material.JUNGLE_FENCE,
			Material.JUNGLE_TRAPDOOR, Material.JUNGLE_DOOR);
		map.put(PaletteRole.ROUGH_STONE, Material.SANDSTONE);
		map.put(PaletteRole.MOSSY_STONE, Material.SMOOTH_SANDSTONE);
		map.put(PaletteRole.ANDESITE, Material.SANDSTONE);
		map.put(PaletteRole.DRESSED_STONE, Material.CUT_SANDSTONE);
		map.put(PaletteRole.CRACKED_STONE, Material.CHISELED_SANDSTONE);
		map.put(PaletteRole.STONE_STAIRS, Material.SANDSTONE_STAIRS);
		map.put(PaletteRole.STONE_WALL, Material.SANDSTONE_WALL);
		return map;
	}

	private static Map<PaletteRole, Material> shared() {
		final Map<PaletteRole, Material> map =
			new EnumMap<>(PaletteRole.class);
		map.put(PaletteRole.LADDER, Material.LADDER);
		map.put(PaletteRole.LANTERN, Material.LANTERN);
		map.put(PaletteRole.WALL_TORCH, Material.WALL_TORCH);
		map.put(PaletteRole.CAMPFIRE, Material.CAMPFIRE);
		map.put(PaletteRole.BANNER, Material.WHITE_BANNER);
		map.put(PaletteRole.BARREL, Material.BARREL);
		map.put(PaletteRole.HAY, Material.HAY_BLOCK);
		map.put(PaletteRole.CAULDRON, Material.WATER_CAULDRON);
		map.put(PaletteRole.SCAFFOLDING, Material.SCAFFOLDING);
		return map;
	}
}
