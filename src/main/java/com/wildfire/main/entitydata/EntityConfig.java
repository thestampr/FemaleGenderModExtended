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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.wildfire.api.IGenderArmor;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.config.ClientConfig;
import com.wildfire.main.config.Configuration;
import com.wildfire.main.config.enums.Gender;
import com.wildfire.main.config.types.ConfigKey;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.physics.BreastPhysics;
import com.wildfire.physics.ButtPhysics;
import com.wildfire.physics.animation.CustomAnimationPhysics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * <p>A stripped down version of a {@link PlayerConfig player's config}, intended for use with non-player entities.</p>
 *
 * <p>Unlike players, this has very minimal configuration support.</p>
 *
 * <p>Currently only used for {@link ArmorStand armor stands}, and as a superclass for {@link PlayerConfig player configs}.</p>
 */
public class EntityConfig {

	public static final LoadingCache<UUID, EntityConfig> CACHE = CacheBuilder.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(5))
			.build(CacheLoader.from(EntityConfig::new));

	public final UUID uuid;
	protected Gender gender = Configuration.GENDER.getDefault();
	protected float pBustSize = Configuration.BUST_SIZE.getDefault();
	protected boolean breastPhysics = Configuration.BREAST_PHYSICS.getDefault();
	protected float bounceMultiplier = Configuration.BOUNCE_MULTIPLIER.getDefault();
	protected float floppyMultiplier = Configuration.FLOPPY_MULTIPLIER.getDefault();
	protected float buttSize = Configuration.BUTT_SIZE.getDefault();
	protected boolean buttPhysics = Configuration.BUTT_PHYSICS.getDefault();
	protected float buttBounceMultiplier = Configuration.BUTT_BOUNCE_MULTIPLIER.getDefault();
	protected float buttFloppyMultiplier = Configuration.BUTT_FLOPPY_MULTIPLIER.getDefault();
	protected boolean realisticModel = Configuration.REALISTIC_MODEL.getDefault();
	protected boolean hipsEnabled = Configuration.HIPS_ENABLED.getDefault();

	protected UVLayout leftBreastUVLayout = Configuration.LEFT_BREAST_UV_LAYOUT.getDefault();
	protected UVLayout rightBreastUVLayout = Configuration.RIGHT_BREAST_UV_LAYOUT.getDefault();

	protected UVLayout leftBreastOverlayUVLayout = Configuration.LEFT_BREAST_OVERLAY_UV_LAYOUT.getDefault();
	protected UVLayout rightBreastOverlayUVLayout = Configuration.RIGHT_BREAST_OVERLAY_UV_LAYOUT.getDefault();
	protected UVLayout leftButtUVLayout = Configuration.LEFT_BUTT_UV_LAYOUT.getDefault();
	protected UVLayout rightButtUVLayout = Configuration.RIGHT_BUTT_UV_LAYOUT.getDefault();
	protected UVLayout leftButtOverlayUVLayout = Configuration.LEFT_BUTT_OVERLAY_UV_LAYOUT.getDefault();
	protected UVLayout rightButtOverlayUVLayout = Configuration.RIGHT_BUTT_OVERLAY_UV_LAYOUT.getDefault();

	protected UVLayout leftBreastArmorUVLayout = Configuration.LEFT_BREAST_ARMOR_UV_LAYOUT.getDefault();
	protected UVLayout rightBreastArmorUVLayout = Configuration.RIGHT_BREAST_ARMOR_UV_LAYOUT.getDefault();

	protected float voicePitch = Configuration.VOICE_PITCH.getDefault();

	// note: hurt sounds, armor physics override, and show in armor are not defined here, as they have no relevance
	// to entities, and are instead entirely in PlayerConfig

	// TODO ideally these physics objects would be made entirely client-sided, but this class is
	//      used on both the client and server (primarily through PlayerConfig), making it very
	//      difficult to do so without some major changes to split this up further into a common class
	//      with a client extension class (e.g. the PlayerEntity & AbstractClientPlayerEntity classes)
	protected final BreastPhysics lBreastPhysics, rBreastPhysics;
	protected final Breasts breasts;
	protected final ButtPhysics leftButtPhysics, rightButtPhysics;
	protected final Butts butts;
	protected boolean jacketLayer = true;
	protected @Nullable BreastDataComponent fromChestComponent;
	protected @Nullable BreastDataComponent fromLeggingsComponent;
	private boolean armorDataInitialized;
	private boolean bodyPhysicsSuppressed;

	@ApiStatus.Internal
	public boolean forceSimplifiedPhysics = false;

	protected EntityConfig(UUID uuid) {
		this.uuid = uuid;
		this.breasts = new Breasts();
		this.butts = new Butts();
		lBreastPhysics = new BreastPhysics(this);
		rBreastPhysics = new BreastPhysics(this);
		leftButtPhysics = new ButtPhysics(this);
		rightButtPhysics = new ButtPhysics(this);
	}

	/**
	 * Copy body settings included in the armor NBT to the current entity.
	 *
	 * @see BreastDataComponent
	 */
	public void readFromArmor(ItemStack chestplate, ItemStack leggings) {
		CustomData chestData = chestplate.get(DataComponents.CUSTOM_DATA);
		CustomData leggingsData = leggings.get(DataComponents.CUSTOM_DATA);
		if(armorDataInitialized && matchesCachedComponent(chestData, fromChestComponent)
				&& matchesCachedComponent(leggingsData, fromLeggingsComponent)) return;
		armorDataInitialized = true;

		fromChestComponent = BreastDataComponent.fromComponent(chestData);
		fromLeggingsComponent = BreastDataComponent.fromComponent(leggingsData);

		if(fromChestComponent == null) {
			pBustSize = 0;
			breastPhysics = false;
			bounceMultiplier = Configuration.BOUNCE_MULTIPLIER.getDefault();
			floppyMultiplier = Configuration.FLOPPY_MULTIPLIER.getDefault();
			breasts.copyFrom(new Breasts());
		} else {
			pBustSize = fromChestComponent.breastSize();
			breastPhysics = fromChestComponent.breastPhysics().enabled();
			bounceMultiplier = fromChestComponent.breastPhysics().intensity();
			floppyMultiplier = fromChestComponent.breastPhysics().momentum();
			breasts.updateCleavage(fromChestComponent.breastCleavage());
			breasts.updateOffsets(fromChestComponent.breastOffsets());
		}

		if(fromLeggingsComponent == null) {
			buttSize = 0;
			buttPhysics = false;
			buttBounceMultiplier = Configuration.BUTT_BOUNCE_MULTIPLIER.getDefault();
			buttFloppyMultiplier = Configuration.BUTT_FLOPPY_MULTIPLIER.getDefault();
			butts.copyFrom(new Butts());
		} else {
			buttSize = fromLeggingsComponent.buttSize();
			buttPhysics = fromLeggingsComponent.buttPhysics().enabled();
			buttBounceMultiplier = fromLeggingsComponent.buttPhysics().intensity();
			buttFloppyMultiplier = fromLeggingsComponent.buttPhysics().momentum();
			butts.updateCleavage(fromLeggingsComponent.buttCleavage());
			butts.updateXOffset(fromLeggingsComponent.buttOffsets().x);
			butts.updateYOffset(fromLeggingsComponent.buttOffsets().y);
			butts.updateZOffset(fromLeggingsComponent.buttOffsets().z);
		}

		// Armor stands do not have a profile gender of their own. A body setting copied to
		// either armor slot is therefore the authoritative signal for both body-part layers.
		gender = pBustSize >= 0.02f || buttSize >= 0.02f ? Gender.FEMALE : Gender.MALE;

		BreastDataComponent appearance = fromLeggingsComponent != null ? fromLeggingsComponent : fromChestComponent;
		realisticModel = appearance != null && appearance.realisticModel();
		jacketLayer = appearance == null || appearance.jacket();
	}

	private static boolean matchesCachedComponent(@Nullable CustomData data,
	                                              @Nullable BreastDataComponent cached) {
		return data == null ? cached == null : cached != null && Objects.equals(data, cached.nbtComponent());
	}

	/**
	 * @return {@code true} if the mod has support for the provided entity
	 */
	public static boolean isSupportedEntity(LivingEntity entity) {
		// TODO mannequins are not properly supported right now; this method only returns true to indicate that
		//		our rendering does technically support it, despite the fact that there is no way to properly utilize
		//		them without using janky workarounds.
		return entity instanceof Avatar || entity instanceof ArmorStand;
	}

	/**
	 * Get the configuration for a given entity
	 *
	 * @apiNote Configuration settings for {@link PlayerConfig}s may not be immediately available upon being
	 *          returned, and may take several seconds to be populated if loaded from the
	 *          {@link com.wildfire.main.cloud.CloudSync cloud sync server}.
	 *
	 * @return The relevant {@link EntityConfig}, or {@link PlayerConfig} if given a {@link Player player}
	 */
	public static EntityConfig getEntity(LivingEntity entity) {
		if(entity instanceof Player) {
			return WildfireGender.getOrAddPlayerById(entity.getUUID());
		}
		return CACHE.getUnchecked(entity.getUUID());
	}

	/**
	 * Resolves the profile snapshot used by entity renderers, including player-like mannequins
	 * created by inventory and paper-doll screens.
	 */
	@Environment(EnvType.CLIENT)
	public static EntityConfig getEntityForRendering(LivingEntity entity) {
		if(entity instanceof Player) return getEntity(entity);
		if(entity instanceof Avatar) {
			// Other mods commonly create a mannequin with the displayed player's UUID. Prefer an
			// already-loaded player profile so vanilla-style paper dolls receive the same body state.
			PlayerConfig playerConfig = WildfireGender.getPlayerById(entity.getUUID());
			if(playerConfig != null) return playerConfig;
			// Our own GUI mannequins install an isolated profile when no shared player profile exists.
			EntityConfig mannequinConfig = CACHE.getIfPresent(entity.getUUID());
			if(mannequinConfig != null) return mannequinConfig;
		}
		return getEntity(entity);
	}

	public Gender getGender() {
		return gender;
	}

	public Breasts getBreasts() {
		return breasts;
	}

	public Butts getButts() {
		return butts;
	}

	public float getBustSize() {
		return pBustSize;
	}

	public float getButtSize() {
		return buttSize;
	}

	public boolean hasBreastPhysics() {
		return breastPhysics && !bodyPhysicsSuppressed;
	}

	public boolean hasButtPhysics() {
		return buttPhysics && !bodyPhysicsSuppressed;
	}

	public boolean usesRealisticModel() {
		return realisticModel;
	}

	public boolean hasHipsEnabled() {
		return hipsEnabled;
	}

	/**
	 * @apiNote See {@link PlayerConfig#getArmorPhysicsOverride()} for the reasoning behind this being {@link ApiStatus.Obsolete @Obsolete}
	 */
	@ApiStatus.Obsolete
	@Environment(EnvType.CLIENT)
	public boolean getArmorPhysicsOverride() {
		return ClientConfig.INSTANCE.get(ClientConfig.ARMOR_PHYSICS_OVERRIDE);
	}

	public boolean showBreastsInArmor() {
		return true;
	}

	public boolean showButtInArmor() {
		return true;
	}

	public float getBounceMultiplier() {
		return bounceMultiplier;
	}

	public float getFloppiness() {
		return this.floppyMultiplier;
	}

	public float getButtBounceMultiplier() {
		return buttBounceMultiplier;
	}

	public float getButtFloppiness() {
		return buttFloppyMultiplier;
	}

	public float getVoicePitch() {
		return this.voicePitch;
	}

	public BreastPhysics getLeftBreastPhysics() {
		return lBreastPhysics;
	}
	public BreastPhysics getRightBreastPhysics() {
		return rBreastPhysics;
	}

	public ButtPhysics getLeftButtPhysics() {
		return leftButtPhysics;
	}

	public ButtPhysics getRightButtPhysics() {
		return rightButtPhysics;
	}

	// FIXME these update methods should match the rest and be in PlayerConfig instead of here
	// FIXME this should really be redesigned to not have multiple methods with very similar names;
	//		ideally something like `getUVs().skin().left()` etc.
	public UVLayout getLeftBreastUVLayout() {
		return this.leftBreastUVLayout;
	}

	public boolean updateLeftBreastUVLayout(UVLayout layout) {
		return updateValue(Configuration.LEFT_BREAST_UV_LAYOUT, layout, v -> this.leftBreastUVLayout = v);
	}

	public UVLayout getRightBreastUVLayout() {
		return this.rightBreastUVLayout;
	}

	public boolean updateRightBreastUVLayout(UVLayout layout) {
		return updateValue(Configuration.RIGHT_BREAST_UV_LAYOUT, layout, v -> this.rightBreastUVLayout = v);
	}

	public UVLayout getLeftBreastOverlayUVLayout() {
		return this.leftBreastOverlayUVLayout;
	}

	public boolean updateLeftBreastOverlayUVLayout(UVLayout layout) {
		return updateValue(Configuration.LEFT_BREAST_OVERLAY_UV_LAYOUT, layout, v -> this.leftBreastOverlayUVLayout = v);
	}

	public UVLayout getRightBreastOverlayUVLayout() {
		return this.rightBreastOverlayUVLayout;
	}

	public boolean updateRightBreastOverlayUVLayout(UVLayout layout) {
		return updateValue(Configuration.RIGHT_BREAST_OVERLAY_UV_LAYOUT, layout, v -> this.rightBreastOverlayUVLayout = v);
	}

	public UVLayout getLeftButtUVLayout() {
		return leftButtUVLayout;
	}

	public boolean updateLeftButtUVLayout(UVLayout layout) {
		return updateValue(Configuration.LEFT_BUTT_UV_LAYOUT, layout, v -> leftButtUVLayout = v);
	}

	public UVLayout getRightButtUVLayout() {
		return rightButtUVLayout;
	}

	public boolean updateRightButtUVLayout(UVLayout layout) {
		return updateValue(Configuration.RIGHT_BUTT_UV_LAYOUT, layout, v -> rightButtUVLayout = v);
	}

	public UVLayout getLeftButtOverlayUVLayout() {
		return leftButtOverlayUVLayout;
	}

	public boolean updateLeftButtOverlayUVLayout(UVLayout layout) {
		return updateValue(Configuration.LEFT_BUTT_OVERLAY_UV_LAYOUT, layout, v -> leftButtOverlayUVLayout = v);
	}

	public UVLayout getRightButtOverlayUVLayout() {
		return rightButtOverlayUVLayout;
	}

	public boolean updateRightButtOverlayUVLayout(UVLayout layout) {
		return updateValue(Configuration.RIGHT_BUTT_OVERLAY_UV_LAYOUT, layout, v -> rightButtOverlayUVLayout = v);
	}

	@Deprecated(forRemoval = true)
	public UVLayout getLeftBreastArmorUVLayout() {
		return this.leftBreastArmorUVLayout;
	}

	@Deprecated(forRemoval = true)
	public UVLayout getRightBreastArmorUVLayout() {
		return this.rightBreastArmorUVLayout;
	}

	protected <VALUE> boolean updateValue(ConfigKey<VALUE> key, VALUE value, Consumer<VALUE> setter) {
		if (key.validate(value)) {
			setter.accept(value);
			return true;
		}
		return false;
	}

	/**
	 * Only used in the case of {@link ArmorStand armor stands}; returns {@code true} if the player who equipped
	 * the armor stand's chestplate has their jacket layer visible.
	 */
	public boolean hasJacketLayer() {
		return jacketLayer;
	}

	@Environment(EnvType.CLIENT)
	public void tickBodyPhysics(LivingEntity entity) {
		IGenderArmor chestArmor = WildfireHelper.getArmorConfig(entity.getItemBySlot(EquipmentSlot.CHEST));
		IGenderArmor leggings = WildfireHelper.getArmorConfig(entity.getItemBySlot(EquipmentSlot.LEGS));
		bodyPhysicsSuppressed = entity instanceof ArmorStand
				&& !ClientConfig.INSTANCE.get(ClientConfig.ARMOR_STAND_PHYSICS);
		if(bodyPhysicsSuppressed) {
			getLeftBreastPhysics().updateSimplified(chestArmor);
			getRightBreastPhysics().updateSimplified(chestArmor);
			getLeftButtPhysics().updateSimplified(leggings);
			getRightButtPhysics().updateSimplified(leggings);
			return;
		}

		var animationMotion = CustomAnimationPhysics.consume(uuid, entity.level().getGameTime());

		if(chestArmor.coversBreasts() && !getArmorPhysicsOverride()) {
			// Armor Physics off: discard accumulated motion instead of letting the armor
			// keep an invisible spring running behind the static rendered shell.
			getLeftBreastPhysics().updateSimplified(chestArmor);
			getRightBreastPhysics().updateSimplified(chestArmor);
		} else {
			getLeftBreastPhysics().update(entity, chestArmor, animationMotion.breasts());
			getRightBreastPhysics().update(entity, chestArmor, animationMotion.breasts());
		}
		if(usesRealisticModel()) {
			getLeftButtPhysics().update(entity, leggings, animationMotion.realisticButt());
			getRightButtPhysics().update(entity, leggings, animationMotion.realisticButt());
		} else {
			getLeftButtPhysics().update(entity, leggings, animationMotion.leftClassicButt());
			getRightButtPhysics().update(entity, leggings, animationMotion.rightClassicButt());
		}
	}

	@Override
	public String toString() {
		return "%s(uuid=%s, gender=%s)".formatted(getClass().getCanonicalName(), uuid, gender);
	}

	public List<String> getDebugInfo() {
		List<String> info = new ArrayList<>();

		info.add("Gender: " + switch(getGender()) {
			case FEMALE -> ChatFormatting.LIGHT_PURPLE + "Female";
			case MALE -> ChatFormatting.BLUE + "Male";
			case OTHER -> ChatFormatting.GREEN + "Other";
		});
		info.add("Breast size: " + getBustSize());
		info.add("Physics enabled: " + hasBreastPhysics());
		var breasts = getBreasts();
		info.add("Uniboob: " + breasts.isUniboob());
		info.add("Cleavage: " + breasts.getCleavage());
		info.add("Offsets: (" + breasts.getXOffset() + ", " + breasts.getYOffset() + ", " + breasts.getZOffset() + ")");
		info.add("Butt size: " + getButtSize());
		info.add("Butt physics enabled: " + hasButtPhysics());

		return info;
	}
}
