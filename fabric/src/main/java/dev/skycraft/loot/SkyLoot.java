package dev.skycraft.loot;

import dev.skycraft.SkyCraft;
import dev.skycraft.SkyDimension;
import dev.skycraft.combat.SkyCombat;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyTri;
import dev.skycraft.world.TriCollider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim creatures the player kills drop Minecraft items at their feet, the way Minecraft's mobs do.
 *
 * <p>Skyrim already reports every nearby actor's name and dead flag each frame. A drop happens
 * when an actor seen alive turns dead within {@link #HIT_WINDOW_TICKS} of the player's last hit on
 * it, so corpses walked up to and kills by others drop nothing. Each actor drops at most once,
 * except creatures SkyCraft spawned itself: Skyrim reuses the form ids of deleted ones, so a
 * spawned creature seen alive is always a new one.
 * What drops comes from {@code config/skycraft-loot.txt} (see {@link LootRules}): a rule's vanilla
 * loot tables are rolled as the death of the creature's stand-in at the hands of its killer, so
 * vanilla's counts, chances, Looting, kill-only drops and cooked meat all apply; its own drops get
 * the same Looting and cooking. Server thread only.
 */
public final class SkyLoot {
	/** 10 seconds: a kill by bleeding, fire or a follow-up arrow still counts as the player's. */
	public static final int HIT_WINDOW_TICKS = 200;
	private static final String FILE = "skycraft-loot.txt";

	private static final Map<Integer, Boolean> WAS_DEAD = new HashMap<>();
	private static final Map<Integer, Integer> LAST_HIT = new HashMap<>();
	/** The last player whose attack reached each actor's stand-in: the kill is theirs. */
	private static final Map<Integer, UUID> HITTER = new HashMap<>();
	private static final Set<Integer> LOOTED = new HashSet<>();
	/** Form ids in the latest actor table; reused every tick. */
	private static final Set<Integer> SEEN = new HashSet<>();
	private static final Random RANDOM = new Random();
	private static LootRules rules;

	private SkyLoot() {
	}

	/** The player landed a hit on this actor on this server tick. */
	public static void recordHit(int formId, int tick) {
		LAST_HIT.put(formId, tick);
		SkyrimActorEntity proxy = SkyCombat.proxy(formId);
		UUID by = proxy != null ? proxy.lastPlayerHit() : null;
		if (by != null) {
			HITTER.put(formId, by);
		}
	}

	public static void reset() {
		WAS_DEAD.clear();
		LAST_HIT.clear();
		HITTER.clear();
		// Skyrim reuses form ids across loads and respawns: an actor looted before can be a new one now.
		LOOTED.clear();
		SEEN.clear();
	}

	/** Called with each fresh read of Skyrim's actor table. */
	public static void onActors(ServerLevel level, List<SkyLink.Actor> actors, int tick) {
		SEEN.clear();
		for (SkyLink.Actor a : actors) {
			Integer id = a.formId();
			SEEN.add(id);
			Boolean before = WAS_DEAD.put(id, a.dead());
			if (!a.dead() || !Boolean.FALSE.equals(before) || LOOTED.contains(id)) {
				continue;
			}
			Integer hit = LAST_HIT.get(id);
			if (hit == null || tick - hit > HIT_WINDOW_TICKS) {
				SkyCraft.LOG.info("SkyCraft-loot: {} died without a recent hit from you, no drop", a.name());
				continue;
			}
			if ((a.flags() & Proto.ACTOR_SPAWNED) == 0) {
				LOOTED.add(id);
			}
			// A credited kill. Everything it pays out goes to the same killer, and the dead creature's
			// stand-in is still there for this tick (SkyCombat.sync discards it after us).
			ServerPlayer killer = killer(level, a);
			if (Features.on(Features.Feature.KILL_XP)) {
				// Straight into the killer's XP bar: orbs dropped at a Skyrim corpse can land under the terrain or
				// out of reach, and SkyCraft doesn't draw them well.
				int xp = KillXp.forLevel(a.level());
				if (killer != null) {
					killer.giveExperiencePoints(xp);
					level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
						net.minecraft.sounds.SoundSource.PLAYERS, 0.2F, 0.6F + level.getRandom().nextFloat() * 0.6F);
				}
				SkyCraft.LOG.info("SkyCraft-loot: killed {} (level {}), {} XP{}", a.name(), a.level(), xp, killer != null ? " to " + killer.getName().getString() : "");
			}
			if (Features.on(Features.Feature.KILL_DROPS)) {
				drop(level, a, killer);
			}
			GearDrops.onKill(level, a, killer, tick);
		}
		// An actor the player just hit is remembered alive while it's briefly out of the table (out
		// of range, Skyrim's table emptied for a frame), so dying there still counts.
		WAS_DEAD.keySet().removeIf(id -> !SEEN.contains(id) && !LAST_HIT.containsKey(id));
		if (tick % 20 == 0) {
			GearDrops.expire(tick);
			for (Iterator<Map.Entry<Integer, Integer>> it = LAST_HIT.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<Integer, Integer> e = it.next();
				if (tick - e.getValue() > HIT_WINDOW_TICKS * 3) {
					HITTER.remove(e.getKey());
					it.remove();
				}
			}
		}
	}

	/**
	 * Who killed this actor: the last player whose attack reached its stand-in (melee, their arrow,
	 * their trident), else the nearest player within 96 blocks of the corpse, else the host; null with
	 * nobody there. For a credited kill in {@link #onActors}, where the dead actor's stand-in still exists.
	 */
	public static @Nullable ServerPlayer killer(ServerLevel level, SkyLink.Actor a) {
		UUID by = HITTER.get(a.formId());
		if (by == null) {
			SkyrimActorEntity proxy = SkyCombat.proxy(a.formId());
			by = proxy != null ? proxy.lastPlayerHit() : null;
		}
		ServerPlayer hitter = by != null ? level.getServer().getPlayerList().getPlayer(by) : null;
		if (hitter != null && hitter.level() == level) {
			return hitter;
		}
		if (level.getNearestPlayer(a.x(), a.y(), a.z(), 96.0, false) instanceof ServerPlayer nearest) {
			return nearest;
		}
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (dev.skycraft.net.SkyNet.isHost(player)) {
				return player;
			}
		}
		return null;
	}

	/** The Looting level of what {@code player} holds, as vanilla's loot functions read it off the killer. */
	public static int lootingLevel(ServerPlayer player) {
		var looting = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
		return EnchantmentHelper.getEnchantmentLevel(looting, player);
	}

	private static void drop(ServerLevel level, SkyLink.Actor a, @Nullable ServerPlayer killer) {
		LootRules.Rule rule = rules().match(a.name());
		if (rule == null) {
			SkyCraft.LOG.info("SkyCraft-loot: killed {}, no drop rule for that name", a.name());
			return;
		}
		// The loot tables read the stand-in as the mob that died: on fire if the kill set it alight.
		SkyrimActorEntity proxy = SkyCombat.proxy(a.formId());
		if (proxy != null && (proxy.isRemoved() || proxy.level() != level)) {
			proxy = null;
		}
		Vec3 corpse = dropPosition(level, a.x(), a.y(), a.z());
		List<ItemStack> stacks = new ArrayList<>();
		for (String table : rule.tables()) {
			stacks.addAll(rollTable(level, table, a, proxy, killer, corpse));
		}
		// Meat comes out cooked from a creature that died burning or to a Fire Aspect weapon, as vanilla's
		// furnace_smelt has it (the tables do this themselves).
		boolean cook = proxy != null && proxy.isOnFire() || killer != null && EnchantmentHelper.hasTag(killer.getMainHandItem(), EnchantmentTags.SMELTS_LOOT);
		int looting = killer != null ? lootingLevel(killer) : 0;
		for (LootRules.Drop d : LootRules.roll(rule, looting, RANDOM)) {
			Identifier key = Identifier.tryParse(cook ? LootRules.cooked(d.item()) : d.item());
			Item item = key != null ? BuiltInRegistries.ITEM.getValue(key) : null;
			if (item == null || item == Items.AIR) {
				SkyCraft.LOG.warn("SkyCraft-loot: unknown item '{}' in {}", d.item(), FILE);
				continue;
			}
			stacks.add(new ItemStack(item, d.min()));
		}
		List<String> dropped = new ArrayList<>();
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			dropped.add(stack.getCount() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
			while (!stack.isEmpty()) {
				// A count past a full stack (64 and Looting on top) drops as several stacks.
				ItemEntity entity = new ItemEntity(level, corpse.x, corpse.y, corpse.z, stack.split(stack.getMaxStackSize()));
				entity.setDefaultPickUpDelay();
				level.addFreshEntity(entity);
			}
		}
		SkyCraft.LOG.info("SkyCraft-loot: killed {}, dropped {}{}", a.name(), dropped.isEmpty() ? "nothing this time" : String.join(", ", dropped),
			looting > 0 ? " (Looting " + looting + ")" : "");
	}

	/**
	 * Rolls a vanilla entity loot table as the death of the creature's stand-in at the hands of
	 * {@code killer}, so vanilla does the counts, chances, Looting, kill-only drops (spider eyes) and
	 * cooked meat. Nothing for an unknown table.
	 */
	private static List<ItemStack> rollTable(ServerLevel level, String table, SkyLink.Actor a, @Nullable SkyrimActorEntity proxy, @Nullable ServerPlayer killer,
		Vec3 corpse) {
		Identifier id = Identifier.tryParse(table);
		LootTable loot = id != null ? level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id)) : LootTable.EMPTY;
		if (loot == LootTable.EMPTY) {
			SkyCraft.LOG.warn("SkyCraft-loot: unknown loot table '{}' in {}", table, FILE);
			return List.of();
		}
		Entity self = proxy;
		if (self == null) {
			// Its stand-in went before the death was seen (it died out of the table): roll as a fresh
			// one at the corpse, never added to the world, so not burning.
			SkyrimActorEntity stand = new SkyrimActorEntity(SkyCombat.SKYRIM_ACTOR, level);
			stand.snapTo(a.x(), a.y(), a.z(), a.yaw(), 0.0F);
			self = stand;
			SkyCraft.LOG.info("SkyCraft-loot: {} has no stand-in left, rolling {} for a fresh one", a.name(), table);
		}
		LootParams.Builder params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, self)
			.withParameter(LootContextParams.ORIGIN, corpse);
		if (killer != null) {
			params.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
				.withParameter(LootContextParams.ATTACKING_ENTITY, killer)
				.withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
				.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
				.withLuck(killer.getLuck());
		} else {
			params.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
		}
		return loot.getRandomItems(params.create(LootContextParamSets.ENTITY));
	}

	/**
	 * Where items dropped at a corpse with its feet at (x, y, z) appear: half a block up, lifted out of
	 * any Minecraft block it would be inside (up to the top of the column, MOTION_BLOCKING) and onto
	 * Skyrim's ground when the corpse sank into it, so they don't fall under the terrain.
	 */
	static Vec3 dropPosition(ServerLevel level, double x, double y, double z) {
		double out = y + 0.5;
		BlockPos.MutableBlockPos pos = BlockPos.containing(x, out, z).mutable();
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
		while (pos.getY() < top && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
			pos.move(Direction.UP);
			out = pos.getY() + 0.1;
		}
		if (SkyDimension.inMirror(level) && SkyCollision.active()) {
			List<SkyTri> tris = new ArrayList<>();
			SkyCollision.trianglesNear(new AABB(x - 0.5, y - 1.0, z - 0.5, x + 0.5, y + 2.0, z + 0.5), tris);
			tris.removeIf(t -> !t.walkable);
			double ground = TriCollider.groundAt(tris, x, y, z, 1.5);
			if (!Double.isNaN(ground) && ground + 0.25 > out) {
				out = ground + 0.25;
			}
		}
		return new Vec3(x, out, z);
	}

	private static LootRules rules() {
		if (rules == null) {
			List<String> problems = new ArrayList<>();
			rules = LootRules.parse(Config.read(FILE, LootRules.DEFAULT_TEXT, LootRules.OLD_DEFAULTS), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-loot: {} {}", FILE, p));
			SkyCraft.LOG.info("SkyCraft-loot: {} drop rules loaded", rules.rules().size());
		}
		return rules;
	}
}
