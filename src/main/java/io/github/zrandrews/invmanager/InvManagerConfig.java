package io.github.zrandrews.invmanager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side settings, stored in config/invmanager.json.
 * Edit the file and restart the server to apply.
 */
public class InvManagerConfig {
	/** How far (in blocks, in every direction) a Hot Deposit block looks for containers. */
	public int hotDepositRadius = 6;

	/** If true, Hot Deposit also takes items from your hotbar. Off by default so your tools stay put. */
	public boolean hotDepositIncludesHotbar = false;

	/** If true, sorting your own inventory also rearranges the hotbar. */
	public boolean sortIncludesHotbar = false;

	/** Give each player 1 Sorter and 3 Hot Deposit blocks the first time they join (like the Bedrock add-on). */
	public boolean giveStarterKit = true;

	/** Ticks a player must wait between two Hot Deposit / Sorter punches (20 ticks = 1 second). */
	public int punchCooldownTicks = 8;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static InvManagerConfig instance = new InvManagerConfig();

	public static InvManagerConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("invmanager.json");

		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					InvManagerConfig loaded = GSON.fromJson(reader, InvManagerConfig.class);
					if (loaded != null) instance = loaded;
				}
			}
			// Write the file back so new settings show up with their defaults.
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException | RuntimeException e) {
			InvManagerMod.LOGGER.error("Could not read config/invmanager.json, using defaults", e);
		}
	}
}
