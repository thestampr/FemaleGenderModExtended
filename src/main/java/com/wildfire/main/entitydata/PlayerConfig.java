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

package com.wildfire.main.entitydata;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.wildfire.gui.screen.BaseWildfireScreen;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.WildfireLocalization;
import com.wildfire.main.cloud.CloudSync;
import com.wildfire.main.cloud.SyncLog;
import com.wildfire.main.config.ClientConfig;
import com.wildfire.main.config.Configuration;
import com.wildfire.main.config.enums.Gender;
import com.wildfire.main.config.types.ConfigKey;
import com.wildfire.main.uvs.UVLayout;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A version of {@link EntityConfig} backed by a {@link Configuration} for use with players
 */
public class PlayerConfig extends EntityConfig {
	private static final String UNIFIED_BUTT_UV_VERSION = "unifiedButtUvVersion";
	private static final int CURRENT_BUTT_UV_VERSION = 2;

	/**
	 * <p>{@code true} if this config should be synced to the connected server on the next attempt</p>
	 *
	 * <p>This only has an effect for the client player.</p>
	 */
	public boolean needsSync;

	/**
	 * <p>{@code true} if this config should be synced to the {@link CloudSync cloud sync server} on the next attempt</p>
	 *
	 * <p>This only has an effect for the client player.</p>
	 */
	public boolean needsCloudSync;

	/**
	 * The current sync status of this player config
	 *
	 * @see #needsSync
	 * @see SyncStatus
	 */
	public SyncStatus syncStatus = SyncStatus.UNKNOWN;

	private final Configuration cfg;
	protected boolean hurtSounds = Configuration.HURT_SOUNDS.getDefault();
	protected boolean holidayThemes = Configuration.HOLIDAY_THEMES.getDefault();
	protected boolean showBreastsInArmor = Configuration.SHOW_IN_ARMOR.getDefault();
	protected boolean showButtInArmor = Configuration.SHOW_BUTT_IN_ARMOR.getDefault();

	/**
	 * @deprecated Use {@link #updateGender(Gender)} instead
	 */
	@Deprecated
	public PlayerConfig(UUID uuid, Gender gender) {
		this(uuid);
		updateGender(gender);
	}

	public PlayerConfig(UUID uuid) {
		super(uuid);
		cfg = new Configuration(uuid.toString());
		cfg.setDefaults();

		// Real players always have a UUID of version 4; if this isn't the case, then this is undeniably
		// an NPC player entity.
		if(uuid.version() != 4) holidayThemes = false;
	}

	// these shouldn't ever be called on players, but just to be safe, override with a noop.
	@Override
	public void readFromArmor(ItemStack chestplate, ItemStack leggings) {
	}

	public Configuration getConfig() {
		return cfg;
	}

	public boolean updateGender(Gender value) {
		return updateValue(Configuration.GENDER, value, v -> this.gender = v);
	}

	public boolean updateBustSize(float value) {
		return updateValue(Configuration.BUST_SIZE, value, v -> this.pBustSize = v);
	}

	public boolean updateButtSize(float value) {
		return updateValue(Configuration.BUTT_SIZE, value, v -> this.buttSize = v);
	}


	public boolean hasHolidayThemes() {
		return holidayThemes;
	}

	public boolean updateHolidayThemes(boolean value) {
		return updateValue(Configuration.HOLIDAY_THEMES, value, v -> this.holidayThemes = v);
	}


	public boolean hasHurtSounds() {
		return hurtSounds;
	}

	public boolean updateVoicePitch(float value) {
		return updateValue(Configuration.VOICE_PITCH, value, v -> this.voicePitch = v);
	}

	public boolean updateHurtSounds(boolean value) {
		return updateValue(Configuration.HURT_SOUNDS, value, v -> this.hurtSounds = v);
	}

	public boolean updateBreastPhysics(boolean value) {
		return updateValue(Configuration.BREAST_PHYSICS, value, v -> this.breastPhysics = v);
	}

	public boolean updateButtPhysics(boolean value) {
		return updateValue(Configuration.BUTT_PHYSICS, value, v -> this.buttPhysics = v);
	}

	public boolean updateRealisticModel(boolean value) {
		return updateValue(Configuration.REALISTIC_MODEL, value, v -> this.realisticModel = v);
	}

	public boolean updateHipsEnabled(boolean value) {
		return updateValue(Configuration.HIPS_ENABLED, value, v -> this.hipsEnabled = v);
	}

	/**
	 * @apiNote The value this method returns has been moved to {@link ClientConfig}, and this method is only
	 * 			retained for compatibility with mods that use this as a mixin target.
	 */
	@ApiStatus.Obsolete
	@Environment(EnvType.CLIENT)
	public boolean getArmorPhysicsOverride() {
		return ClientConfig.INSTANCE.get(ClientConfig.ARMOR_PHYSICS_OVERRIDE);
	}

