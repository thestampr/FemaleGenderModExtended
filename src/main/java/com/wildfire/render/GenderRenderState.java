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

package com.wildfire.render;

import com.wildfire.main.WildfireGenderClient;
import com.wildfire.main.config.enums.Gender;
import com.wildfire.main.entitydata.Breasts;
import com.wildfire.main.entitydata.Butts;
import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.main.uvs.UvEditorFeedback;
import com.wildfire.physics.BodyPhysics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A decoupled render state object that represents a snapshot of a {@link EntityConfig} during a certain frame.
 */
@Environment(EnvType.CLIENT)
public class GenderRenderState {
	private static final RenderStateDataKey<GenderRenderState> STATE = RenderStateDataKey.create(() -> "GenderRenderState");

	public static void update(LivingEntity entity, EntityRenderState state) {
		if(!EntityConfig.isSupportedEntity(entity)) return;
		var config = EntityConfig.getEntityForRendering(entity);
		var genderState = new GenderRenderState(config, entity);
		if(entity instanceof Avatar && !(entity instanceof Player)) {
			genderState.applyMannequinSizes();
		}
		state.setData(STATE, genderState);
	}

	public static @Nullable GenderRenderState get(EntityRenderState state) {
		return state.getData(STATE);
	}

	public void applyPaperDollPhysics(boolean buttPreview, float positionX, float positionY, float rotation,
	                                  float configuredSize) {
		BodyPhysicsState left = buttPreview ? leftButtPhysics : leftBreastPhysics;
		BodyPhysicsState right = buttPreview ? rightButtPhysics : rightBreastPhysics;
		left.setPreview(positionX, positionY, rotation, configuredSize);
		right.setPreview(positionX, positionY, rotation, configuredSize);
	}

	private void applyMannequinSizes() {
		leftBreastPhysics.setPreviewSize(bustSize);
		rightBreastPhysics.setPreviewSize(bustSize);
		leftButtPhysics.setPreviewSize(buttSize);
		rightButtPhysics.setPreviewSize(buttSize);
	}

	public final BreastState breasts;
	public final ButtState butts;
	public final BodyPhysicsState leftBreastPhysics;
	public final BodyPhysicsState rightBreastPhysics;
	public final BodyPhysicsState leftButtPhysics;
	public final BodyPhysicsState rightButtPhysics;

	public final float partialTicks;
	public final float animationSampleTime;
	public final long gameTime;
	public final UUID entityId;
	public final @Nullable Avatar avatar;

	public final Gender gender;
	public final float bustSize;
	public final boolean hasBreastPhysics;
	public final float buttSize;
	public final boolean hasButtPhysics;
	public final boolean realisticModel;
	public final float buttBounceMultiplier;
	public final float buttFloppyMultiplier;
	public final float bounceMultiplier;
	public final float floppyMultiplier;
	public final boolean armorPhysicsOverride;
	public final boolean showBreastsInArmor;
	public final boolean showButtInArmor;
	public final boolean hasJacketLayer;
	public final boolean hasHolidayThemes;

	public UVLayout leftBreastUVLayout;
	public UVLayout rightBreastUVLayout;
	public UVLayout leftBreastOverlayUVLayout;
	public UVLayout rightBreastOverlayUVLayout;
	public UVLayout leftButtUVLayout;
	public UVLayout rightButtUVLayout;
	public UVLayout leftButtOverlayUVLayout;
	public UVLayout rightButtOverlayUVLayout;
	public final UVLayout leftBreastArmorUVLayout;
	public final UVLayout rightBreastArmorUVLayout;

	public final boolean isBreathing;
	public final @Nullable Component nametag;
	public PreviewLayer previewLayer = PreviewLayer.ALL;
	private @Nullable BreastSide previewSelectedSide;
	private @Nullable BreastSide previewHoveredSide;

	public enum PreviewLayer {
		ALL,
		BODY,
		OUTER
	}

	public void applyUvPreviewSelection(BreastSide selectedSide, @Nullable BreastSide hoveredSide) {
		this.previewSelectedSide = selectedSide;
		this.previewHoveredSide = hoveredSide;
	}

	public UvEditorFeedback.State uvPreviewFeedbackState(BreastSide side) {
		if(side == previewSelectedSide) return UvEditorFeedback.State.ACTIVE;
		if(side == previewHoveredSide) return UvEditorFeedback.State.HOVERED;
		return UvEditorFeedback.State.IDLE;
	}

	public void applyBreastUvPreview(UVLayout left, UVLayout right, UVLayout leftOverlay,
	                                UVLayout rightOverlay) {
		this.leftBreastUVLayout = left.copy();
		this.rightBreastUVLayout = right.copy();
		this.leftBreastOverlayUVLayout = leftOverlay.copy();
		this.rightBreastOverlayUVLayout = rightOverlay.copy();
	}

	public void applyButtUvPreview(UVLayout left, UVLayout right, UVLayout leftOverlay,
	                              UVLayout rightOverlay) {
		this.leftButtUVLayout = left.copy();
		this.rightButtUVLayout = right.copy();
		this.leftButtOverlayUVLayout = leftOverlay.copy();
		this.rightButtOverlayUVLayout = rightOverlay.copy();
	}

