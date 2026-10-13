package dev.skycraft.worlds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Tamriel Wilds' spawn lists keep vanilla 26.3's categories, the vanilla biome tags the Wilds join add to
 * the tag and name only Wilds biomes (a tag naming a missing biome fails to load, and the preview pack
 * leaves Home's out), and the mirror's spawn guards are in the mixin config. Reads the mod's resources from
 * the test classpath (no Minecraft). The data comes from tools/gen_worlds_data.py.
 */
class WorldsResourcesTest {
	private static final List<String> WILDS = List.of("whiterun_tundra", "falkreath_pines", "the_rift", "eastmarch_springs", "the_reach",
		"the_pale", "winterhold_peaks", "solstheim_ash", "tamriel_river", "sea_of_ghosts");
	/** gen_worlds_data.py VANILLA_TAGS. */
	private static final List<String> VANILLA_TAGS = List.of("has_structure/mineshaft", "has_structure/abandoned_camp_old_growth_spruce_taiga",
		"has_structure/abandoned_camp_dappled_forest", "has_structure/ruined_portal_mountain", "has_structure/village_plains",
		"has_structure/village_taiga", "has_structure/village_snowy", "has_structure/pillager_outpost", "has_structure/igloo",
		"has_structure/trail_ruins", "has_structure/trial_chambers", "has_structure/shipwreck", "has_structure/ocean_ruin_cold",
		"spawns_snow_foxes", "spawns_white_rabbits", "spawns_cold_variant_farm_animals", "polar_bears_spawn_on_alternate_blocks");

	private static String text(String path) {
		try (InputStream in = WorldsResourcesTest.class.getResourceAsStream("/" + path)) {
			assertNotNull(in, "missing resource " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError(path, e);
		}
	}

	private static String biome(String name) {
		return text("data/skycraft/worldgen/biome/" + name + ".json");
	}

	/** The entries of one spawn category in a biome's JSON, or "" if it has none. */
	private static String category(String biome, String category) {
		Matcher m = Pattern.compile("\"" + category + "\": \\[(.*?)\\]", Pattern.DOTALL).matcher(biome);
		return m.find() ? m.group(1) : "";
	}

	@Test
	void fishSwimAsVanillaDoes() {
		for (String name : WILDS) {
			String biome = biome(name);
			String water = category(biome, "water_creature");
			assertFalse(water.contains("\"minecraft:cod\"") || water.contains("\"minecraft:salmon\""), name + ": cod and salmon are water_ambient");
			assertFalse(category(biome, "water_ambient").contains("\"minecraft:squid\""), name + ": squid are water_creature");
			assertTrue(category(biome, "underground_water_creature").contains("\"minecraft:glow_squid\""), name + ": glow squid in cave water");
		}
		assertTrue(category(biome("sea_of_ghosts"), "water_ambient").contains("\"minecraft:cod\""));
		assertTrue(category(biome("sea_of_ghosts"), "monster").contains("\"minecraft:drowned\""));
		assertTrue(category(biome("tamriel_river"), "monster").contains("\"minecraft:drowned\""));
	}

	@Test
	void landHasAnimalsAndMonsters() {
		for (String name : WILDS) {
			if (!name.equals("tamriel_river")) {
				assertFalse(category(biome(name), "creature").isBlank(), name + " animals");
			}
			assertTrue(category(biome(name), "monster").contains("\"minecraft:zombie\""), name + " monsters");
		}
		assertTrue(category(biome("whiterun_tundra"), "creature").contains("\"minecraft:donkey\""));
		assertTrue(category(biome("home_fields"), "creature").contains("\"minecraft:sheep\""));
	}

	@Test
	void vanillaTagsOnlyGainWildsBiomes() {
		for (String tag : VANILLA_TAGS) {
			String json = text("data/minecraft/tags/worldgen/biome/" + tag + ".json");
			assertTrue(json.contains("\"replace\": false"), tag + " adds to the vanilla tag");
			Matcher m = Pattern.compile("\"([a-z_]+):([a-z_]+)\"").matcher(json);
			int n = 0;
			while (m.find()) {
				n++;
				assertTrue(m.group(1).equals("skycraft") && WILDS.contains(m.group(2)), tag + " names " + m.group());
			}
			assertTrue(n > 0, tag + " is empty");
		}
		assertTrue(text("data/minecraft/tags/worldgen/biome/has_structure/village_plains.json").contains("\"skycraft:whiterun_tundra\""));
		assertTrue(text("data/minecraft/tags/worldgen/biome/has_structure/village_snowy.json").contains("\"skycraft:the_pale\""));
	}

	@Test
	void theMirrorsSpawnGuardsAreRegistered() {
		String mixins = text("skycraft.mixins.json");
		for (String mixin : List.of("ServerChunkCacheSpawnMixin", "ServerLevelSpawnMixin", "TrialSpawnerMixin")) {
			assertTrue(mixins.contains("\"" + mixin + "\""), mixin);
		}
	}
}
