package dev.skycraft.loot;

import dev.skycraft.SkyCraft;
import dev.skycraft.config.Config;
import dev.skycraft.config.Features;
import dev.skycraft.link.SkyLink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gear drops: a Skyrim actor the player kills sometimes drops the Minecraft version of what it wore, the
 * way a Minecraft mob drops its equipment (what becomes what and the odds: {@link GearMap}).
 *
 * <p>When an actor in the actor table dies, Skyrim sends each piece it wore as a worn ItemEvent
 * (Proto.SRC_WORN, container = the actor); {@link SkyBridge} hands those here and never to the player.
 * They can come before or after {@link SkyLoot} credits the player with the kill, so each waits
 * {@link #WAIT_TICKS} for the other; worn gear of a death nobody credits is forgotten. Server thread only.
 */
public final class GearDrops {
	/** 10 seconds, a kill's hit window: how long worn gear waits for its kill, and a kill for the rest of its gear. */
	static final int WAIT_TICKS = SkyLoot.HIT_WINDOW_TICKS;
	private static final String FILE = "skycraft-gear-drops.txt";

	/** Worn gear by actor form id, waiting for the kill. */
	private static final Map<Integer, List<Worn>> WORN = new HashMap<>();
	/** Credited kills by actor form id, for gear still on its way. */
	private static final Map<Integer, Kill> KILLS = new HashMap<>();
	private static final Random RANDOM = new Random();
	private static GearMap.Settings settings;

	private record Worn(SkyLink.Item item, int tick) {
	}

	private record Kill(String name, double x, double y, double z, int looting, int tick) {
	}

	private GearDrops() {
	}

	/** A piece of gear a Skyrim actor wore when it died (a worn ItemEvent). */
	public static void onWorn(ServerLevel level, SkyLink.Item it, int tick) {
		if (!Features.on(Features.Feature.GEAR_DROPS)) {
			return;
		}
		expire(tick);
		Kill kill = KILLS.get(it.container());
		if (kill != null) {
			roll(level, kill, it);
			return;
		}
		WORN.computeIfAbsent(it.container(), id -> new ArrayList<>()).add(new Worn(it, tick));
	}

	/** SkyLoot credited {@code killer} (SkyLoot.killer) with killing this actor. */
	public static void onKill(ServerLevel level, SkyLink.Actor a, @Nullable ServerPlayer killer, int tick) {
		if (!Features.on(Features.Feature.GEAR_DROPS)) {
			return;
		}
		expire(tick);
		// Looting on the killer's weapon, as for Minecraft's equipment drops (only a player's counts there too).
		int looting = killer != null ? SkyLoot.lootingLevel(killer) : 0;
		Kill kill = new Kill(a.name(), a.x(), a.y(), a.z(), looting, tick);
		KILLS.put(a.formId(), kill);
		List<Worn> worn = WORN.remove(a.formId());
		if (worn != null) {
			for (Worn w : worn) {
				roll(level, kill, w.item());
			}
		}
	}

	/**
	 * Forgets what waited too long (or is from before the server started: its ticks begin at 0 again).
	 * SkyLoot calls it every second, so gear and kills that never meet don't stay forever.
	 */
	public static void expire(int tick) {
		KILLS.values().removeIf(k -> tick - k.tick() > WAIT_TICKS || tick < k.tick());
		WORN.values().removeIf(list -> {
			list.removeIf(w -> tick - w.tick() > WAIT_TICKS || tick < w.tick());
			return list.isEmpty();
		});
	}

	/** Rolls one worn piece of a credited kill and drops its Minecraft gear at the corpse. */
	private static void roll(ServerLevel level, Kill kill, SkyLink.Item it) {
		GearMap.Gear gear = GearMap.of(it.tags(), it.kind(), it.material(), it.name());
		if (gear == null) {
			return;
		}
		Identifier key = Identifier.tryParse(gear.item());
		Item item = key != null ? BuiltInRegistries.ITEM.getValue(key) : null;
		if (item == null || item == Items.AIR) {
			SkyCraft.LOG.warn("SkyCraft-loot: unknown gear item '{}' for {}", gear.item(), it.name());
			return;
		}
		GearMap.Settings s = settings();
		int had = Math.abs(it.count());
		if (gear.ammo()) {
			int n = GearMap.arrows(RANDOM, s.arrows(), kill.looting(), had);
			if (n > 0) {
				drop(level, kill, new ItemStack(item, n));
			}
			SkyCraft.LOG.info("SkyCraft-loot: {}'s {} x {}: {} {}", kill.name(), had, it.name(), n, gear.item());
			return;
		}
		float chance = GearMap.chance(s.chance(), s.looting(), kill.looting());
		// Each hand rolls on its own, like each of a mob's equipment slots (the same weapon in both hands is 2).
		for (int i = 0; i < Math.min(had, 2); i++) {
			boolean drops = RANDOM.nextFloat() < chance;
			if (drops) {
				ItemStack stack = new ItemStack(item);
				if (stack.isDamageableItem()) {
					stack.setDamageValue(GearMap.damage(stack.getMaxDamage(), RANDOM));
				}
				drop(level, kill, stack);
			}
			SkyCraft.LOG.info("SkyCraft-loot: {}'s {}: {} ({}% chance{})", kill.name(), it.name(), drops ? "dropped " + gear.item() : "no drop",
				Math.round(chance * 1000.0F) / 10.0F, kill.looting() > 0 ? ", Looting " + kill.looting() : "");
		}
	}

	private static void drop(ServerLevel level, Kill kill, ItemStack stack) {
		Vec3 at = SkyLoot.dropPosition(level, kill.x(), kill.y(), kill.z());
		ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, stack);
		entity.setDefaultPickUpDelay();
		level.addFreshEntity(entity);
	}

	private static GearMap.Settings settings() {
		if (settings == null) {
			List<String> problems = new ArrayList<>();
			settings = GearMap.parse(Config.read(FILE, GearMap.DEFAULT_TEXT), problems);
			problems.forEach(p -> SkyCraft.LOG.warn("SkyCraft-loot: {} {}", FILE, p));
			SkyCraft.LOG.info("SkyCraft-loot: gear drops at {} per piece, {} per Looting level, up to {} arrows", settings.chance(), settings.looting(),
				settings.arrows());
		}
		return settings;
	}
}
