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

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.config.Configuration;
import com.wildfire.main.config.types.FloatConfigKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * <p>Data component-like class for storing player body settings on armor equipped onto armor stands.</p>
 *
 * <p>Note that while this is treated similarly to any other {@link DataComponents data component} for performance reasons,
 * this is never written as its own component on item stacks, but instead uses the {@link DataComponents#CUSTOM_DATA custom NBT data component}
 * (under the {@code WildfireGender} key) for compatibility with vanilla clients on servers.</p>
 */
public record BreastDataComponent(float breastSize, float breastCleavage, Vector3f breastOffsets,
		ArmorStandPhysics breastPhysics, float buttSize, float buttCleavage, Vector3f buttOffsets,
		ArmorStandPhysics buttPhysics, boolean realisticModel, boolean jacket,
		@Nullable CustomData nbtComponent) {

	private static final String KEY = "WildfireGender";
	private static final ArmorStandPhysics DISABLED_BREAST_PHYSICS = new ArmorStandPhysics(false,
			Configuration.BOUNCE_MULTIPLIER.getDefault(), Configuration.FLOPPY_MULTIPLIER.getDefault());
	private static final ArmorStandPhysics DISABLED_BUTT_PHYSICS = new ArmorStandPhysics(false,
			Configuration.BUTT_BOUNCE_MULTIPLIER.getDefault(), Configuration.BUTT_FLOPPY_MULTIPLIER.getDefault());
	private static final Codec<BreastDataComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			WildfireHelper.boundedFloat(Configuration.BUST_SIZE)
					.optionalFieldOf("BreastSize", 0f)
					.forGetter(BreastDataComponent::breastSize),
			WildfireHelper.boundedFloat(Configuration.BREASTS_CLEAVAGE)
					.optionalFieldOf("Cleavage", Configuration.BREASTS_CLEAVAGE.getDefault())
					.forGetter(BreastDataComponent::breastCleavage),
			Codec.BOOL
					.optionalFieldOf("Jacket", true)
					.forGetter(BreastDataComponent::jacket),
			WildfireHelper.boundedFloat(Configuration.BREASTS_OFFSET_X)
					.optionalFieldOf("XOffset", 0f)
					.forGetter(component -> component.breastOffsets.x),
			WildfireHelper.boundedFloat(Configuration.BREASTS_OFFSET_Y)
					.optionalFieldOf("YOffset", 0f)
					.forGetter(component -> component.breastOffsets.y),
			WildfireHelper.boundedFloat(Configuration.BREASTS_OFFSET_Z)
					.optionalFieldOf("ZOffset", 0f)
					.forGetter(component -> component.breastOffsets.z),
			ArmorStandPhysics.BREAST_CODEC
					.optionalFieldOf("BreastPhysics", DISABLED_BREAST_PHYSICS)
					.forGetter(BreastDataComponent::breastPhysics),
			WildfireHelper.boundedFloat(Configuration.BUTT_SIZE)
					.optionalFieldOf("ButtSize", 0f)
					.forGetter(BreastDataComponent::buttSize),
			WildfireHelper.boundedFloat(Configuration.BUTTS_CLEAVAGE)
					.optionalFieldOf("ButtCleavage", Configuration.BUTTS_CLEAVAGE.getDefault())
					.forGetter(BreastDataComponent::buttCleavage),
			WildfireHelper.boundedFloat(Configuration.BUTTS_OFFSET_X)
					.optionalFieldOf("ButtXOffset", Configuration.BUTTS_OFFSET_X.getDefault())
					.forGetter(component -> component.buttOffsets.x),
			WildfireHelper.boundedFloat(Configuration.BUTTS_OFFSET_Y)
					.optionalFieldOf("ButtYOffset", Configuration.BUTTS_OFFSET_Y.getDefault())
					.forGetter(component -> component.buttOffsets.y),
			WildfireHelper.boundedFloat(Configuration.BUTTS_OFFSET_Z)
					.optionalFieldOf("ButtZOffset", Configuration.BUTTS_OFFSET_Z.getDefault())
					.forGetter(component -> component.buttOffsets.z),
			ArmorStandPhysics.BUTT_CODEC
					.optionalFieldOf("ButtPhysics", DISABLED_BUTT_PHYSICS)
					.forGetter(BreastDataComponent::buttPhysics),
			Codec.BOOL
					.optionalFieldOf("RealisticModel", Configuration.REALISTIC_MODEL.getDefault())
					.forGetter(BreastDataComponent::realisticModel)
		).apply(instance, (breastSize, breastCleavage, jacket, breastX, breastY, breastZ, breastPhysics,
				buttSize, buttCleavage, buttX, buttY, buttZ, buttPhysics, realisticModel) -> new BreastDataComponent(
				breastSize, breastCleavage, new Vector3f(breastX, breastY, breastZ),
				breastPhysics, buttSize, buttCleavage, new Vector3f(buttX, buttY, buttZ),
				buttPhysics, realisticModel, jacket, null))
	);

	public static @Nullable BreastDataComponent fromPlayer(@NotNull Player player, @NotNull PlayerConfig config,
	                                                      EquipmentSlot slot) {
		boolean displaysBodyParts = config.getGender().displaysGenderedBodyParts();
		boolean copiesBreasts = slot == EquipmentSlot.CHEST
				&& displaysBodyParts && config.showBreastsInArmor();
		boolean copiesButt = slot == EquipmentSlot.LEGS
				&& displaysBodyParts && config.getButtSize() >= 0.02f && config.showButtInArmor();
		if(!copiesBreasts && !copiesButt) return null;

		return new BreastDataComponent(
				copiesBreasts ? config.getBustSize() : 0,
				config.getBreasts().getCleavage(), config.getBreasts().getOffsets(),
				copiesBreasts ? ArmorStandPhysics.forBreasts(config) : DISABLED_BREAST_PHYSICS,
				copiesButt ? config.getButtSize() : 0,
				config.getButts().getCleavage(), config.getButts().getOffsets(),
				copiesButt ? ArmorStandPhysics.forButt(config) : DISABLED_BUTT_PHYSICS,
				config.usesRealisticModel(), player.isModelPartShown(PlayerModelPart.JACKET), null);
	}

	public static @Nullable BreastDataComponent fromComponent(@Nullable CustomData component) {
		if(component == null) {
			return null;
		}

		return CODEC.decode(NbtOps.INSTANCE, component.copyTag().getCompoundOrEmpty(KEY))
				.result()
				.map(Pair::getFirst)
				.map(breastDataComponent -> breastDataComponent.withComponent(component))
				.orElse(null);
	}

	public void write(ItemStack stack) {
		if(stack.isEmpty()) {
			throw new IllegalArgumentException("The provided ItemStack must not be empty");
		}

		CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.store(KEY, CODEC, this));
	}

	public static void removeFromStack(ItemStack stack) {
		if(stack.isEmpty()) return;
		CustomData component = stack.get(DataComponents.CUSTOM_DATA);
		if(component != null && component.copyTag().contains(KEY)) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, nbt -> nbt.remove(KEY));
		}
	}

	private BreastDataComponent withComponent(CustomData component) {
		return new BreastDataComponent(breastSize, breastCleavage, breastOffsets,
				breastPhysics, buttSize, buttCleavage, buttOffsets, buttPhysics,
				realisticModel, jacket, component);
	}

	public record ArmorStandPhysics(boolean enabled, float intensity, float momentum) {
		private static Codec<ArmorStandPhysics> codec(FloatConfigKey intensityKey,
		                                               FloatConfigKey momentumKey) {
			return RecordCodecBuilder.create(instance -> instance.group(
					Codec.BOOL.optionalFieldOf("Enabled", false).forGetter(ArmorStandPhysics::enabled),
					WildfireHelper.boundedFloat(intensityKey).optionalFieldOf("Intensity", intensityKey.getDefault())
							.forGetter(ArmorStandPhysics::intensity),
					WildfireHelper.boundedFloat(momentumKey).optionalFieldOf("Momentum", momentumKey.getDefault())
							.forGetter(ArmorStandPhysics::momentum)
			).apply(instance, ArmorStandPhysics::new));
		}

		private static final Codec<ArmorStandPhysics> BREAST_CODEC = codec(
				Configuration.BOUNCE_MULTIPLIER, Configuration.FLOPPY_MULTIPLIER);
		private static final Codec<ArmorStandPhysics> BUTT_CODEC = codec(
				Configuration.BUTT_BOUNCE_MULTIPLIER, Configuration.BUTT_FLOPPY_MULTIPLIER);

		private static ArmorStandPhysics forBreasts(PlayerConfig config) {
			return new ArmorStandPhysics(config.hasBreastPhysics(), config.getBounceMultiplier(), config.getFloppiness());
		}

		private static ArmorStandPhysics forButt(PlayerConfig config) {
			return new ArmorStandPhysics(config.hasButtPhysics(), config.getButtBounceMultiplier(),
					config.getButtFloppiness());
		}
	}
}
