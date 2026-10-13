package dev.skycraft.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.skycraft.loot.ItemRules;
import dev.skycraft.loot.LootRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigTest {
	private static final String OLD_1 = "# first\na = 1\n";
	private static final String OLD_2 = "# second\na = 2\n";
	private static final String NEW = "# third\na = 3\nb = 4\n";

	@TempDir
	Path dir;

	@Test
	void replacesAnUntouchedEarlierDefaultAndKeepsIt() throws IOException {
		Path file = this.dir.resolve("skycraft-test.txt");
		Files.writeString(file, OLD_2);
		assertTrue(Config.upgrade(file, NEW, List.of(OLD_1, OLD_2)));
		assertEquals(NEW, Files.readString(file));
		assertEquals(OLD_2, Files.readString(this.dir.resolve("skycraft-test.txt.old")));
		// Once it holds the new defaults, there's nothing more to do.
		assertFalse(Config.upgrade(file, NEW, List.of(OLD_1, OLD_2)));
		assertEquals(NEW, Files.readString(file));
	}

	@Test
	void leavesEditedFilesAlone() throws IOException {
		Path file = this.dir.resolve("skycraft-test.txt");
		String edited = OLD_1 + "c = 5\n";
		Files.writeString(file, edited);
		assertFalse(Config.upgrade(file, NEW, List.of(OLD_1, OLD_2)));
		assertEquals(edited, Files.readString(file));
		assertFalse(Files.exists(Config.backup(file)));
		Files.writeString(file, OLD_1.replace("a = 1", "a = 9"));
		assertFalse(Config.upgrade(file, NEW, List.of(OLD_1, OLD_2)));
	}

	@Test
	void lineEndingsAndAByteOrderMarkAreNoEdit() {
		// An editor may save the file as CRLF, add a BOM or drop the last newline without anyone changing a rule.
		assertTrue(Config.untouched(OLD_1.replace("\n", "\r\n"), List.of(OLD_1)));
		assertTrue(Config.untouched("﻿" + OLD_1, List.of(OLD_1)));
		assertTrue(Config.untouched(OLD_1.strip(), List.of(OLD_1)));
		assertFalse(Config.untouched(OLD_1.replace("# first", "#first"), List.of(OLD_1)));
		assertFalse(Config.untouched(OLD_1, List.of()));
	}

	@Test
	void backupSitsBesideTheFile() {
		assertEquals(this.dir.resolve("skycraft-loot.txt.old"), Config.backup(this.dir.resolve("skycraft-loot.txt")));
	}

	@Test
	void oldLootDefaultsGetTheNewOnes() throws IOException {
		Path file = this.dir.resolve("skycraft-loot.txt");
		Files.writeString(file, LootRules.OLD_DEFAULTS.getFirst());
		assertTrue(Config.upgrade(file, LootRules.DEFAULT_TEXT, LootRules.OLD_DEFAULTS));
		assertEquals(LootRules.DEFAULT_TEXT, Files.readString(file));
		assertTrue(Files.readString(Config.backup(file)).contains("wolf = minecraft:bone 1-2"));
		assertFalse(Config.untouched(LootRules.DEFAULT_TEXT, LootRules.OLD_DEFAULTS));
		// The default every earlier version shipped, as it was written to players' config folders.
		assertTrue(LootRules.OLD_DEFAULTS.getFirst().startsWith("# SkyCraft creature drops: what Skyrim creatures drop as Minecraft items when you kill them.\n"));
		assertTrue(LootRules.OLD_DEFAULTS.getFirst().endsWith("\nhorker = minecraft:salmon 2-3\n"));
	}

	@Test
	void anOldDefaultCanBeGivenByItsHash() throws IOException {
		List<String> old = List.of(Config.SHA256 + Config.sha256(OLD_1));
		assertTrue(Config.untouched(OLD_1, old));
		assertTrue(Config.untouched(OLD_1.replace("\n", "\r\n"), old));
		assertFalse(Config.untouched(OLD_1 + "c = 5\n", old));
		Path file = this.dir.resolve("skycraft-items.txt");
		Files.writeString(file, OLD_1);
		assertTrue(Config.upgrade(file, NEW, old));
		assertEquals(NEW, Files.readString(file));
	}

	@Test
	void itemRulesKnowTheirOldDefaultsButNotTheCurrentOne() {
		assertEquals(8, ItemRules.OLD_DEFAULTS.size());
		for (String old : ItemRules.OLD_DEFAULTS) {
			assertTrue(old.matches("sha256:[0-9a-f]{64}"), old);
		}
		assertFalse(Config.untouched(ItemRules.DEFAULT_TEXT, ItemRules.OLD_DEFAULTS));
	}
}
