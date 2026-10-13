package dev.skycraft.loot;

import dev.skycraft.link.Proto;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Gear drops: the Minecraft version of a piece of gear a Skyrim actor wore when the player killed it, and
 * the odds, which are Minecraft 26.3's for a mob's equipment. {@link GearDrops} does the dropping.
 *
 * <p>Weapons and armor become the SkyCraft piece of their material's tier (gauntlets fill the greaves slot,
 * since Minecraft has no glove slot, as the bought gear rules do); materials without a SkyCraft set take the
 * nearest tier. Hide and leather are Minecraft leather armor, armor of no known material chainmail, wooden
 * weapons wooden ones; bows, crossbows, shields, pickaxes and woodcutter's axes are the Minecraft item;
 * arrows and bolts are arrows. Staffs, clothing and jewelry drop nothing. Which piece it is comes from the
 * kind Skyrim gives it (Proto.KIND_*: its weapon type, the slots it covers); only items without one (from an
 * older plugin) are judged by the words of their name, and so are pickaxes and woodcutter's axes, which Skyrim
 * types as war axes. Pure Java on purpose (no Minecraft classes), so it's unit tested without the game.
 */
public final class GearMap {
	/** Minecraft 26.3's chance for each piece of a mob's equipment (DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE). */
	public static final float VANILLA_CHANCE = 0.085F;
	/** Looting's equipment_drops effect: plus 0.01 per level (data/minecraft/enchantment/looting.json). */
	public static final float VANILLA_LOOTING = 0.01F;
	/** A skeleton drops 0 to 2 arrows, plus 0 to 1 per level of Looting (loot_table/entities/skeleton.json). */
	public static final int VANILLA_ARROWS = 2;

	/** {@code item} is a Minecraft item id; {@code ammo} for arrows, which drop by count rather than by chance. */
	public record Gear(String item, boolean ammo) {
	}

	/** Settings from {@code config/skycraft-gear-drops.txt}. */
	public record Settings(float chance, float looting, int arrows) {
	}

	public static final String DEFAULT_TEXT = """
		# SkyCraft gear drops: a Skyrim person or creature you kill sometimes drops the Minecraft version of a
		# weapon or armor piece it wore, as a Minecraft zombie or skeleton drops its gear. The switch is
		# gear_drops in skycraft-features.txt. Restart Minecraft after editing.
		# Chance for each piece (Minecraft's: 0.085, 8.5%), plus this much per level of Looting on your weapon
		# (Minecraft's: 0.01).
		chance = 0.085
		looting = 0.01
		# Arrows and bolts it had become 0 to this many Minecraft arrows, plus 0 to 1 per level of Looting, as
		# a skeleton's do (never more than it had).
		arrows = 2
		""";

	/** Gear tiers SkyCraft registers (GearTiers.TIERS), by Skyrim material; the rest map to the nearest. */
	private static final List<String> MATERIAL_TIER = List.of(
		"", // none: see NONE_*
		"wooden", // wood: Minecraft's wooden weapons
		"iron", "steel",
		"steel", // imperial: Skyrim's Imperial gear is steel
		"orcish", "dwarven", "elven", "glass", "ebony", "daedric",
		"daedric", // dragon (dragonbone, dragonscale, dragonplate): the top tier, like daedric
		"iron", // silver: Minecraft has no silver; it melts to iron everywhere else in SkyCraft
		"leather", // hide, leather, studded, fur: Minecraft leather armor
		"steel", // draugr (Ancient Nord): hits like steel
		"orcish" // falmer: chaurus chitin, about orcish
	);
	/** Weapons of no known material (Forsworn, Nordic, Stalhrim, uniques) with a weapon word in the name. */
	private static final String NONE_WEAPON = "iron";
	/** Armor of no known material (Stormcloak, guards, Forsworn, Thieves Guild, Blades): chainmail. */
	private static final String NONE_ARMOR = "chainmail";

