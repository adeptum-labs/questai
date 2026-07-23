package com.adeptum.questai.fortify;

import com.adeptum.questai.SubPlugin;
import com.adeptum.questai.mob.MobForge;
import com.adeptum.questai.village.NamedVillage;
import com.adeptum.questai.village.VillageRegistry;
import com.adeptum.questai.villager.MemoryEvent;
import com.adeptum.questai.villager.StoredLocation;
import com.adeptum.questai.villager.VillagerProfileStore;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Turns donated material into buildings that go up stage by stage.
 *
 * <p>Deliberately thin: every decision it makes lives in a pure class beside
 * it, because this one cannot be constructed without a running server.
 */
public class VillageFortification implements SubPlugin {

	private static final long TICK_INTERVAL = 40L;
	private static final int MAX_LEVEL_SPREAD = 3;
	private static final Map<VillageWork, WorkSchematic> SCHEMATICS =
		loadSchematics();

	private final JavaPlugin plugin;
	private final VillageRegistry registry;
	private final VillageWorksStore worksStore;
	private final VillagerProfileStore profileStore;
	private final MobForge mobForge;
	private final boolean enabled;
	private final long stageIntervalMillis;
	private final double witnessRadius;
	private BukkitTask task;

	public VillageFortification(final JavaPlugin plugin,
		final VillageRegistry registry, final VillageWorksStore worksStore,
		final VillagerProfileStore profileStore, final MobForge mobForge) {

		this.plugin = plugin;
		this.registry = registry;
		this.worksStore = worksStore;
		this.profileStore = profileStore;
		this.mobForge = mobForge;
		this.enabled =
			plugin.getConfig().getBoolean("villages.fortify.enabled", true);
		this.stageIntervalMillis = plugin.getConfig()
			.getLong("villages.fortify.stage-interval-seconds", 300L) * 1000L;
		this.witnessRadius =
			plugin.getConfig().getDouble("villages.fortify.witness-radius", 96.0);
	}

