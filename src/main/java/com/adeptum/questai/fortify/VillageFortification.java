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
import java.util.logging.Level;
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
	/** How far out to look for the villagers that measure a village. */
	private static final double CROWD_RADIUS = 96.0;
	private static final double CROWD_HEIGHT = 32.0;
	private static final Map<VillageWork, WorkSchematic> SCHEMATICS =
		loadSchematics();
	private static final WorkSchematic PIECE_A =
		WorkSchematic.load("structures/palisade-module-a.txt");
	private static final WorkSchematic PIECE_B =
		WorkSchematic.load("structures/palisade-module-b.txt");
	private static final WorkSchematic PIECE_CORNER =
		WorkSchematic.load("structures/palisade-corner.txt");
	private static final WorkSchematic PIECE_END =
		WorkSchematic.load("structures/palisade-end.txt");
	private static final WorkSchematic PIECE_SEAM =
		WorkSchematic.load("structures/palisade-seam.txt");
	private static final int MODULES_PER_PULSE = 2;
	// Tried in this order from the recorded gate index: the line itself,
	// then the slots bracketing it outward, so a puddle at the exact gate
	// line does not block the gate forever
	private static final int[] GATE_SLOT_OFFSETS =
		{0, 1, -1, 2, -2, 3, -3, 4, -4};

	private final JavaPlugin plugin;
	private final VillageRegistry registry;
	private final VillageWorksStore worksStore;
	private final VillagerProfileStore profileStore;
	private final MobForge mobForge;
	private final boolean enabled;
	private final long stageIntervalMillis;
	private final long pulseIntervalMillis;
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
		this.pulseIntervalMillis = plugin.getConfig()
			.getLong("villages.fortify.pulse-interval-seconds", 60L) * 1000L;
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
		if (!enabled) {
			return null;
		}
		final NamedVillage village = registry.find(location);
		return village == null ? null : VillageRegistry.rowIdFor(village);
	}

	/** True inside a village whose ladder has raised its bell tower. */
	public boolean bellBoostApplies(final Location location) {
		final String rowId = rowIdAt(location);
		final WorkState state = rowId == null ? null : worksStore.get(rowId);
		return state != null && state.getBuiltSites()
			.containsKey(VillageWork.BELL_TOWER.ordinal());
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
		if (cannotDonate(rowId)) {
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
			if (readyToRetryBuild(work, state)) {
				startBuildIfFunded(player, rowId, work);
				return;
			}
			player.sendMessage("§7They have all they need from you for now.");
			return;
		}
		player.sendMessage("§aYou hand over " + given
			+ " toward " + work.getDisplayName() + ".");
		startBuildIfFunded(player, rowId, work);
	}

	/** True when the feature is off, or the click named no real works row. */
	private boolean cannotDonate(final String rowId) {
		return !enabled || rowId == null;
	}

	/**
	 * Whether a click that handed over nothing new should still retry site
	 * selection: the tier is fully funded but no site was ever secured, so
	 * every later donation would otherwise dead-end on the same message.
	 */
	private static boolean readyToRetryBuild(final VillageWork work,
		final WorkState state) {

		return state != null && state.getSite() == null && isFunded(work, state);
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
		if (!isFunded(work, state)) {
			return;
		}

		final NamedVillage village = resolveVillage(player, rowId);
		if (work == VillageWork.PALISADE) {
			startPalisade(player, rowId, work, village);
			return;
		}
		if (work == VillageWork.GATE) {
			startGate(player, rowId, work, state);
			return;
		}
		if (village == null) {
			player.sendMessage("§7They have what they need, but nobody can "
				+ "say where the village ends.");
			return;
		}
		startPointStructure(player, rowId, work, village);
	}

	/**
	 * Hunts the band around the village for ground the crew can level, and
	 * when there is none, hands the donor the search's own account of why.
	 */
	private void startPointStructure(final Player player, final String rowId,
		final VillageWork work, final NamedVillage village) {

		final SiteSearch search = new SiteSearch(player.getWorld());
		final WorkSite.Candidate site =
			findSite(search, measureExtent(village), work);
		if (site == null) {
			player.sendMessage(search.getReport().describe());
			return;
		}
		beginPointStructure(player, rowId, work, site, null);
	}

	/**
	 * The village at the donor's feet, or by row id when they have stepped
	 * past the claim radius while a still-open works row names the village.
	 */
	private NamedVillage resolveVillage(final Player player, final String rowId) {
		final NamedVillage found = registry.find(player.getLocation());
		return found != null ? found : registry.byRowId(rowId);
	}

	/** Whether the village has been handed everything this tier asks for. */
	private static boolean isFunded(final VillageWork work, final WorkState state) {
		return work.getRequirements().entrySet().stream()
			.allMatch(need -> state.getTally()
				.getOrDefault(need.getKey(), 0) >= need.getValue());
	}

	/**
	 * The gate rides the palisade's ring line. When the recorded slot's
	 * ground cannot be built on, the nearest slots either side are tried
	 * before giving up, so a puddle at the exact line cannot block it.
	 */
	private void startGate(final Player player, final String rowId,
		final VillageWork work, final WorkState state) {

		final org.bukkit.World world = player.getWorld();
		final PalisadeRing.RingModule slot = gateRingSlot(world, state);
		final WorkSite.Candidate site =
			slot == null ? null : gateSite(world, slot);
		if (site == null) {
			player.sendMessage("§7They have what they need, but the wall gives "
				+ "them nowhere to hang a gate.");
			return;
		}
		beginPointStructure(player, rowId, work, site, slot);
	}

	/**
	 * Records the chosen site, opens the wall for the gate when it is one,
	 * clears and levels the pad, then breaks ground. {@code gateSlot} is the
	 * ring slot the gate cuts into, already chosen alongside the site so the
	 * two never disagree.
	 */
	private void beginPointStructure(final Player player, final String rowId,
		final VillageWork work, final WorkSite.Candidate site,
		final PalisadeRing.RingModule gateSlot) {

		final StoredLocation origin = new StoredLocation(
			player.getWorld().getUID(), site.x(), site.baseY(), site.z());
		worksStore.beginBuild(rowId, origin, site.rotation());
		player.sendMessage("§6Work begins on " + work.getDisplayName() + ".");
		if (gateSlot != null) {
			demolishRingRun(player.getWorld(), gateSlot, site.baseY());
		}
		clearAndLevel(player.getWorld(), origin, SCHEMATICS.get(work),
			site.rotation());
		// The survey stakes go in while the donor is still standing there
		advance(rowId, worksStore.get(rowId), System.currentTimeMillis());
	}

	/**
	 * Trees down, hollows filled: the ground work a crew does before a
	 * single stake goes in. The footprint is taken as the plan lies on the
	 * ground, so a quarter-turned plan clears the width it truly covers.
	 */
	private static void clearAndLevel(final org.bukkit.World world,
		final StoredLocation origin, final WorkSchematic schematic,
		final int rotation) {

		if (schematic == null) {
			return;
		}
		final boolean turned = rotation % 2 != 0;
		new SiteGround(world).prepare(origin.toLocation(),
			turned ? schematic.getDepth() : schematic.getWidth(),
			turned ? schematic.getWidth() : schematic.getDepth(),
			schematic.getHeight());
	}

	/**
	 * The palisade needs no site search — its ring is a function of the
	 * village centre — so it begins straight away and plants its stakes.
	 * The ring itself stays the fixed square {@link PalisadeRing} draws;
	 * only what it is centred on comes from the villagers.
	 */
	private void startPalisade(final Player player, final String rowId,
		final VillageWork work, final NamedVillage village) {

		if (village == null) {
			player.sendMessage("§7They have what they need, but nobody can "
				+ "say where the village ends.");
			return;
		}
		final VillageExtent.Extent extent = measureExtent(village);
		final StoredLocation centre = new StoredLocation(
			player.getWorld().getUID(), extent.centreX(),
			village.centre().y(), extent.centreZ());
		worksStore.beginBuild(rowId, centre, 0);
		player.sendMessage("§6Work begins on " + work.getDisplayName() + ".");
		placeStakeRing(player.getWorld(), centre);
	}

	/**
	 * Searches the band around the village for a footprint worth levelling.
	 * Returns null rather than forcing a bad site, the same way the raid
	 * spawner skips a spawn it cannot ground.
	 */
	private static WorkSite.Candidate findSite(final SiteSearch search,
		final VillageExtent.Extent extent, final VillageWork work) {

		final WorkSchematic schematic = SCHEMATICS.get(work);
		final int[] band = VillageExtent.band(extent, work);
		if (schematic == null || band == null) {
			return null;
		}
		return search.find(extent, band, schematic.getWidth());
	}

	/**
	 * The village as its people describe it. A stored centre is only where
	 * its finder happened to stand, so everything sited around a village is
	 * measured from the villagers themselves where enough are about.
	 */
	private static VillageExtent.Extent measureExtent(
		final NamedVillage village) {

		final Location centre = village.centre().toLocation();
		final List<VillageExtent.Point> crowd = centre == null
			|| centre.getWorld() == null ? List.of()
			: centre.getWorld()
				.getNearbyEntities(centre, CROWD_RADIUS, CROWD_HEIGHT,
					CROWD_RADIUS)
				.stream()
				.filter(entity -> entity instanceof org.bukkit.entity.Villager)
				.map(entity -> new VillageExtent.Point(
					entity.getLocation().getX(), entity.getLocation().getZ()))
				.toList();
		return VillageExtent.measure(crowd, village.centre().x(),
			village.centre().z());
	}

	/**
	 * The gate stands on the ring line at the given slot, opening toward the
	 * road. Its rotation is the ring slot's plus a half turn, because point
	 * structures are drawn with the outside on row one where ring pieces put
	 * it on their last row.
	 */
	private WorkSite.Candidate gateSite(final org.bukkit.World world,
		final PalisadeRing.RingModule slot) {

		final Location ground = com.adeptum.questai.utility.SpawnGround
			.findSurface(world, slot.x(), slot.z());
		if (ground == null) {
			return null;
		}

		final int rotation = Math.floorMod(slot.rotation() + 2, 4);
		final WorkSchematic gate = SCHEMATICS.get(VillageWork.GATE);
		// The passage centre, grid (5, 3), lands on the ring at the slot
		final SchematicEntry anchor = WorkSchematic.rotate(
			new SchematicEntry(5, 0, 3, PaletteRole.AIR, null,
				BuildStage.SHELL),
			rotation, gate.getWidth(), gate.getDepth());
		return new WorkSite.Candidate(slot.x() + 2 - anchor.x(),
			ground.getBlockY(), slot.z() - anchor.z(), rotation);
	}

	/**
	 * The ring slot the palisade recorded as the gate opening, or null when
	 * no palisade stands yet. Tries that slot first, then the slots
	 * bracketing it outward, keeping the first one whose ground resolves.
	 */
	private PalisadeRing.RingModule gateRingSlot(final org.bukkit.World world,
		final WorkState state) {

		final WorkState.BuiltSite palisade = state == null ? null
			: state.getBuiltSites().get(VillageWork.PALISADE.ordinal());
		if (palisade == null) {
			return null;
		}
		final List<PalisadeRing.RingModule> ring = PalisadeRing.ring(
			(int) palisade.origin().x(), (int) palisade.origin().z());
		final int gateIndex = PalisadeRing.gateIndex(ring);
		for (final int offset : GATE_SLOT_OFFSETS) {
			final PalisadeRing.RingModule slot =
				ring.get(Math.floorMod(gateIndex + offset, ring.size()));
			if (com.adeptum.questai.utility.SpawnGround
				.findSurface(world, slot.x(), slot.z()) != null) {
				return slot;
			}
		}
		return null;
	}

	private void tick() {
		final long now = System.currentTimeMillis();
		worksStore.all().forEach((rowId, state) -> tickRow(rowId, state, now));
	}

	/** One bad row must not stop every other village's works from ticking. */
	private void tickRow(final String rowId, final WorkState state,
		final long now) {

		try {
			tickOne(rowId, state, now);
		} catch (final RuntimeException e) {
			plugin.getLogger().log(Level.WARNING,
				"[VillageFortification] Skipping works row " + rowId, e);
		}
	}

	/** Palisade rows pulse on their own clock; everything else stages. */
	private void tickOne(final String rowId, final WorkState state,
		final long now) {

		if (state.getSite() == null) {
			return;
		}
		if (VillageWork.byTier(state.getTier()) == VillageWork.PALISADE) {
			if (now - state.getStageAt() >= pulseIntervalMillis) {
				pulse(rowId, state, now);
			}
			return;
		}
		if (now - state.getStageAt() >= stageIntervalMillis) {
			advance(rowId, state, now);
		}
	}

	private void advance(final String rowId, final WorkState state,
		final long now) {

		final VillageWork work = VillageWork.byTier(state.getTier());
		final WorkSchematic schematic = schematicFor(work);
		final Location site = siteLocation(state);
		if (schematic == null || site == null || !witnessed(site)) {
			return;
		}
		if (state.getStage() >= BuildStage.values().length) {
			// A row persisted past the last stage, most likely a crash
			// between placing it and recording the tier as complete
			worksStore.completeTier(rowId);
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
		if (work == VillageWork.BELL_TOWER) {
			for (int ring = 1; ring <= 3; ring++) {
				Bukkit.getScheduler().runTaskLater(plugin,
					() -> site.getWorld().playSound(site,
						org.bukkit.Sound.BLOCK_BELL_USE, 1.0f, 1.0f),
					20L * ring);
			}
		}
		if (work == VillageWork.WATCHTOWER) {
			site.getWorld().spawnEntity(site.clone().add(4, 1, 8),
				org.bukkit.entity.EntityType.IRON_GOLEM);
		}
		payOff(rowId, work, site);
	}

	/** Rewards the witnesses, seeds the memory, moves the ladder up. */
	private void payOff(final String rowId, final VillageWork work,
		final Location focus) {

		final List<Player> nearby = focus.getWorld().getPlayers().stream()
			.filter(player -> player.getLocation()
				.distanceSquared(focus) <= witnessRadius * witnessRadius)
			.toList();
		for (final Player player : nearby) {
			reward(player, work);
		}
		recordMemory(focus, nearby, work);
		worksStore.completeTier(rowId);
	}

	/**
	 * Advances both ring fronts by up to {@link #MODULES_PER_PULSE} slots
	 * each, then checks whether the loop has closed.
	 */
	private void pulse(final String rowId, final WorkState state,
		final long now) {

		final Location centre = state.getSite().toLocation();
		if (centre == null || !witnessed(centre)) {
			return;
		}
		final List<PalisadeRing.RingModule> ring = PalisadeRing.ring(
			centre.getBlockX(), centre.getBlockZ());
		final PulseContext context = new PulseContext(rowId, state, centre,
			ring, PalisadeRing.gateIndex(ring), ring.size());
		final Fronts fronts =
			new Fronts(state.getFrontForward(), state.getFrontBackward());

		for (int step = 0; step < MODULES_PER_PULSE; step++) {
			advanceFront(context, fronts, true);
			advanceFront(context, fronts, false);
		}
		worksStore.advanceFronts(rowId, fronts.forward, fronts.backward, now);

		if (PalisadeRing.complete(fronts.forward, fronts.backward,
			context.size())) {

			sweepStakes(centre.getWorld(), ring);
			celebrateSeam(centre, ring, context.gate(), context.size());
			payOff(rowId, VillageWork.PALISADE, centre);
		}
	}

	/** The fixed facts of one pulse, bundled so the helpers stay narrow. */
	private record PulseContext(String rowId, WorkState state,
		Location centre, List<PalisadeRing.RingModule> ring, int gate,
		int size) {
	}

	/** How far each ring front has got, mutated in place across one pulse. */
	private static final class Fronts {
		private int forward;
		private int backward;

		Fronts(final int forward, final int backward) {
			this.forward = forward;
			this.backward = backward;
		}
	}

	/**
	 * Advances one front by one slot, when the ring is not already closed
	 * and the slot could be built. A front that must wait for a chunk to
	 * load, or that only recorded a gap, does not move — it retries the
	 * same slot next pulse.
	 */
	private void advanceFront(final PulseContext context, final Fronts fronts,
		final boolean forwards) {

		if (PalisadeRing.complete(fronts.forward, fronts.backward,
			context.size())) {
			return;
		}
		final int index = forwards
			? PalisadeRing.nextForward(context.gate(), fronts.forward,
				context.size())
			: PalisadeRing.nextBackward(context.gate(), fronts.backward,
				context.size());
		final boolean lastSlot =
			fronts.forward + fronts.backward == context.size() - 1;
		if (!buildSlot(context.rowId(), context.state(), context.centre(),
			context.ring(), index, lastSlot, forwards)) {
			return;
		}
		if (forwards) {
			fronts.forward++;
		} else {
			fronts.backward++;
		}
	}

	/** The topping-out moment happens at the seam, on the far side. */
	private static void celebrateSeam(final Location centre,
		final List<PalisadeRing.RingModule> ring, final int gate,
		final int size) {

		final PalisadeRing.RingModule seam =
			ring.get(Math.floorMod(gate + size / 2, size));
		final Location top = new Location(centre.getWorld(),
			seam.x(), centre.getBlockY() + 4, seam.z());
		centre.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
			top, 40, 2.0, 2.0, 2.0);
		centre.getWorld().playSound(top,
			org.bukkit.Sound.ENTITY_VILLAGER_CELEBRATE, 1.0f, 1.0f);
	}

	/**
	 * Builds one ring slot. Returns false only when the slot's chunk is not
	 * loaded — that front simply waits for a wanderer to load it. Ground the
	 * wall cannot stand on becomes a recorded gap the front steps past.
	 */
	private boolean buildSlot(final String rowId, final WorkState state,
		final Location centre, final List<PalisadeRing.RingModule> ring,
		final int index, final boolean lastSlot, final boolean forwards) {

		final PalisadeRing.RingModule slot = ring.get(index);
		final org.bukkit.World world = centre.getWorld();
		if (!world.isChunkLoaded(slot.x() >> 4, slot.z() >> 4)) {
			return false;
		}

		final Integer baseY = slotBase(world, slot);
		if (baseY == null) {
			worksStore.recordGap(rowId, index);
			return true;
		}
		clearStake(world, slot, baseY);
		placePiece(world, pieceFor(slot.kind(), lastSlot), slot, baseY);
		closeGapBehind(state, world, ring, index, forwards);
		return true;
	}

	/** The levelled base for a slot, or null when the ground refuses it. */
	private static Integer slotBase(final org.bukkit.World world,
		final PalisadeRing.RingModule slot) {

		final int length = slot.kind() == PalisadeRing.Kind.MODULE_A
			|| slot.kind() == PalisadeRing.Kind.MODULE_B
			? PalisadeRing.MODULE_LENGTH : 1;
		final int[] heights = new int[length];
		for (int i = 0; i < length; i++) {
			final Integer height = groundHeight(world, slot, i);
			if (height == null) {
				return null;
			}
			heights[i] = height;
		}
		return WorkSite.levelFits(heights, 1)
			? WorkSite.medianHeight(heights) : null;
	}

	/** The surface height one step along the slot's run, or null if unusable. */
	private static Integer groundHeight(final org.bukkit.World world,
		final PalisadeRing.RingModule slot, final int offset) {

		final Location ground = com.adeptum.questai.utility.SpawnGround
			.findSurface(world, stepX(slot, offset), stepZ(slot, offset));
		return ground == null ? null : ground.getBlockY();
	}

	/** Advances the x coordinate along the slot's run direction. */
	private static int stepX(final PalisadeRing.RingModule slot,
		final int offset) {

		return switch (slot.rotation()) {
			case 0 -> slot.x() + offset;
			case 2 -> slot.x() - offset;
			default -> slot.x();
		};
	}

	/** Advances the z coordinate along the slot's run direction. */
	private static int stepZ(final PalisadeRing.RingModule slot,
		final int offset) {

		return switch (slot.rotation()) {
			case 1 -> slot.z() + offset;
			case 3 -> slot.z() - offset;
			default -> slot.z();
		};
	}

	/** The filler columns reuse the corner post — a lone braced post reads
	 * as deliberate punctuation against the corners, right where fillers
	 * stand. */
	private static WorkSchematic pieceFor(final PalisadeRing.Kind kind,
		final boolean lastSlot) {

		if (lastSlot) {
			return PIECE_SEAM;
		}
		return switch (kind) {
			case MODULE_A -> PIECE_A;
			case MODULE_B -> PIECE_B;
			case CORNER -> PIECE_CORNER;
			case FILLER -> PIECE_CORNER;
		};
	}

	/** A built slot bordering a recorded gap closes it with an end post. */
	private void closeGapBehind(final WorkState state,
		final org.bukkit.World world,
		final List<PalisadeRing.RingModule> ring, final int index,
		final boolean forwards) {

		final int behind =
			Math.floorMod(forwards ? index - 1 : index + 1, ring.size());
		if (!state.getRingGaps().contains(behind)) {
			return;
		}
		final PalisadeRing.RingModule gapSlot = ring.get(behind);
		final Integer baseY = slotBase(world, ring.get(index));
		if (baseY != null) {
			placePiece(world, PIECE_END, gapSlot, baseY);
		}
	}

	/** Anchors a piece by its first wall cell — local {@code (0, z=1)}. */
	private void placePiece(final org.bukkit.World world,
		final WorkSchematic piece, final PalisadeRing.RingModule slot,
		final int baseY) {

		final BiomePalette palette = BiomePalette.forBiome(
			world.getBlockAt(slot.x(), baseY, slot.z()).getBiome());
		final SchematicEntry anchor = WorkSchematic.rotate(
			new SchematicEntry(0, 0, 1, PaletteRole.AIR, null,
				BuildStage.SHELL),
			slot.rotation(), piece.getWidth(), piece.getDepth());

		final List<SchematicEntry> entries = new java.util.ArrayList<>();
		for (final BuildStage stage : BuildStage.values()) {
			entries.addAll(piece.stage(stage));
		}
		entries.replaceAll(entry -> WorkSchematic.rotate(entry,
			slot.rotation(), piece.getWidth(), piece.getDepth()));
		entries.sort(PlacementOrder.comparator());

		for (final SchematicEntry entry : entries) {
			final org.bukkit.block.Block block = world.getBlockAt(
				slot.x() + entry.x() - anchor.x(),
				baseY + entry.y(),
				slot.z() + entry.z() - anchor.z());
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

	/** Opening the wall is loud and deliberate, not a silent swap. */
	private void demolishRingRun(final org.bukkit.World world,
		final PalisadeRing.RingModule slot, final int baseY) {

		final int[] along = slot.rotation() % 2 == 0
			? new int[] {1, 0} : new int[] {0, 1};
		clearRingCells(world, slot, along, baseY);
		closeRingCut(world, slot, along);
	}

	/** Breaks every palisade block standing across the gate's 9-wide run. */
	private void clearRingCells(final org.bukkit.World world,
		final PalisadeRing.RingModule slot, final int[] along,
		final int baseY) {

		for (int offset = -4; offset <= 4; offset++) {
			final int x = slot.x() + (2 + offset) * along[0];
			final int z = slot.z() + (2 + offset) * along[1];
			clearRingColumn(world, x, z, baseY);
		}
	}

	/** Breaks the palisade blocks standing in one column of the cut. */
	private static void clearRingColumn(final org.bukkit.World world,
		final int x, final int z, final int baseY) {

		for (int y = baseY - 1; y <= baseY + 5; y++) {
			final org.bukkit.block.Block block = world.getBlockAt(x, y, z);
			if (block.getType().isAir() || com.adeptum.questai.utility
				.NaturalTerrain.isSurface(block.getType())) {
				continue;
			}
			world.spawnParticle(org.bukkit.Particle.BLOCK,
				block.getLocation().add(0.5, 0.5, 0.5), 12,
				block.getBlockData());
			block.setType(org.bukkit.Material.AIR, false);
		}
	}

	/** Closes the cut with an end post one cell beyond each side of the run. */
	private void closeRingCut(final org.bukkit.World world,
		final PalisadeRing.RingModule slot, final int[] along) {

		for (final int side : new int[] {-5, 5}) {
			final PalisadeRing.RingModule post = new PalisadeRing.RingModule(
				slot.x() + (2 + side) * along[0],
				slot.z() + (2 + side) * along[1],
				slot.rotation(), PalisadeRing.Kind.CORNER);
			final Integer postBase = slotBase(world, post);
			if (postBase != null) {
				placePiece(world, PIECE_END, post, postBase);
			}
		}
	}

	/** The whole plan visible at night, the moment the tier is funded. */
	private void placeStakeRing(final org.bukkit.World world,
		final StoredLocation centre) {

		final List<PalisadeRing.RingModule> ring = PalisadeRing.ring(
			(int) centre.x(), (int) centre.z());
		for (int i = 0; i < ring.size(); i += 2) {
			final PalisadeRing.RingModule slot = ring.get(i);
			if (!world.isChunkLoaded(slot.x() >> 4, slot.z() >> 4)) {
				continue;
			}
			final Location ground = com.adeptum.questai.utility.SpawnGround
				.findSurface(world, slot.x(), slot.z());
			if (ground == null) {
				continue;
			}
			world.getBlockAt(slot.x(), ground.getBlockY() + 1, slot.z())
				.setType(org.bukkit.Material.OAK_FENCE, false);
			if (i % 4 == 0) {
				world.getBlockAt(slot.x(), ground.getBlockY() + 2, slot.z())
					.setType(org.bukkit.Material.LANTERN, false);
			}
		}
	}

	/** Stakes come down as the wall goes up, or at the latest at the end. */
	private static void clearStake(final org.bukkit.World world,
		final PalisadeRing.RingModule slot, final int baseY) {

		for (int y = baseY; y <= baseY + 3; y++) {
			final org.bukkit.block.Block block =
				world.getBlockAt(slot.x(), y, slot.z());
			if (org.bukkit.Tag.FENCES.isTagged(block.getType())
				|| block.getType() == org.bukkit.Material.LANTERN) {
				block.setType(org.bukkit.Material.AIR, false);
			}
		}
	}

	/** The last stakes standing come down once the loop closes. */
	private static void sweepStakes(final org.bukkit.World world,
		final List<PalisadeRing.RingModule> ring) {

		for (final PalisadeRing.RingModule slot : ring) {
			if (!world.isChunkLoaded(slot.x() >> 4, slot.z() >> 4)) {
				continue;
			}
			final Location ground = com.adeptum.questai.utility.SpawnGround
				.findSurface(world, slot.x(), slot.z());
			if (ground != null) {
				clearStake(world, slot, ground.getBlockY());
			}
		}
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