	private static final Set<String> BLADE_WORDS = Set.of("sword", "greatsword", "dagger", "blade", "katana", "scimitar", "claymore", "wakizashi",
		"tanto", "shortsword", "longsword", "saber", "sabre", "rapier", "knife");
	private static final Set<String> AXE_WORDS = Set.of("axe", "battleaxe", "waraxe", "mace", "warhammer", "hammer", "maul", "club");
	private static final Set<String> STAFF_WORDS = Set.of("staff", "stave");
	private static final Set<String> SHIELD_WORDS = Set.of("shield", "targe", "buckler");
	private static final Set<String> HELMET_WORDS = Set.of("helmet", "helm", "hood", "mask", "masque", "headdress", "crown", "hat", "cap", "cowl");
	private static final Set<String> BOOTS_WORDS = Set.of("boots", "shoes", "sabatons", "footwraps");
	private static final Set<String> GLOVES_WORDS = Set.of("gauntlets", "bracers", "gloves", "vambraces", "armguards", "cuffs", "wraps", "handwraps",
		"greaves");

	private GearMap() {
	}

	/**
	 * The Minecraft gear for a Skyrim item with these tags (ItemTags), kind (Proto.KIND_*, 0 when Skyrim didn't say),
	 * material (ItemMaterial) and name, or null when it has none (staffs, clothing, jewelry, anything else).
	 */
	public static Gear of(int tags, int kind, int material, String name) {
		List<String> words = ItemRules.words(ItemRules.fullName(name != null ? name : ""));
		String mat = material >= 0 && material < MATERIAL_TIER.size() ? MATERIAL_TIER.get(material) : "";
		if ((tags & ItemRules.bit("arrow")) != 0) {
			return new Gear("minecraft:arrow", true);
		}
		if ((tags & ItemRules.bit("weapon")) != 0) {
			return weapon(kind, words, mat);
		}
		if ((tags & ItemRules.bit("armor")) != 0 && (tags & (ItemRules.bit("clothing") | ItemRules.bit("jewelry"))) == 0) {
			return armor(kind, words, mat);
		}
		return null;
	}

	private static Gear weapon(int kind, List<String> words, String mat) {
		// Tools first, by name: Skyrim types pickaxes and woodcutter's axes as war axes.
		if (words.contains("pickaxe")) {
			return gear("minecraft:iron_pickaxe");
		}
		if (words.contains("woodcutter's") && words.contains("axe")) {
			return gear("minecraft:iron_axe");
		}
		if (words.contains("fishing") && words.contains("rod")) {
			return gear("minecraft:fishing_rod");
		}
		boolean axe;
		switch (kind) {
			case Proto.KIND_BOW:
				return gear("minecraft:bow");
			case Proto.KIND_CROSSBOW:
				return gear("minecraft:crossbow");
			case Proto.KIND_STAFF, Proto.KIND_HAND_TO_HAND:
				return null;
			case Proto.KIND_SWORD, Proto.KIND_DAGGER, Proto.KIND_GREATSWORD:
				axe = false;
				break;
			case Proto.KIND_WAR_AXE, Proto.KIND_MACE, Proto.KIND_BATTLEAXE:
				axe = true;
				break;
			default:
				// Skyrim didn't say (an older plugin): the words of the name.
				if (words.contains("crossbow")) {
					return gear("minecraft:crossbow");
				}
				if (words.contains("bow")) {
					return gear("minecraft:bow");
				}
				if (any(words, STAFF_WORDS)) {
					return null;
				}
				axe = any(words, AXE_WORDS);
				if (!axe && !any(words, BLADE_WORDS) && (mat.isEmpty() || mat.equals("leather"))) {
					// No weapon word and no metal: a staff by another name (the Wabbajack) or something a spell made.
					return null;
				}
				// Unique weapons without a weapon word (Chillrend, Dawnbreaker) are swords, Skyrim's commonest.
		}
		String tier = mat.isEmpty() || mat.equals("leather") ? NONE_WEAPON : mat;
		if (tier.equals("wooden")) {
			return gear(axe ? "minecraft:wooden_axe" : "minecraft:wooden_sword");
		}
		return gear("skycraft:" + tier + (axe ? "_war_axe" : "_sword"));
	}

