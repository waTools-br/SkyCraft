package dev.skycraft.config;

import dev.skycraft.SkyCraft;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Editable text files in Minecraft's config folder, written with defaults on first use. */
public final class Config {
	/** Marks an old default given by its hash ({@link #untouched}). */
	public static final String SHA256 = "sha256:";

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

	/**
	 * Whether {@code text} is one of {@code oldDefaults} as it was shipped. An entry {@code "sha256:<hex>"} stands
	 * for an old default by the SHA-256 of its text (UTF-8, as {@link #plain} leaves it): long files keep their
	 * history short that way.
	 */
	public static boolean untouched(String text, List<String> oldDefaults) {
		String hash = null;
		for (String old : oldDefaults) {
			if (old.startsWith(SHA256)) {
				if (hash == null) {
					hash = sha256(text);
				}
				if (old.substring(SHA256.length()).equalsIgnoreCase(hash)) {
					return true;
				}
			} else if (same(text, old)) {
				return true;
			}
		}
		return false;
	}

	/** The SHA-256 of {@code text} as {@link #untouched} compares it, in hex. */
	public static String sha256(String text) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(plain(text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e); // every Java has SHA-256
		}
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
