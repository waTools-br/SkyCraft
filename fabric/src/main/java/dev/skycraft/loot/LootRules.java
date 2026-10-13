package dev.skycraft.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * What Minecraft items a dead Skyrim creature drops, by its Skyrim display name.
 *
 * <p>Plain text, one rule per line, so it can be edited without a rebuild:
 * <pre>
 * # Skyrim name words = drops
 * frostbite spider = table minecraft:entities/spider
 * mammoth = minecraft:beef 2-5, minecraft:leather 1-3
 * draugr = table minecraft:entities/husk, minecraft:bone 0-1
 * centurion = skycraft:dwarven_metal_ingot 2-4, minecraft:redstone_block 1 25%
 * </pre>
 * A rule matches when one of its words is a whole word of the creature's name, ignoring case, so
 * "bear" matches "Cave Bear" but "wolf" doesn't match "Werewolf"; an entry of several words
 * ("sabre cat") needs all of them. The first matching rule wins.
 *
 * <p>A drop is {@code item [min-max] [chance%]}, rolled the way vanilla rolls a mob's: a drop without
 * a chance gets up to one more per Looting level (vanilla's {@code enchanted_count_increase}), a drop
 * with one a percentage point more per level instead (vanilla's {@code random_chance_with_enchanted_bonus},
 * 2.5% + 1% per level). {@code table <id>} names a vanilla loot table, which SkyLoot rolls as that
 * mob's death so vanilla does the rest. Pure Java on purpose, so it's unit tested without Minecraft.
 */
public final class LootRules {
	/** {@code chance} is a percentage; 100 means always. */
	public record Drop(String item, int min, int max, float chance) {
		public Drop(String item, int min, int max) {
			this(item, min, max, 100.0F);
		}

		/** Whether it drops only some of the time. */
		public boolean chancy() {
			return this.chance < 100.0F;
		}
	}

	/** {@code tables} are vanilla loot table ids, rolled besides {@code drops}. */
	public record Rule(List<String> words, List<Drop> drops, List<String> tables) {
	}

	private static final Pattern TABLE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	/** Raw meat and fish to their cooked kind, as vanilla's furnace_smelt does for a mob that died burning. */
	private static final Map<String, String> COOKED = Map.of("beef", "cooked_beef", "porkchop", "cooked_porkchop", "chicken", "cooked_chicken",
		"mutton", "cooked_mutton", "rabbit", "cooked_rabbit", "cod", "cooked_cod", "salmon", "cooked_salmon");

	/** Shipped defaults, written to the config folder on first run. Why each creature drops what it does: docs/CONVERSIONS.md, "Creature drops". */
	public static final String DEFAULT_TEXT = """
		# SkyCraft creature drops: what Skyrim creatures drop as Minecraft items when you kill them.
		# One rule per line:  name words = drop, drop, ...
		# A word matches a whole word of the creature's Skyrim name, ignoring case ("bear" matches
		# "Cave Bear", "wolf" doesn't match "Werewolf"); "sabre cat" needs both words. The first
		# matching line wins, so specific names come before general ones. Restart Skyrim after editing.
		# A drop is one of:
		#   item min-max        a count from min to max ("item 2" is exactly 2, "item" alone is 1);
		#                       Looting adds 0 to 1 more per level, as on Minecraft's mobs
		#   item min-max 2.5%   only that often; Looting adds 1% per level (Minecraft's rare drops)
		#   table <loot table>  a Minecraft mob's loot table, rolled as if you killed that mob: its
		#                       counts and chances, Looting, kill-only drops (spider eyes), cooked meat
		# Raw meat and fish come out cooked when the creature died burning, as in Minecraft.

		# Dragons are bosses and pay like it: bone and wing membrane, dragon's breath, a hoard of gold
		# and diamonds, and now and then netherite scrap (dragon bone and scale make Skyrim's best
		# armor) or a dragon head trophy. Older dragons give more; Alduin gives the most.
		alduin = minecraft:dragon_head 1, minecraft:netherite_scrap 2-4, minecraft:diamond 3-6, minecraft:gold_ingot 4-8, minecraft:bone 8-12, minecraft:dragon_breath 2-4
		legendary dragon, ancient dragon, paarthurnax, odahviing, durnehviir, vulthuryol, viinturuth, kruziikrel, sahrotaar = minecraft:bone 5-10, minecraft:phantom_membrane 2-3, minecraft:dragon_breath 1-3, minecraft:gold_ingot 2-5, minecraft:diamond 1-3, minecraft:netherite_scrap 1 33%, minecraft:dragon_head 1 20%
		elder dragon, revered dragon, nahagliiv, vuljotnaak, voslaarum, naaslaarum = minecraft:bone 4-8, minecraft:phantom_membrane 1-3, minecraft:dragon_breath 1-2, minecraft:gold_ingot 2-4, minecraft:diamond 1-2, minecraft:netherite_scrap 1 20%, minecraft:dragon_head 1 10%
		skeletal dragon = minecraft:bone 6-12, minecraft:bone_block 1-2, minecraft:gold_ingot 1-3, minecraft:diamond 0-1, minecraft:netherite_scrap 1 10%
		# Dragon priests are undead liches: Minecraft's evoker (a totem of undying), with bone and gold.
		dragon priest, krosis, morokei, hevnoraak, nahkriin, otar, rahgot, vokun, volsung, ahzidal, dukaan, zahkriisos = table minecraft:entities/evoker, minecraft:bone 1-3, minecraft:gold_ingot 1-3
		dragon, mirmulnir, sahloknir = minecraft:bone 3-6, minecraft:phantom_membrane 1-2, minecraft:dragon_breath 0-2, minecraft:gold_ingot 1-3, minecraft:diamond 0-1, minecraft:netherite_scrap 1 10%, minecraft:dragon_head 1 5%

		# Undead. Ghosts and shades are phantoms (ectoplasm is phantom membrane); draugr are husks, the
		# dried dead, with old bones, and the strong ones their tomb's gold; skeletons are skeletons,
		# and the Soul Cairn's wrathmen wither skeletons.
		ghost, ghostly, spectral, shade, mistman = table minecraft:entities/phantom
		draugr deathlord, draugr overlord, draugr scourge, wight lord = table minecraft:entities/husk, minecraft:bone 0-2, minecraft:gold_nugget 0-3
		draugr = table minecraft:entities/husk, minecraft:bone 0-1
		wrathman = table minecraft:entities/wither_skeleton
		skeleton, bonewalker, boneman = table minecraft:entities/skeleton
		ash spawn = minecraft:bone 0-2, minecraft:charcoal 0-2

		# Daedra. Each atronach is the Minecraft elemental nearest it: flame the blaze (fire salts are
		# blaze powder), storm the breeze. Frost has none: frost salts' packed ice, and snow. Hermaeus
		# Mora's seekers are squid, his lurkers guardians with ink.
		flame atronach, flame thrall = table minecraft:entities/blaze
		frost atronach, frost thrall = minecraft:packed_ice 0-1, minecraft:snowball 0-3
		storm atronach, storm thrall = table minecraft:entities/breeze
		seeker = table minecraft:entities/squid
		lurker = table minecraft:entities/guardian, minecraft:ink_sac 0-2

		# Dwemer automatons are dwarven metal (SkyCraft's ingot) run on power (redstone); a centurion's
		# dynamo core is a redstone block and a sphere's soul gem an amethyst shard, as item conversion
		# has them. Before "spider", so a Dwarven Spider is a machine.
		centurion = skycraft:dwarven_metal_ingot 2-4, minecraft:redstone 1-3, minecraft:redstone_block 1 25%
		ballista = skycraft:dwarven_metal_ingot 1-2, minecraft:redstone 0-2, minecraft:arrow 0-3
		dwarven sphere = skycraft:dwarven_metal_ingot 1-2, minecraft:redstone 0-2, minecraft:amethyst_shard 1 10%
		dwarven, dwemer = skycraft:dwarven_metal_ingot 0-1, minecraft:redstone 0-1

		# Spiders are Minecraft's spider: string, and a spider eye when you made the kill. Chaurus are
		# spider-like with a chitin shell (turtle scute, as item conversion has it); flying chaurus
		# hunters add wing membrane. Ash hoppers are big insects: jelly (slime ball) and string.
		chaurus hunter = table minecraft:entities/spider, minecraft:phantom_membrane 0-1, minecraft:turtle_scute 1 25%
		chaurus = table minecraft:entities/spider, minecraft:turtle_scute 1 25%
		giant frostbite spider = table minecraft:entities/spider, minecraft:string 1-3
		spider = table minecraft:entities/spider
		ash hopper = minecraft:slime_ball 0-1, minecraft:string 0-1

		# Falmer are blind and hunt by sound, like the Warden: sculk, flesh (a Falmer ear is rotten
		# flesh) and now and then an echo shard; the Forgotten Vale's strongest a little more.
		gloomlurker, shadowmaster, nightprowler, falmer warmonger = minecraft:sculk 1-2, minecraft:rotten_flesh 0-1, minecraft:echo_shard 1 5%, minecraft:sculk_catalyst 1 5%
		falmer = minecraft:sculk 0-1, minecraft:rotten_flesh 0-1, minecraft:echo_shard 1 2.5%

		# Spriggans are tree spirits: sticks and sap (resin clump); burnt ones charcoal. Hagravens are
		# Minecraft's witches with feathers. Wisps glow (glow dust is glowstone dust), wispmothers add
		# wisp wrappings (string). Ice wraiths: teeth (bone) and snow.
		burnt spriggan = minecraft:charcoal 0-2, minecraft:stick 0-1
		spriggan = minecraft:stick 0-2, minecraft:resin_clump 0-1
		hagraven = table minecraft:entities/witch, minecraft:feather 0-2
		wispmother = minecraft:glowstone_dust 0-2, minecraft:string 0-2
		wisp = minecraft:glowstone_dust 0-1
		ice wraith, wraith = minecraft:bone 0-1, minecraft:snowball 0-3

		# Trolls: troll fat (slime ball), hide and bone. Armored trolls are war beasts like the
		# ravager and carry its saddle; frost trolls add snow. Beast forms: hide and bone; a vampire
		# lord crumbles to vampire dust (gunpowder) and leaves wing membrane.
		armored troll = table minecraft:entities/ravager, minecraft:slime_ball 0-2, minecraft:bone 0-2
		frost troll = minecraft:slime_ball 0-2, minecraft:leather 0-2, minecraft:snowball 0-2
		troll = minecraft:slime_ball 0-2, minecraft:leather 0-2, minecraft:bone 0-1
		werewolf = minecraft:leather 0-2, minecraft:bone 0-2, minecraft:rotten_flesh 0-1
		werebear = minecraft:leather 1-3, minecraft:bone 0-2, minecraft:rotten_flesh 0-1
		vampire lord, harkon = minecraft:gunpowder 1-3, minecraft:phantom_membrane 0-2
		death hound = minecraft:rotten_flesh 0-2, minecraft:bone 0-1
		gargoyle = minecraft:cobblestone 0-2, minecraft:flint 0-1

		# Big game. Mammoths: more of a cow's beef and hide, and tusk bone. Sabre cats: hide and tooth
		# (bone). Bears are polar bears (fish) with their pelt. Minecraft's wolves drop nothing, so
		# Skyrim's keep their pelt and bones.
		mammoth = minecraft:beef 2-5, minecraft:leather 1-3, minecraft:bone 1-2
		sabre cat = minecraft:leather 0-2, minecraft:bone 0-1
		bear = table minecraft:entities/polar_bear, minecraft:leather 0-2
		wolf = minecraft:bone 0-2, minecraft:leather 0-1

		# Animals with a Minecraft twin use its table: deer and elk are cows, hares rabbits,
		# bristlebacks (boars) hoglins. Minecraft's goats and foxes drop nothing, so a goat gives a
		# sheep's mutton and a hide, a fox its pelt (rabbit hide).
		cow, deer, elk = table minecraft:entities/cow
		chicken, hen = table minecraft:entities/chicken
		horse = table minecraft:entities/horse
		hare, rabbit = table minecraft:entities/rabbit
		bristleback, boar = table minecraft:entities/hoglin
		goat = minecraft:mutton 1-2, minecraft:leather 0-1
		fox = minecraft:rabbit_hide 0-2

		# Shore and water. Horkers: meat (beef, as item conversion has it) and tusk bone. Mudcrabs:
		# legs (cod) and now and then their chitin (turtle scute). Slaughterfish are salmon. Netch
		# are floating jellies: hide (leather) and jelly (slime ball).
		horker = minecraft:beef 1-3, minecraft:bone 0-2
		mudcrab = minecraft:cod 0-2, minecraft:turtle_scute 1 10%
		slaughterfish = table minecraft:entities/salmon
		netch = minecraft:leather 0-2, minecraft:slime_ball 0-2

		# Skeevers: tail (rotten flesh) and a small hide (rabbit hide); venomfangs now and then a
		# spider eye. Rieklings: bone and hide.
		venomfang skeever = minecraft:rotten_flesh 0-1, minecraft:rabbit_hide 0-1, minecraft:spider_eye 1 25%
		skeever = minecraft:rotten_flesh 0-1, minecraft:rabbit_hide 0-1
		riekling = minecraft:bone 0-1, minecraft:leather 0-1

		# Giants last, so a Giant Frostbite Spider or Giant Mudcrab matches its own line first. A
		# giant's toe is rotten flesh in item conversion; giants also hoard a little gold.
		giant, karstaag = minecraft:bone 1-3, minecraft:leather 0-2, minecraft:rotten_flesh 0-2, minecraft:gold_nugget 0-4
		""";

	/**
	 * Earlier shipped defaults, word for word. A config file still holding one of them was never
	 * edited, so config.Config replaces it with {@link #DEFAULT_TEXT} (keeping a .old copy).
	 */
	public static final List<String> OLD_DEFAULTS = List.of("""
		# SkyCraft creature drops: what Skyrim creatures drop as Minecraft items when you kill them.
		# One rule per line:  name words = item min-max, item min-max, ...
		# A word matches a whole word of the creature's Skyrim name, ignoring case ("bear" matches
		# "Cave Bear"). The first matching line wins. Restart Skyrim after editing.
		deer, elk = minecraft:beef 1-3, minecraft:leather 0-2
		goat = minecraft:mutton 1-2
		hare, rabbit, fox = minecraft:rabbit 1-1, minecraft:rabbit_hide 0-1
		chicken, hen = minecraft:chicken 1-1, minecraft:feather 0-2
		cow = minecraft:beef 2-4, minecraft:leather 1-2
		mammoth = minecraft:beef 4-8, minecraft:leather 2-4
		bear, sabre = minecraft:beef 2-4, minecraft:leather 1-3
		wolf = minecraft:bone 1-2
		mudcrab, slaughterfish = minecraft:cod 1-2
		skeever = minecraft:rotten_flesh 1-1
		horker = minecraft:salmon 2-3
		""");

	private final List<Rule> rules;

	private LootRules(List<Rule> rules) {
		this.rules = rules;
	}

	public List<Rule> rules() {
		return this.rules;
	}

	/** Parses rule text. Bad lines and drops are skipped and reported to {@code problems}. */
	public static LootRules parse(String text, List<String> problems) {
		List<Rule> out = new ArrayList<>();
		int n = 0;
		for (String raw : text.split("\\R")) {
			n++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			int eq = line.indexOf('=');
			if (eq <= 0) {
				problems.add("line " + n + ": no '='");
				continue;
			}
			List<String> words = new ArrayList<>();
			for (String w : line.substring(0, eq).split(",")) {
				// Split like creature names are: "sabre cat" (or "sabre-cat") needs both words.
				String word = String.join(" ", ItemRules.words(w));
				if (!word.isEmpty()) {
					words.add(word);
				}
			}
			List<Drop> drops = new ArrayList<>();
			List<String> tables = new ArrayList<>();
			int problemsBefore = problems.size();
			for (String d : line.substring(eq + 1).split(",")) {
				String part = d.strip();
				if (part.isEmpty()) {
					continue;
				}
				String[] bits = part.split("\\s+");
				if (bits[0].equalsIgnoreCase("table")) {
					if (bits.length != 2 || !TABLE_ID.matcher(bits[1]).matches()) {
						problems.add("line " + n + ": expected 'table <loot table>' in '" + part + "'");
						continue;
					}
					tables.add(bits[1]);
					continue;
				}
				Drop drop = drop(bits, part, n, problems);
				if (drop != null) {
					drops.add(drop);
				}
			}
			if (words.isEmpty() || (drops.isEmpty() && tables.isEmpty())) {
				if (problems.size() == problemsBefore) {
					problems.add("line " + n + ": needs at least one word and one drop");
				}
				continue;
			}
			out.add(new Rule(List.copyOf(words), List.copyOf(drops), List.copyOf(tables)));
		}
		return new LootRules(List.copyOf(out));
	}

	/** One {@code item [min-max] [chance%]} drop, or null (reported to {@code problems}) if it's malformed. */
	private static Drop drop(String[] bits, String part, int n, List<String> problems) {
		int min = 1, max = 1;
		float chance = 100.0F;
		boolean counted = false, chanced = false;
		for (int i = 1; i < bits.length; i++) {
			String bit = bits[i];
			boolean percent = bit.endsWith("%");
			if (percent ? chanced : counted) {
				problems.add("line " + n + ": expected 'item min-max chance%' in '" + part + "'");
				return null;
			}
			try {
				if (percent) {
					chance = Float.parseFloat(bit.substring(0, bit.length() - 1));
					chanced = true;
				} else {
					String[] range = bit.split("-", -1);
					min = Integer.parseInt(range[0]);
					max = range.length > 1 ? Integer.parseInt(range[1]) : min;
					counted = true;
					if (range.length > 2) {
						throw new NumberFormatException(bit);
					}
				}
			} catch (NumberFormatException e) {
				problems.add("line " + n + ": bad " + (percent ? "chance" : "count") + " in '" + part + "'");
				return null;
			}
		}
		if (min < 0 || max < min || max > 64) {
			problems.add("line " + n + ": count out of range in '" + part + "'");
			return null;
		}
		if (!(chance > 0.0F && chance <= 100.0F)) {
			problems.add("line " + n + ": chance out of range in '" + part + "'");
			return null;
		}
		return new Drop(bits[0], min, max, chance);
	}

	/** The first rule with an entry whose words are all whole words of {@code name}, or null. */
	public Rule match(String name) {
		List<String> nameWords = ItemRules.words(name);
		if (nameWords.isEmpty()) {
			return null;
		}
		for (Rule rule : this.rules) {
			for (String word : rule.words()) {
				if (word.indexOf(' ') < 0 ? nameWords.contains(word) : nameWords.containsAll(List.of(word.split(" ")))) {
					return rule;
				}
			}
		}
		return null;
	}

	/** Rolls a count for each drop, without Looting; zero counts are left out. */
	public static List<Drop> roll(Rule rule, Random random) {
		return roll(rule, 0, random);
	}

	/**
	 * Rolls each drop the way vanilla rolls a mob's, for a killer with Looting level {@code looting};
	 * zero counts and missed chances are left out. The rule's tables aren't rolled here (SkyLoot does).
	 */
	public static List<Drop> roll(Rule rule, int looting, Random random) {
		int level = Math.max(0, looting);
		List<Drop> out = new ArrayList<>();
		for (Drop d : rule.drops()) {
			int count = d.min() + (d.max() > d.min() ? random.nextInt(d.max() - d.min() + 1) : 0);
			if (d.chancy()) {
				// random_chance_with_enchanted_bonus: a rare drop gets one percentage point per level.
				if (random.nextFloat() * 100.0F >= d.chance() + level) {
					continue;
				}
			} else if (level > 0) {
				// enchanted_count_increase with vanilla's usual 0-1 per level: round(level * uniform 0-1).
				count += Math.round(level * random.nextFloat());
			}
			if (count > 0) {
				out.add(new Drop(d.item(), count, count));
			}
		}
		return out;
	}

	/** The cooked kind of raw meat or fish ("minecraft:beef" to "minecraft:cooked_beef"), or {@code item} itself. */
	public static String cooked(String item) {
		int colon = item.indexOf(':');
		if (colon >= 0 && !item.substring(0, colon).equals("minecraft")) {
			return item;
		}
		String cooked = COOKED.get(item.substring(colon + 1));
		return cooked == null ? item : item.substring(0, colon + 1) + cooked;
	}
}