	@Override
	public void onEnable() {
		if (!enabled) {
			return;
		}
		this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
			TICK_INTERVAL, TICK_INTERVAL);
	}

	@Override
	public void onDisable() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	/** The works key for the village at this location, or null when none. */
	public String rowIdAt(final Location location) {
		final NamedVillage village = registry.find(location);
		return village == null ? null : VillageRegistry.rowIdFor(village);
	}

	/** Every tier that comes with a point-structure drawing. */
	private static Map<VillageWork, WorkSchematic> loadSchematics() {
		final Map<VillageWork, WorkSchematic> map =
			new EnumMap<>(VillageWork.class);
		for (final VillageWork work : VillageWork.values()) {
			if (work.getSchematicResource() != null) {
				map.put(work, WorkSchematic.load(work.getSchematicResource()));
			}
		}
		return map;
	}

	/**
	 * Takes everything the player carries toward the active project, up to what
	 * is still outstanding, and starts the build once it is fully funded.
	 */
	public void donate(final Player player, final String rowId) {
		if (rowId == null) {
			return;
		}
		final WorkState state = worksStore.get(rowId);
		final VillageWork work =
			VillageWork.byTier(state == null ? 0 : state.getTier());
		if (!acceptingDonations(work, state)) {
			return;
		}

		final int given = applyDonations(player, rowId, work, state);
		if (given == 0) {
			player.sendMessage("§7They have all they need from you for now.");
			return;
		}
		player.sendMessage("§aYou hand over " + given
			+ " toward " + work.getDisplayName() + ".");
		startBuildIfFunded(player, rowId, work);
	}

	/** Whether this village still wants donations toward its active tier. */
	private static boolean acceptingDonations(final VillageWork work,
		final WorkState state) {

		return work != null && work.hasPlans()
			&& (state == null || state.getSite() == null);
	}

	/** Takes what is still outstanding from the player, and reports it. */
	private int applyDonations(final Player player, final String rowId,
		final VillageWork work, final WorkState state) {

		int given = 0;
		for (final Map.Entry<String, Integer> need
			: work.getRequirements().entrySet()) {

			final int have = state == null ? 0
				: state.getTally().getOrDefault(need.getKey(), 0);
			final int shortfall = need.getValue() - have;
			if (shortfall <= 0) {
				continue;
			}
			final int taken = MaterialTally.consume(player.getInventory(),
				work.accepted(need.getKey()), shortfall);
			if (taken > 0) {
				worksStore.donate(rowId, need.getKey(), taken);
				given += taken;
			}
		}
		return given;
	}

	private void startBuildIfFunded(final Player player, final String rowId,
		final VillageWork work) {

		final WorkState state = worksStore.get(rowId);
		final boolean funded = work.getRequirements().entrySet().stream()
			.allMatch(need -> state.getTally()
				.getOrDefault(need.getKey(), 0) >= need.getValue());
		if (!funded) {
			return;
		}

		final NamedVillage village = registry.find(player.getLocation());
		final WorkSite.Candidate site = village == null ? null
			: findSite(player.getLocation().getWorld(), village, work);
		if (site == null) {
			player.sendMessage(
				"§7They have what they need, but nowhere level to build.");
			return;
		}

		worksStore.beginBuild(rowId,
			new StoredLocation(player.getWorld().getUID(),
				site.x(), site.baseY(), site.z()),
			site.rotation());
		player.sendMessage("§6Work begins on " + work.getDisplayName() + ".");
		// The survey stakes go in while the donor is still standing there
		advance(rowId, worksStore.get(rowId), System.currentTimeMillis());
	}

	/**
	 * Walks a ring around the village looking for a footprint that is natural,
	 * unoccupied and flat enough to level. Returns null rather than forcing a
	 * bad site, the same way the raid spawner skips a spawn it cannot ground.
	 */
	private WorkSite.Candidate findSite(final org.bukkit.World world,
		final NamedVillage village, final VillageWork work) {

		final WorkSchematic schematic = SCHEMATICS.get(work);
		final int[] band = searchBand(work);
		if (schematic == null || band == null) {
			return null;
		}
		return scanBand(world, band, schematic.getWidth(),
			village.centre().x(), village.centre().z());
	}

	/** Walks each ring in the band from nearest to farthest. */
	private WorkSite.Candidate scanBand(final org.bukkit.World world,
		final int[] band, final int footprint, final double centreX,
		final double centreZ) {

		for (int radius = band[0]; radius <= band[1]; radius += 2) {
			final WorkSite.Candidate found =
				scanRing(world, radius, footprint, centreX, centreZ);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** Walks one ring around the centre, returning the first usable spot. */
	private WorkSite.Candidate scanRing(final org.bukkit.World world,
		final int radius, final int footprint, final double centreX,
		final double centreZ) {

		for (int step = 0; step < 24; step++) {
			final double angle = 2 * Math.PI * step / 24;
			final int x = (int) Math.round(centreX + radius * Math.cos(angle));
			final int z = (int) Math.round(centreZ + radius * Math.sin(angle));
			final int[] heights = sampleHeights(world, x, z, footprint);
			if (heights == null
				|| !WorkSite.levelFits(heights, MAX_LEVEL_SPREAD)) {
				continue;
			}
			return new WorkSite.Candidate(x, WorkSite.medianHeight(heights), z,
				WorkSite.facingRotation(centreX - x, centreZ - z));
		}
		return null;
	}

	/**
	 * How far from the centre each point structure belongs — the watchtower on
	 * the outer edge, the bell tower among the houses. Tiers built along the
	 * ring rather than around it have no band and site themselves.
	 */
	private static int[] searchBand(final VillageWork work) {
		return switch (work) {
			case WATCHTOWER -> new int[] {36, 46};
			case BELL_TOWER -> new int[] {4, 12};
			default -> null;
		};
	}

	/**
	 * Surface heights across the footprint, or null if any column is
	 * unusable — no resolvable ground, or ground that is not natural
	 * terrain. The naturalness check is what keeps a site from straddling a
	 * village house or a player's build.
	 */
	private int[] sampleHeights(final org.bukkit.World world, final int x,
		final int z, final int footprint) {

		final int[] heights = new int[footprint * footprint];
		int index = 0;
		for (int dx = 0; dx < footprint; dx++) {
			for (int dz = 0; dz < footprint; dz++) {
				final Location ground = com.adeptum.questai.utility.SpawnGround
					.findSurface(world, x + dx, z + dz);
				if (ground == null || !com.adeptum.questai.utility.NaturalTerrain
					.isSurface(ground.getBlock().getType())) {

					return null;
				}
				heights[index++] = ground.getBlockY();
			}
		}
		return heights;
	}

	private void tick() {
		final long now = System.currentTimeMillis();
		worksStore.all().forEach((rowId, state) -> {
			if (state.getSite() != null
				&& now - state.getStageAt() >= stageIntervalMillis) {

				advance(rowId, state, now);
			}
		});
	}

	private void advance(final String rowId, final WorkState state,
		final long now) {

		final VillageWork work = VillageWork.byTier(state.getTier());
		final WorkSchematic schematic = schematicFor(work);
		final Location site = siteLocation(state);
		if (schematic == null || site == null || !witnessed(site)) {
			return;
		}
		final BuildStage stage = BuildStage.values()[state.getStage()];
		place(site, state.getRotation(), stage, schematic);
		worksStore.advanceStage(rowId, now);

		if (stage == BuildStage.DETAIL) {
			finish(rowId, state, site, work, schematic);
		}
	}

	private static WorkSchematic schematicFor(final VillageWork work) {
		return work == null ? null : SCHEMATICS.get(work);
	}

	private static Location siteLocation(final WorkState state) {
		return state.getSite() == null ? null : state.getSite().toLocation();
	}

	/** Construction only proceeds where somebody can see it happen. */
	private boolean witnessed(final Location site) {
		if (!site.getWorld().isChunkLoaded(site.getBlockX() >> 4,
			site.getBlockZ() >> 4)) {

			return false;
		}
		return site.getWorld().getPlayers().stream()
			.anyMatch(player -> player.getLocation()
				.distanceSquared(site) <= witnessRadius * witnessRadius);
	}

	private void place(final Location site, final int rotation,
		final BuildStage stage, final WorkSchematic schematic) {

		final BiomePalette palette = BiomePalette.forBiome(site.getBlock()
			.getBiome());
		final List<SchematicEntry> entries =
			new java.util.ArrayList<>(schematic.stage(stage));
		entries.replaceAll(entry -> WorkSchematic.rotate(entry, rotation,
			schematic.getWidth(), schematic.getDepth()));
		entries.sort(PlacementOrder.comparator());

		for (final SchematicEntry entry : entries) {
			final org.bukkit.block.Block block = site.getWorld().getBlockAt(
				site.getBlockX() + entry.x(),
				site.getBlockY() + entry.y(),
				site.getBlockZ() + entry.z());
			if (!replaceable(block)) {
				continue;
			}
			final org.bukkit.block.data.BlockData data =
				palette.resolve(entry.role(), entry.state());
			if (data != null) {
				block.setBlockData(data,
					PlacementOrder.needsPhysics(entry.role()));
			}
		}
	}

	/** Never overwrite a player's work; the crater carver's rule. */
	private static boolean replaceable(final org.bukkit.block.Block block) {
		return block.getType().isAir() || !block.getType().isSolid()
			|| com.adeptum.questai.utility.NaturalTerrain
				.isSurface(block.getType());
	}

	private void finish(final String rowId, final WorkState state,
		final Location site, final VillageWork work,
		final WorkSchematic schematic) {

		clearTemporaryWorks(site, state.getRotation(), schematic);
		celebrate(site, schematic);
		if (work == VillageWork.WATCHTOWER) {
			site.getWorld().spawnEntity(site.clone().add(4, 1, 8),
				org.bukkit.entity.EntityType.IRON_GOLEM);
		}

		final List<Player> nearby = site.getWorld().getPlayers().stream()
			.filter(player -> player.getLocation()
				.distanceSquared(site) <= witnessRadius * witnessRadius)
			.toList();

		for (final Player player : nearby) {
			reward(player, work);
		}
		recordMemory(site, nearby, work);
		worksStore.completeTier(rowId);
	}

	/** The payoff moment: the village audibly enjoys its new structure. */
	private static void celebrate(final Location site,
		final WorkSchematic schematic) {

		final Location top = site.clone().add(schematic.getWidth() / 2.0,
			6, schematic.getDepth() / 2.0);
		site.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
			top, 80, 3.0, 3.0, 3.0);
		site.getWorld().playSound(top,
			org.bukkit.Sound.ENTITY_VILLAGER_CELEBRATE, 1.0f, 1.0f);
	}

	private void reward(final Player player, final VillageWork work) {
		com.gmail.nossr50.api.ExperienceAPI.addXP(player,
			WorkRewards.pickSkill(ThreadLocalRandom.current()),
			WorkRewards.xpFor(work), "COMMAND");
		player.sendMessage("§6The village raises " + work.getDisplayName()
			+ ", and remembers who paid for it.");

		final com.adeptum.questai.mob.MobDrops.GearRoll roll =
			WorkRewards.rollGear(ThreadLocalRandom.current(), work);
		if (roll == null) {
			return;
		}
		player.getInventory().addItem(mobForge.buildGear(roll)).values()
			.forEach(rest -> player.getWorld()
				.dropItemNaturally(player.getLocation(), rest));
	}

	/** Everyone present is remembered by everyone who lives there. */
	private void recordMemory(final Location site, final List<Player> nearby,
		final VillageWork work) {

		site.getWorld().getNearbyEntities(site, witnessRadius, 16, witnessRadius)
			.stream()
			.filter(entity -> entity instanceof org.bukkit.entity.Villager)
			.map(entity -> entity.getUniqueId())
			.filter(profileStore::hasProfile)
			.forEach(villagerId -> nearby.forEach(player ->
				profileStore.recordEvent(villagerId, player.getUniqueId(),
					MemoryEvent.Type.RAISED_WORKS, work.getMemoryTitle())));
	}

	/** The staging, stakes and materials dump come down at the finish. */
	private void clearTemporaryWorks(final Location site, final int rotation,
		final WorkSchematic schematic) {

		for (final BuildStage stage : BuildStage.values()) {
			for (final SchematicEntry raw : schematic.stage(stage)) {
				if (raw.role() != PaletteRole.SCAFFOLDING
					&& raw.stage() != BuildStage.SURVEY) {
					continue;
				}
				final SchematicEntry entry = WorkSchematic.rotate(raw, rotation,
					schematic.getWidth(), schematic.getDepth());
				site.getWorld().getBlockAt(
					site.getBlockX() + entry.x(),
					site.getBlockY() + entry.y(),
					site.getBlockZ() + entry.z())
					.setType(org.bukkit.Material.AIR, false);
			}
		}
	}
}