	public boolean showBreastsInArmor() {
		return showBreastsInArmor;
	}

	public boolean updateShowBreastsInArmor(boolean value) {
		return updateValue(Configuration.SHOW_IN_ARMOR, value, v -> this.showBreastsInArmor = v);
	}

	@Override
	public boolean showButtInArmor() {
		return showButtInArmor;
	}

	public boolean updateShowButtInArmor(boolean value) {
		return updateValue(Configuration.SHOW_BUTT_IN_ARMOR, value, v -> showButtInArmor = v);
	}

	public boolean updateBounceMultiplier(float value) {
		return updateValue(Configuration.BOUNCE_MULTIPLIER, value, v -> this.bounceMultiplier = v);
	}

	public boolean updateFloppiness(float value) {
		return updateValue(Configuration.FLOPPY_MULTIPLIER, value, v -> this.floppyMultiplier = v);
	}

	public boolean updateButtBounceMultiplier(float value) {
		return updateValue(Configuration.BUTT_BOUNCE_MULTIPLIER, value, v -> buttBounceMultiplier = v);
	}

	public boolean updateButtFloppiness(float value) {
		return updateValue(Configuration.BUTT_FLOPPY_MULTIPLIER, value, v -> buttFloppyMultiplier = v);
	}

	public SyncStatus getSyncStatus() {
		return this.syncStatus;
	}

	/**
	 * Returns a copy of the player's current configuration; the stored values are guaranteed to be valid for
	 * the associated {@link ConfigKey}, and does not include any unrecognized keys.
	 *
	 * @return A new copy of the player's {@link JsonObject saved config values}
	 */
	public JsonObject toJson() {
		var json = new JsonObject();
		Configuration.KEYS.forEach(key -> key.dump(this, json));
		json.addProperty(UNIFIED_BUTT_UV_VERSION, CURRENT_BUTT_UV_VERSION);
		return json;
	}

	/**
	 * @return {@code true} if the current player {@link Configuration#exists() has a local config file}
	 */
	public boolean hasLocalConfig() {
		return cfg.exists();
	}

	/**
	 * Loads the current player's settings from a file on disk
	 *
	 * @param markForSync {@code true} if {@link #needsSync} should be set to true
	 */
	public void loadFromDisk(boolean markForSync) {
		this.syncStatus = SyncStatus.CACHED;
		cfg.removeParameter(UNIFIED_BUTT_UV_VERSION);
		cfg.load();
		boolean migrateSavedUvs = cfg.exists() && buttUvVersion() < CURRENT_BUTT_UV_VERSION;
		loadFromConfig(markForSync);
		if(migrateSavedUvs) cfg.save();
	}

	/**
	 * Loads the current player's settings from the local {@link Configuration}
	 *
	 * @param markForSync {@code true} if {@link #needsSync} should be set to true
	 */
	public void loadFromConfig(boolean markForSync) {
		migrateSavedButtUvs();
		Configuration.KEYS.forEach(key -> key.writeToPlayer(this));
		if(markForSync) {
			this.needsSync = true;
		}
	}

	private int buttUvVersion() {
		return cfg.get(UNIFIED_BUTT_UV_VERSION) instanceof JsonPrimitive version && version.isNumber()
				? version.getAsInt() : 0;
	}

	private void migrateSavedButtUvs() {
		int version = buttUvVersion();
		if(version >= CURRENT_BUTT_UV_VERSION) return;
		if(version == 1) {
			// Version 1 assigned the rear torso UV halves to the opposite butt meshes.
			UVLayout left = cfg.get(Configuration.LEFT_BUTT_UV_LAYOUT);
			UVLayout right = cfg.get(Configuration.RIGHT_BUTT_UV_LAYOUT);
			UVLayout leftOverlay = cfg.get(Configuration.LEFT_BUTT_OVERLAY_UV_LAYOUT);
			UVLayout rightOverlay = cfg.get(Configuration.RIGHT_BUTT_OVERLAY_UV_LAYOUT);
			cfg.set(Configuration.LEFT_BUTT_UV_LAYOUT, right);
			cfg.set(Configuration.RIGHT_BUTT_UV_LAYOUT, left);
			cfg.set(Configuration.LEFT_BUTT_OVERLAY_UV_LAYOUT, rightOverlay);
			cfg.set(Configuration.RIGHT_BUTT_OVERLAY_UV_LAYOUT, leftOverlay);
		} else {
			// The old split UVs cannot express one continuous face; discard them once.
			cfg.set(Configuration.LEFT_BUTT_UV_LAYOUT, Configuration.LEFT_BUTT_UV_LAYOUT.getDefault());
			cfg.set(Configuration.RIGHT_BUTT_UV_LAYOUT, Configuration.RIGHT_BUTT_UV_LAYOUT.getDefault());
			cfg.set(Configuration.LEFT_BUTT_OVERLAY_UV_LAYOUT, Configuration.LEFT_BUTT_OVERLAY_UV_LAYOUT.getDefault());
			cfg.set(Configuration.RIGHT_BUTT_OVERLAY_UV_LAYOUT, Configuration.RIGHT_BUTT_OVERLAY_UV_LAYOUT.getDefault());
			cfg.removeParameter("leftButtLowerUVLayout");
			cfg.removeParameter("rightButtLowerUVLayout");
			cfg.removeParameter("leftButtLowerOverlayUVLayout");
			cfg.removeParameter("rightButtLowerOverlayUVLayout");
		}
		cfg.set(UNIFIED_BUTT_UV_VERSION, new JsonPrimitive(CURRENT_BUTT_UV_VERSION));
	}

