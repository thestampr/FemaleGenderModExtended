/*
 * Wildfire's Female Gender Mod is a female gender mod created for Minecraft.
 * Copyright (C) 2023-present WildfireRomeo
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wildfire.main;

import com.google.gson.JsonObject;
import com.wildfire.main.cloud.CloudSync;
import com.wildfire.main.config.ClientConfig;
import com.wildfire.main.config.Configuration;
import com.wildfire.main.contributors.Contributors;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.networking.WildfireSync;
import com.wildfire.render.debug.GenderDebugHudEntry;
import com.wildfire.render.debug.PhysicsDebugHudEntry;
import com.wildfire.resources.GenderArmorResourceManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Environment(EnvType.CLIENT)
public class WildfireGenderClient implements ClientModInitializer {
	private static final Executor LOAD_EXECUTOR = Util.ioPool().forName("wildfire_gender$loadPlayerData");

	@Override
	public void onInitializeClient() {
		tryImportConfig("FemaleGenderMod", Configuration.CONFIG_DIR);
		tryImportConfig("WildfireGender", Configuration.CONFIG_DIR);
		tryImportConfig("female_gender_mod.json", "female_gender_mod_extended.json");
		tryImportConfig("wildfire_gender.json", "female_gender_mod_extended.json");

		ClientConfig.INSTANCE.load();
		WildfireSounds.register();
		WildfireSync.registerClient();
		WildfireEventHandler.registerClientEvents();
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(GenderArmorResourceManager.ID, GenderArmorResourceManager.INSTANCE);
		DebugScreenEntries.register(GenderDebugHudEntry.SELF, new GenderDebugHudEntry(true));
		DebugScreenEntries.register(GenderDebugHudEntry.OTHER, new GenderDebugHudEntry(false));
		// only register this in dev env, as this likely isn't going to be very useful anywhere else.
		if(FabricLoader.getInstance().isDevelopmentEnvironment()) {
			DebugScreenEntries.register(PhysicsDebugHudEntry.ID, new PhysicsDebugHudEntry());
		}
		WildfireCommand.init();
	}

	private static void tryImportConfig(String oldPath, String newPath) {
		Path oldFile = FabricLoader.getInstance().getConfigDir().resolve(oldPath);
		Path newFile = FabricLoader.getInstance().getConfigDir().resolve(newPath);

		if(Files.notExists(oldFile)) {
			WildfireGender.LOGGER.debug("{} doesn't exist, nothing to migrate", oldPath);
			return;
		}
		if(Files.exists(oldFile) && Files.exists(newFile)) {
			WildfireGender.LOGGER.debug("Skipping config import from {} because {} already exists", oldPath, newPath);
			return;
		}

		try(var paths = Files.walk(oldFile)) {
			for(Path source : paths.toList()) {
				Path destination = newFile.resolve(oldFile.relativize(source));
				if(Files.isDirectory(source)) {
					Files.createDirectories(destination);
				} else {
					Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
			WildfireGender.LOGGER.info("Imported config from {} to '{}'", oldPath, newFile);
		} catch (IOException e) {
			WildfireGender.LOGGER.error("Failed to import config from {}", oldPath, e);
		}
	}

	public static CompletableFuture<@Nullable PlayerConfig> loadGenderInfo(UUID uuid, boolean markForSync, boolean bypassQueue) {
		var cache = WildfireGender.getPlayerById(uuid);
		if(cache == null) {
			return CompletableFuture.completedFuture(null);
		}
		return loadGenderInfo(cache, markForSync, bypassQueue);
	}

	public static CompletableFuture<PlayerConfig> loadGenderInfo(PlayerConfig player, boolean markForSync, boolean bypassQueue) {
		return CompletableFuture.supplyAsync(() -> {
			var uuid = player.uuid;
			if(player.hasLocalConfig()) {
				player.loadFromDisk(markForSync);
			} else if(player.syncStatus == PlayerConfig.SyncStatus.UNKNOWN) {
				JsonObject data;
				try {
					var future = bypassQueue ? CloudSync.getProfile(uuid) : CloudSync.queueFetch(uuid);
					data = future.join();
				} catch(Exception e) {
					WildfireGender.LOGGER.error("Failed to fetch profile from sync server", e);
					throw e;
				}
				// make sure the server we're connected to hasn't provided player data while we were fetching data from
				// the sync server
				if(data != null && player.syncStatus == PlayerConfig.SyncStatus.UNKNOWN) {
					player.updateFromJson(data);
					if(markForSync) {
						player.needsSync = true;
					}
				}
			}
			return player;
		}, LOAD_EXECUTOR);
	}

	public static @Nullable Component getNametag(UUID uuid) {
		var clientPlayer = Minecraft.getInstance().player;
		if(ClientConfig.INSTANCE.get(ClientConfig.HIDE_OWN_CONTRIBUTOR_TAG) && clientPlayer != null && uuid.equals(clientPlayer.getUUID())) {
			return null;
		}

		return Contributors.getNametag(uuid);
	}
}