	private static Gear armor(int kind, List<String> words, String mat) {
		String piece = switch (kind) {
			case Proto.KIND_SHIELD -> "shield";
			case Proto.KIND_HELMET -> "helmet";
			case Proto.KIND_CUIRASS -> "cuirass";
			case Proto.KIND_GAUNTLETS -> "greaves";
			case Proto.KIND_BOOTS -> "boots";
			// Skyrim didn't say (an older plugin): the words of the name.
			default -> any(words, SHIELD_WORDS) ? "shield" : any(words, HELMET_WORDS) ? "helmet" : any(words, BOOTS_WORDS) ? "boots"
				: any(words, GLOVES_WORDS) ? "greaves" : "cuirass";
		};
		if (piece.equals("shield")) {
			return gear("minecraft:shield");
		}
		if (mat.equals("wooden")) {
			return null;
		}
		String tier = mat.isEmpty() ? NONE_ARMOR : mat;
		if (tier.equals("leather") || tier.equals("chainmail")) {
			return gear("minecraft:" + tier + "_" + switch (piece) {
				case "cuirass" -> "chestplate";
				case "greaves" -> "leggings";
				default -> piece;
			});
		}
		return gear("skycraft:" + tier + "_" + piece);
	}

	private static Gear gear(String item) {
		return new Gear(item, false);
	}

	private static boolean any(List<String> words, Set<String> wanted) {
		for (String w : words) {
			if (wanted.contains(w)) {
				return true;
			}
		}
		return false;
	}

	/** Chance for one piece to drop: {@code base}, plus {@code perLevel} per level of Looting, as Minecraft's linear effect adds it. */
	public static float chance(float base, float perLevel, int looting) {
		return Math.clamp(base + perLevel * Math.max(0, looting), 0.0F, 1.0F);
	}

	/**
	 * Arrows from a quiver of {@code had}: 0 to {@code max}, plus a rounded 0 to 1 per level of Looting (Minecraft's
	 * set_count and enchanted_count_increase for a skeleton), never more than it had.
	 */
	public static int arrows(Random random, int max, int looting, int had) {
		int n = random.nextInt(Math.max(0, max) + 1) + Math.round(Math.max(0, looting) * random.nextFloat());
		return Math.min(n, Math.max(0, had));
	}

	/** Damage a dropped piece comes with: Minecraft's Mob.dropCustomDeathLoot (anywhere from new to worn out). */
	public static int damage(int maxDamage, Random random) {
		return maxDamage - random.nextInt(1 + random.nextInt(Math.max(maxDamage - 3, 1)));
	}

	/** Parses {@code key = value} lines; bad ones keep the default and go to {@code problems}. */
	public static Settings parse(String text, List<String> problems) {
		float chance = VANILLA_CHANCE, looting = VANILLA_LOOTING;
		int arrows = VANILLA_ARROWS;
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			int hash = line.indexOf('#');
			if (hash >= 0) {
				line = line.substring(0, hash).strip();
			}
			if (line.isEmpty()) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq < 0) {
				problems.add("line " + n + ": expected 'key = value'");
				continue;
			}
			String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(eq + 1).strip();
			try {
				switch (key) {
					case "chance" -> chance = fraction(value);
					case "looting" -> looting = fraction(value);
					case "arrows" -> arrows = count(value);
					default -> problems.add("line " + n + ": unknown setting '" + key + "'");
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": bad value '" + value + "' for " + key);
			}
		}
		return new Settings(chance, looting, arrows);
	}

	private static float fraction(String value) {
		float f = Float.parseFloat(value);
		if (!(f >= 0.0F && f <= 1.0F)) {
			throw new NumberFormatException(value);
		}
		return f;
	}

	private static int count(String value) {
		int i = Integer.parseInt(value);
		if (i < 0 || i > 64) {
			throw new NumberFormatException(value);
		}
		return i;
	}
}