	/**
	 * Write all known {@link ConfigKey}s from this {@link PlayerConfig} to the underlying {@link Configuration}
	 */
	public void writeToConfig() {
		Configuration.KEYS.forEach(key -> key.writeToConfig(this));
	}

	/**
	 * Saves the settings stored in this {@link PlayerConfig} to the underlying {@link Configuration},
	 * and then attempts to {@link Configuration#save() save to disk}.
	 */
	public void save() {
		cfg.set(UNIFIED_BUTT_UV_VERSION, new JsonPrimitive(CURRENT_BUTT_UV_VERSION));
		writeToConfig();
		getConfig().save();
		needsSync = true;
		needsCloudSync = true;
	}

	/**
	 * @deprecated Use {@code plr.save()} instead
	 */
	@Deprecated(forRemoval = true)
	@ApiStatus.ScheduledForRemoval(inVersion = "First release of 26.1")
	public static void saveGenderInfo(PlayerConfig plr) {
		plr.save();
	}

	@Override
	public boolean hasJacketLayer() {
		throw new UnsupportedOperationException("PlayerConfig does not support #hasJacketLayer(); use Player#isModelPartShown instead");
	}

	@ApiStatus.Internal
	public void attemptCloudSync() {
		var client = Minecraft.getInstance();
		if(client.player == null || !this.uuid.equals(client.player.getUUID())) return;
		if(!needsCloudSync) return;
		if(client.screen instanceof BaseWildfireScreen) return;
		if(!ClientConfig.INSTANCE.get(ClientConfig.AUTOMATIC_CLOUD_SYNC)) return;
		if(CloudSync.syncOnCooldown()) return;

		CompletableFuture.runAsync(() -> {
			try {
				CloudSync.sync(this).join();
				WildfireGender.LOGGER.info("Synced player data to the cloud");
			} catch(Exception e) {
				WildfireGender.LOGGER.error("Failed to sync player data", e);
				SyncLog.add(WildfireLocalization.SYNC_LOG_FAILED_TO_SYNC_DATA);
			}
		});
		needsCloudSync = false;
	}

	/**
	 * Update player data from the provided {@link JsonObject}
	 *
	 * @apiNote This method will set the player's {@link #getSyncStatus() sync status} to {@link SyncStatus#SYNCED},
	 *          as it's expected that this method is only used in such cases where this would be applicable.
	 *
	 * @param json The {@link JsonObject} to merge with the existing config for this player
	 */
	public void updateFromJson(JsonObject json) {
		cfg.removeParameter(UNIFIED_BUTT_UV_VERSION);
		json.asMap().forEach(this.cfg::set);
		loadFromConfig(false);
		this.syncStatus = SyncStatus.SYNCED;
	}

	@Override
	public List<String> getDebugInfo() {
		var lines = super.getDebugInfo();
		lines.add(1, "Sync status: " + getSyncStatus());
		lines.add("Female hurt sounds: " + hasHurtSounds());
		lines.add("Show in armor: " + showBreastsInArmor());
		lines.add("Show butt in armor: " + showButtInArmor());
		lines.add("Realistic model: " + usesRealisticModel());
		lines.add("Hips enabled: " + hasHipsEnabled());
		return lines;
	}

	public enum SyncStatus {
		/**
		 * <p>Indicates that the relevant configuration has had its data loaded from a file on disk.</p>
		 *
		 * <p>This is only applicable on a client, as dedicated servers do not read player data from
		 * configuration files.</p>
		 */
		CACHED,

		/**
		 * <p>Indicates that the relevant configuration has had its data loaded from a sync packet,
		 * or from a profile retrieved from {@link CloudSync the cloud sync server}.</p>
		 *
		 * <p>This is currently only set on the client.</p>
		 */
		// TODO this should be set on dedicated servers if/when the player config cache is split
		//		into separate server-sided & client-sided caches
		SYNCED,

		/**
		 * <p>Indicates that this configuration has an unknown sync state.</p>
		 *
		 * <p>This is the default sync state for new configuration instances, and on dedicated servers is
		 * the only sync state.</p>
		 */
		UNKNOWN,
	}
}
