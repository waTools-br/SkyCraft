package dev.skycraft.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.skycraft.gear.GearTiers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LootRulesTest {
	private static LootRules defaults() {
		List<String> problems = new ArrayList<>();
		LootRules rules = LootRules.parse(LootRules.DEFAULT_TEXT, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		return rules;
	}

	private static LootRules parseClean(String text) {
		List<String> problems = new ArrayList<>();
		LootRules rules = LootRules.parse(text, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		return rules;
	}

	/** The first entry of the rule {@code name} matches: which line of the defaults took it. */
	private static String line(LootRules rules, String name) {
		LootRules.Rule rule = rules.match(name);
		return rule == null ? null : rule.words().getFirst();
	}

	@Test
	void defaultsParseCleanly() {
		LootRules rules = defaults();
		assertEquals(61, rules.rules().size());
		for (LootRules.Rule rule : rules.rules()) {
			assertFalse(rule.drops().isEmpty() && rule.tables().isEmpty(), rule.words().toString());
		}
	}

	@Test
	void matchesWholeWordsIgnoringCase() {
		LootRules rules = defaults();
		assertEquals(List.of("minecraft:entities/cow"), rules.match("Deer").tables());
		assertEquals(List.of("minecraft:entities/polar_bear"), rules.match("Cave Bear").tables());
		assertEquals("minecraft:leather", rules.match("Snowy Sabre Cat").drops().getFirst().item());
		assertEquals("minecraft:cod", rules.match("Giant Mudcrab").drops().getFirst().item());
		assertNotNull(rules.match("ice wolf"));
	}

	@Test
	void everyCreatureMatchesItsOwnLine() {
		LootRules rules = defaults();
		Map<String, String> expected = new LinkedHashMap<>();
		// Specific names before general ones: the machine before the spider, the giant spider before the giant.
		expected.put("Frostbite Spider", "spider");
		expected.put("Giant Frostbite Spider", "giant frostbite spider");
		expected.put("Albino Spider", "spider");
		expected.put("Dwarven Spider", "dwarven");
		expected.put("Dwarven Spider Guardian", "dwarven");
		expected.put("Dwarven Sphere", "dwarven sphere");
		expected.put("Dwarven Centurion Guard", "centurion"); // "Dwarven Centurion Guardian" cut to Skyrim's 23 bytes
		expected.put("Dwarven Ballista", "ballista");
		expected.put("Chaurus", "chaurus");
		expected.put("Chaurus Reaper", "chaurus");
		expected.put("Chaurus Hunter", "chaurus hunter");
		expected.put("Chaurus Hunter Fledglin", "chaurus hunter");
		expected.put("Ash Hopper", "ash hopper");
		expected.put("Falmer", "falmer");
		expected.put("Falmer Skulker", "falmer");
		expected.put("Falmer Gloomlurker", "gloomlurker");
		expected.put("Falmer Warmonger", "gloomlurker");
		expected.put("Restless Draugr", "draugr");
		expected.put("Draugr Wight", "draugr");
		expected.put("Draugr Deathlord", "draugr deathlord");
		expected.put("Draugr Death Overlord", "draugr deathlord");
		expected.put("Spectral Draugr", "ghost");
		expected.put("Ghost", "ghost");
		expected.put("Shade", "ghost");
		expected.put("Skeleton", "skeleton");
		expected.put("Bonewalker", "skeleton");
		expected.put("Wrathman", "wrathman");
		expected.put("Ash Spawn", "ash spawn");
		expected.put("Dragon Priest", "dragon priest");
		expected.put("Morokei", "dragon priest");
		expected.put("Otar the Mad", "dragon priest");
		expected.put("Dragon", "dragon");
		expected.put("Blood Dragon", "dragon");
		expected.put("Frost Dragon", "dragon");
		expected.put("Serpentine Dragon", "dragon");
		expected.put("Elder Dragon", "elder dragon");
		expected.put("Revered Dragon", "elder dragon");
		expected.put("Ancient Dragon", "legendary dragon");
		expected.put("Legendary Dragon", "legendary dragon");
		expected.put("Odahviing", "legendary dragon");
		expected.put("Skeletal Dragon", "skeletal dragon");
		expected.put("Alduin", "alduin");
		expected.put("Flame Atronach", "flame atronach");
		expected.put("Frost Atronach", "frost atronach");
		expected.put("Storm Atronach", "storm atronach");
		expected.put("Seeker", "seeker");
		expected.put("Lurker", "lurker");
		expected.put("Spriggan", "spriggan");
		expected.put("Spriggan Matron", "spriggan");
		expected.put("Spriggan Earth Mother", "spriggan");
		expected.put("Burnt Spriggan", "burnt spriggan");
		expected.put("Hagraven", "hagraven");
		expected.put("Wispmother", "wispmother");
		expected.put("Wisp", "wisp");
		expected.put("Ice Wraith", "ice wraith");
		expected.put("Troll", "troll");
		expected.put("Frost Troll", "frost troll");
		expected.put("Armored Troll", "armored troll");
		expected.put("Armored Frost Troll", "armored troll");
		expected.put("Werewolf", "werewolf");
		expected.put("Werebear", "werebear");
		expected.put("Vampire Lord", "vampire lord");
		expected.put("Death Hound", "death hound");
		expected.put("Gargoyle", "gargoyle");
		expected.put("Mammoth", "mammoth");
		expected.put("Sabre Cat", "sabre cat");
		expected.put("Snowy Sabre Cat", "sabre cat");
		expected.put("Vale Sabre Cat", "sabre cat");
		expected.put("Bear", "bear");
		expected.put("Snow Bear", "bear");
		expected.put("Wolf", "wolf");
		expected.put("Ice Wolf", "wolf");
		expected.put("Cow", "cow");
		expected.put("Deer", "cow");
		expected.put("Elk", "cow");
		expected.put("Vale Deer", "cow");
		expected.put("Chicken", "chicken");
		expected.put("Horse", "horse");
		expected.put("Hare", "hare");
		expected.put("Bristleback", "bristleback");
		expected.put("Goat", "goat");
		expected.put("Fox", "fox");
		expected.put("Horker", "horker");
		expected.put("Mudcrab", "mudcrab");
		expected.put("Giant Mudcrab", "mudcrab");
		expected.put("Slaughterfish", "slaughterfish");
		expected.put("Bull Netch", "netch");
		expected.put("Venomfang Skeever", "venomfang skeever");
		expected.put("Skeever", "skeever");
		expected.put("Riekling", "riekling");
		expected.put("Giant", "giant");
		expected.put("Karstaag", "giant");
		for (Map.Entry<String, String> e : expected.entrySet()) {
			assertEquals(e.getValue(), line(rules, e.getKey()), e.getKey());
		}
	}

	@Test
	void ignoresPeopleAndPartialWords() {
		LootRules rules = defaults();
		for (String name : List.of("Bandit Marauder", "Lydia", "Whiterun Guard", "Vampire", "Dremora", "Forsworn Briarheart", "Dragonborn", "Frost", "")) {
			assertNull(rules.match(name), name);
		}
		assertNull(rules.match(null));
	}

	@Test
	void spidersRollMinecraftsSpiderTable() {
		LootRules.Rule spider = defaults().match("Frostbite Spider");
		assertEquals(List.of("minecraft:entities/spider"), spider.tables());
		assertTrue(spider.drops().isEmpty());
	}

	@Test
	void entriesOfSeveralWordsNeedAllOfThem() {
		LootRules rules = parseClean("""
			sabre cat, ice-wraith = minecraft:string 1-2
			cat = minecraft:cod 1
			""");
		// Before, "sabre cat" was one word that no name has, so the sabre cat fell through to "cat".
		assertEquals("minecraft:string", rules.match("Snowy Sabre Cat").drops().getFirst().item());
		assertEquals("minecraft:string", rules.match("Ice Wraith").drops().getFirst().item());
		assertEquals("minecraft:cod", rules.match("Cat").drops().getFirst().item());
		assertNull(rules.match("Sabre Tooth"));
	}

	@Test
	void parsesTablesChancesAndCounts() {
		LootRules rules = parseClean("""
			frostbite spider = table minecraft:entities/spider, minecraft:cobweb 0-1
			dragon = minecraft:diamond 1 2.5%, minecraft:emerald 33%, minecraft:bone 3-6, minecraft:gold_ingot 2
			ghost = TABLE minecraft:entities/phantom
			""");
		LootRules.Rule spider = rules.match("Frostbite Spider");
		assertEquals(List.of("minecraft:entities/spider"), spider.tables());
		assertEquals(List.of(new LootRules.Drop("minecraft:cobweb", 0, 1)), spider.drops());
		assertEquals(List.of(new LootRules.Drop("minecraft:diamond", 1, 1, 2.5F), new LootRules.Drop("minecraft:emerald", 1, 1, 33.0F),
			new LootRules.Drop("minecraft:bone", 3, 6), new LootRules.Drop("minecraft:gold_ingot", 2, 2)), rules.match("Blood Dragon").drops());
		assertTrue(rules.match("Blood Dragon").drops().getFirst().chancy());
		assertFalse(rules.match("Blood Dragon").drops().get(2).chancy());
		assertEquals(List.of("minecraft:entities/phantom"), rules.match("Ghost").tables());
	}

	@Test
	void earlierDefaultsStillParseTheSame() {
		// Lines written in the old format (item min-max) keep their meaning.
		LootRules old = parseClean(LootRules.OLD_DEFAULTS.getFirst());
		assertEquals(11, old.rules().size());
		assertEquals(List.of(new LootRules.Drop("minecraft:beef", 1, 3), new LootRules.Drop("minecraft:leather", 0, 2)), old.match("Deer").drops());
		assertEquals(new LootRules.Drop("minecraft:rabbit", 1, 1), old.match("Fox").drops().getFirst());
		assertTrue(old.match("Deer").tables().isEmpty());
	}

	@Test
	void rollsWithinRange() {
		LootRules.Rule deer = parseClean("deer = minecraft:beef 1-3, minecraft:leather 0-2").match("Deer");
		Random random = new Random(42);
		for (int i = 0; i < 500; i++) {
			for (LootRules.Drop d : LootRules.roll(deer, random)) {
				if (d.item().equals("minecraft:beef")) {
					assertTrue(d.min() >= 1 && d.min() <= 3, "beef " + d.min());
				} else {
					assertEquals("minecraft:leather", d.item());
					assertTrue(d.min() >= 1 && d.min() <= 2, "leather " + d.min());
				}
			}
		}
	}

	@Test
	void lootingAddsUpToOnePerLevelLikeVanilla() {
		LootRules.Rule rule = parseClean("x = minecraft:string 0-2").match("X");
		Random random = new Random(7);
		int most = 0;
		long without = 0, with = 0;
		for (int i = 0; i < 20000; i++) {
			for (LootRules.Drop d : LootRules.roll(rule, 0, random)) {
				assertTrue(d.min() <= 2, "no Looting: " + d.min());
				without += d.min();
			}
			for (LootRules.Drop d : LootRules.roll(rule, 3, random)) {
				assertTrue(d.min() <= 5, "Looting III: " + d.min());
				most = Math.max(most, d.min());
				with += d.min();
			}
		}
		assertEquals(5, most);
		// 0-2 averages 1; Looting III adds round(3 * uniform 0-1), which averages 1.5.
		assertEquals(1.0, without / 20000.0, 0.05);
		assertEquals(2.5, with / 20000.0, 0.08);
	}

	@Test
	void chanceDropsFollowTheirRateAndLootingAddsAPoint() {
		LootRules.Rule rule = parseClean("x = minecraft:diamond 1 2.5%").match("X");
		Random random = new Random(11);
		int n = 200000, plain = 0, looted = 0;
		for (int i = 0; i < n; i++) {
			for (LootRules.Drop d : LootRules.roll(rule, 0, random)) {
				assertEquals(1, d.min());
				plain++;
			}
			for (LootRules.Drop d : LootRules.roll(rule, 3, random)) {
				assertEquals(1, d.min(), "Looting raises the chance, not the count");
				looted++;
			}
		}
		assertEquals(0.025, plain / (double) n, 0.002);
		assertEquals(0.055, looted / (double) n, 0.002);
	}

	@Test
	void cooksRawMeatAndFish() {
		assertEquals("minecraft:cooked_beef", LootRules.cooked("minecraft:beef"));
		assertEquals("minecraft:cooked_salmon", LootRules.cooked("minecraft:salmon"));
		assertEquals("cooked_mutton", LootRules.cooked("mutton"));
		assertEquals("minecraft:leather", LootRules.cooked("minecraft:leather"));
		assertEquals("minecraft:cooked_beef", LootRules.cooked("minecraft:cooked_beef"));
		assertEquals("skycraft:beef", LootRules.cooked("skycraft:beef"));
	}

	@Test
	void reportsBadLinesAndKeepsGoodOnes() {
		List<String> problems = new ArrayList<>();
		LootRules rules = LootRules.parse("""
			# comment
			deer = minecraft:beef 1-3
			no equals sign here
			goat = minecraft:mutton x-2
			skeever = minecraft:rotten_flesh 5-2
			cow = minecraft:beef
			""", problems);
		assertEquals(2, rules.rules().size());
		assertEquals(3, problems.size(), problems.toString());
		assertEquals(1, rules.match("Cow").drops().getFirst().max());
	}

	@Test
	void reportsBadTablesAndChances() {
		List<String> problems = new ArrayList<>();
		LootRules rules = LootRules.parse("""
			spider = table
			ghost = table Minecraft:Entities/Phantom
			troll = table minecraft:entities/ravager extra
			wisp = minecraft:glowstone_dust 1 0%
			wraith = minecraft:bone 1 101%
			horker = minecraft:bone 1 x%
			netch = minecraft:leather 1 2
			seeker = minecraft:ink_sac 5% 6%
			dragon = minecraft:diamond 1-2-3
			giant = minecraft:bone 1-3, minecraft:gold 1 200%
			""", problems);
		assertEquals(10, problems.size(), problems.toString());
		assertEquals(1, rules.rules().size());
		assertEquals(List.of(new LootRules.Drop("minecraft:bone", 1, 3)), rules.match("Giant").drops());
	}

	/** Every table the defaults name is an entity loot table of Minecraft 26.3's (when its data is on the test classpath). */
	@Test
	void defaultsNameRealTables() throws IOException {
		assumeTrue(LootRulesTest.class.getResource("/data/minecraft/loot_table/entities/spider.json") != null, "Minecraft's data isn't on the test classpath");
		for (LootRules.Rule rule : defaults().rules()) {
			for (String table : rule.tables()) {
				String[] id = table.split(":");
				try (InputStream in = LootRulesTest.class.getResourceAsStream("/data/" + id[0] + "/loot_table/" + id[1] + ".json")) {
					assertNotNull(in, "no loot table " + table);
					assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"minecraft:entity\""), table + " is an entity table");
				}
			}
		}
	}

	/** Every item the defaults drop, raw or cooked, is one of Minecraft's (a field of Items, read without starting Minecraft) or SkyCraft's. */
	@Test
	void defaultsDropRealItems() throws ClassNotFoundException {
		Class<?> items = Class.forName("net.minecraft.world.item.Items", false, LootRulesTest.class.getClassLoader());
		for (LootRules.Rule rule : defaults().rules()) {
			for (LootRules.Drop d : rule.drops()) {
				for (String item : List.of(d.item(), LootRules.cooked(d.item()))) {
					String[] id = item.split(":");
					if (id[0].equals("skycraft")) {
						assertTrue(GearTiers.allItemIds().contains(id[1]), item);
						continue;
					}
					assertEquals("minecraft", id[0], item);
					try {
						items.getField(id[1].toUpperCase(Locale.ROOT));
					} catch (NoSuchFieldException e) {
						throw new AssertionError("no item " + item, e);
					}
				}
			}
		}
	}
}
