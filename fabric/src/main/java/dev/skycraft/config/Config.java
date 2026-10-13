package dev.skycraft.config;

import dev.skycraft.SkyCraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Editable text files in Minecraft's config folder, written with defaults on first use. */
public final class Config {
	private Config() {
	}

	/** The file's text, or {@code defaults} (written to the file if it's missing). */
	public static String read(String file, String defaults) {
		return read(file, defaults, List.of());
	}

	/**
	 * The file's text, or {@code defaults} (written to the file if it's missing). A file that still
	 * holds one of {@code oldDefaults}, earlier shipped defaults nobody edited, gets the new
	 * {@code defaults} instead, with its old text kept beside it in {@code <file>.old} (see {@link #upgrade}).
	 * A file someone edited is never touched.
	 */
	public static String read(String file, String defaults, List<String> oldDefaults) {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(file);
		try {
			if (Files.exists(path)) {
				if (upgrade(path, defaults, oldDefaults)) {
					SkyCraft.LOG.info("SkyCraft: {} held earlier defaults you never edited, so it has the new ones now (the old file is {})", path,
						backup(path).getFileName());
					return defaults;
				}
				return Files.readString(path);
			}
			Files.createDirectories(path.getParent());
			Files.writeString(path, defaults);
			SkyCraft.LOG.info("SkyCraft: wrote defaults to {}", path);
		} catch (IOException e) {
			SkyCraft.LOG.warn("SkyCraft: couldn't read {}, using defaults: {}", path, e.toString());
		}
		return defaults;
	}

	/**
	 * Replaces the file at {@code path} with {@code defaults} if it holds one of {@code oldDefaults}
	 * untouched, keeping its text in {@link #backup} (over any older backup). Returns whether it did.
	 */
	public static boolean upgrade(Path path, String defaults, List<String> oldDefaults) throws IOException {
		String text = Files.readString(path);
		if (!untouched(text, oldDefaults) || same(text, defaults)) {
			return false;
		}
		Files.writeString(backup(path), text);
		Files.writeString(path, defaults);
		return true;
	}

	/** Whether {@code text} is one of {@code oldDefaults} as it was shipped. */
	public static boolean untouched(String text, List<String> oldDefaults) {
		for (String old : oldDefaults) {
			if (same(text, old)) {
				return true;
			}
		}
		return false;
	}

	/** Where {@link #upgrade} keeps a replaced file: beside it, with ".old" added to its name. */
	public static Path backup(Path path) {
		return path.resolveSibling(path.getFileName() + ".old");
	}

	private static boolean same(String a, String b) {
		return plain(a).equals(plain(b));
	}

	/** Text as written, less what an editor changes without anyone editing: line endings, a byte order mark, a final newline. */
	private static String plain(String text) {
		String s = text.replace("\r\n", "\n").replace('\r', '\n');
		return (s.startsWith("﻿") ? s.substring(1) : s).stripTrailing();
	}
}