	private GenderRenderState(EntityConfig entityConfig, LivingEntity entity) {
		this.breasts = new BreastState(entityConfig.getBreasts());
		this.butts = new ButtState(entityConfig.getButts());
		this.leftBreastPhysics = new BodyPhysicsState(entityConfig.getLeftBreastPhysics());
		this.rightBreastPhysics = new BodyPhysicsState(entityConfig.getRightBreastPhysics());
		this.leftButtPhysics = new BodyPhysicsState(entityConfig.getLeftButtPhysics());
		this.rightButtPhysics = new BodyPhysicsState(entityConfig.getRightButtPhysics());

		this.partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		this.animationSampleTime = entity.tickCount + partialTicks;
		this.gameTime = entity.level().getGameTime();
		this.entityId = entity.getUUID();
		this.avatar = entity instanceof Avatar playerLikeEntity ? playerLikeEntity : null;

		this.gender = entityConfig.getGender();
		this.bustSize = entityConfig.getBustSize();
		this.hasBreastPhysics = entityConfig.hasBreastPhysics();
		this.buttSize = entityConfig.getButtSize();
		this.hasButtPhysics = entityConfig.hasButtPhysics();
		this.realisticModel = entityConfig.usesRealisticModel();
		this.buttBounceMultiplier = entityConfig.getButtBounceMultiplier();
		this.buttFloppyMultiplier = entityConfig.getButtFloppiness();
		this.bounceMultiplier = entityConfig.getBounceMultiplier();
		this.floppyMultiplier = entityConfig.getFloppiness();
		this.armorPhysicsOverride = entityConfig.getArmorPhysicsOverride();
		this.showBreastsInArmor = entityConfig.showBreastsInArmor();
		this.showButtInArmor = entityConfig.showButtInArmor();

		if(avatar != null) {
			this.hasJacketLayer = avatar.isModelPartShown(PlayerModelPart.JACKET);
		} else {
			this.hasJacketLayer = entityConfig instanceof PlayerConfig || entityConfig.hasJacketLayer();
		}

		if(entityConfig instanceof PlayerConfig playerConfig) {
			this.hasHolidayThemes = playerConfig.hasHolidayThemes();
		} else {
			this.hasHolidayThemes = false;
		}

		this.leftBreastUVLayout = entityConfig.getLeftBreastUVLayout().copy();
		this.rightBreastUVLayout = entityConfig.getRightBreastUVLayout().copy();
		this.leftBreastOverlayUVLayout = entityConfig.getLeftBreastOverlayUVLayout().copy();
		this.rightBreastOverlayUVLayout = entityConfig.getRightBreastOverlayUVLayout().copy();
		this.leftButtUVLayout = entityConfig.getLeftButtUVLayout().copy();
		this.rightButtUVLayout = entityConfig.getRightButtUVLayout().copy();
		this.leftButtOverlayUVLayout = entityConfig.getLeftButtOverlayUVLayout().copy();
		this.rightButtOverlayUVLayout = entityConfig.getRightButtOverlayUVLayout().copy();
		this.leftBreastArmorUVLayout = entityConfig.getLeftBreastArmorUVLayout().copy();
		this.rightBreastArmorUVLayout = entityConfig.getRightBreastArmorUVLayout().copy();

		this.isBreathing = !entity.isUnderWater() || MobEffectUtil.hasWaterBreathing(entity) ||
			entity.level().getBlockState(entity.blockPosition()).is(Blocks.BUBBLE_COLUMN);
		this.nametag = entity instanceof Player ? WildfireGenderClient.getNametag(entity.getUUID()) : null;
	}

	public static class BreastState {
		public final float xOffset;
		public final float yOffset;
		public final float zOffset;
		public final float cleavage;
		public final boolean uniboob;

		private BreastState(Breasts breasts) {
			this.xOffset = breasts.getXOffset();
			this.yOffset = breasts.getYOffset();
			this.zOffset = breasts.getZOffset();
			this.cleavage = breasts.getCleavage();
			this.uniboob = breasts.isUniboob();
		}
	}

	public static class ButtState {
		public final float xOffset;
		public final float yOffset;
		public final float zOffset;
		public final float cleavage;
		public final boolean linkedPhysics;

		private ButtState(Butts butts) {
			this.xOffset = butts.getXOffset();
			this.yOffset = butts.getYOffset();
			this.zOffset = butts.getZOffset();
			this.cleavage = butts.getCleavage();
			this.linkedPhysics = butts.isLinkedPhysics();
		}
	}

	public class BodyPhysicsState {
		private final float prePositionY, positionY;
		private final float prePositionX, positionX;
		private final float preBounceRotation, bounceRotation;
		private final float previousSize, size;
		private float previewPositionX, previewPositionY, previewRotation;
		private float previewSize = Float.NaN;

		private BodyPhysicsState(BodyPhysics physics) {
			this.prePositionY = physics.getPrePositionY();
			this.positionY = physics.getPositionY();
			this.prePositionX = physics.getPrePositionX();
			this.positionX = physics.getPositionX();
			this.preBounceRotation = physics.getPreBounceRotation();
			this.bounceRotation = physics.getBounceRotation();
			this.previousSize = physics.getPreviousSize();
			this.size = physics.getSize();
		}

		public float getPositionY() {
			return Mth.lerp(partialTicks, this.prePositionY, this.positionY) + previewPositionY;
		}

		public float getPositionX() {
			return Mth.lerp(partialTicks, this.prePositionX, this.positionX) + previewPositionX;
		}

		public float getBounceRotation() {
			return Mth.lerp(partialTicks, this.preBounceRotation, this.bounceRotation) + previewRotation;
		}

		public float getSize() {
			return Float.isNaN(previewSize)
					? Mth.lerp(partialTicks, this.previousSize, this.size)
					: previewSize;
		}

		private void setPreview(float positionX, float positionY, float rotation, float configuredSize) {
			this.previewPositionX = positionX;
			this.previewPositionY = positionY;
			this.previewRotation = rotation;
			this.previewSize = configuredSize;
		}

		private void setPreviewSize(float configuredSize) {
			this.previewSize = configuredSize;
		}
	}
}
