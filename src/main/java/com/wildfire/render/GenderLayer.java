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

import com.mojang.blaze3d.vertex.PoseStack;
import com.wildfire.api.IGenderArmor;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.config.ClientConfig;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.main.uvs.UVQuad;
import com.wildfire.render.WildfireModelRenderer.BreastModelBox;
import com.wildfire.render.WildfireModelRenderer.OverlayModelBox;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.joml.Quaternionf;

import java.lang.Math;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public class GenderLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends BodyPartLayer<S, M> {

	private static final float DEG_TO_RAD = (float) (Math.PI / 180);
	private static final float DISPLAYED_FULL_SIZE = 0.8f;

	@UnknownNullability("null until #resizeBox() is first called")
	private BreastModelBox lBreast, rBreast;
	@UnknownNullability("null until #resizeBox() is first called")
	private OverlayModelBox lBreastWear, rBreastWear;

	private @Nullable UVLayout prevLeftBreastUVLayout, prevRightBreastUVLayout,
		prevLeftBreastOverlayUVLayout, prevRightBreastOverlayUVLayout;
	private boolean previousRealisticModel;

	private boolean isUniboob;
	// although ItemStack instances are mutable, this is safe to keep a reference to as this is a copy of the real stack
	protected ItemStack armorStack = ItemStack.EMPTY;
	protected IGenderArmor genderArmor = IGenderArmor.EMPTY;
	protected boolean isChestplateOccupied, bounceEnabled, breathingAnimation, cacheStaticDeformation;
	protected float breastOffsetX, breastOffsetY, breastOffsetZ, lPhysPositionY, lPhysPositionX, rPhysPositionY, rPhysPositionX,
			lPhysBounceRotation, rPhysBounceRotation, breastSize, zOffset, outwardAngle, physicsIntensity, physicsMomentum,
			breathingRotation, configuredBreastSize;
	protected boolean realisticModel;

	public GenderLayer(RenderLayerParent<S, M> render) {
		super(render);
	}

	@Override
	public void submit(PoseStack matrixStack, SubmitNodeCollector queue, int light, S state, float limbAngle, float limbDistance) {
		var entityConfigState = GenderRenderState.get(state);
		if(entityConfigState == null) return;

		try {
			if(!setupRender(state, entityConfigState)) return;
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);

			//noinspection CodeBlock2Expr
			renderSides(state, getParentModel(), matrixStack, side -> {
				renderBreast(state, matrixStack, queue, overlay, side);
			});
		} catch(Exception e) {
			WildfireGender.LOGGER.error("Failed to render breast layer", e);
		}
	}

	/**
	 * Common logic for setting up breast rendering
	 *
	 * @return {@code true} if rendering should continue
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	protected boolean setupRender(S entityState, GenderRenderState genderState) {
		if(!ClientConfig.RENDER_BREASTS) return false;
		if(!genderState.gender.displaysGenderedBodyParts()) return false;

		armorStack = entityState.chestEquipment;
		//Note: When the stack is empty the helper will fall back to an implementation that returns the proper data
		genderArmor = WildfireHelper.getArmorConfig(armorStack);
		boolean armorCoversBreasts = !armorStack.isEmpty() && genderArmor.coversBreasts();
		isChestplateOccupied = armorCoversBreasts && !genderState.armorPhysicsOverride;
		if(genderArmor.alwaysHidesBreasts() || !genderState.showBreastsInArmor && armorCoversBreasts) {
			//If the armor always hides breasts or there is armor and the player configured breasts
			// to be hidden when wearing armor, we can just exit early rather than doing any calculations
			return false;
		}

		if(!isLayerVisible(entityState)) {
			return false;
		}

		GenderRenderState.BreastState breasts = genderState.breasts;
		breastOffsetX = WildfireHelper.round(breasts.xOffset, 1);
		breastOffsetY = -WildfireHelper.round(breasts.yOffset, 1);
		breastOffsetZ = -WildfireHelper.round(breasts.zOffset, 1);

		isUniboob = breasts.uniboob;
		realisticModel = genderState.realisticModel;
		physicsIntensity = genderState.bounceMultiplier;
		physicsMomentum = genderState.floppyMultiplier;

		GenderRenderState.BodyPhysicsState leftPhysicsState = genderState.leftBreastPhysics;
		final float bSize = leftPhysicsState.getSize();
		configuredBreastSize = bSize;
		outwardAngle = Math.round(breasts.cleavage * 100f);
		outwardAngle = Math.min(outwardAngle, 10);

		resizeBox(genderState, bSize);

		lPhysPositionY = leftPhysicsState.getPositionY();
		lPhysPositionX = leftPhysicsState.getPositionX();
		lPhysBounceRotation = leftPhysicsState.getBounceRotation();
		if(isUniboob) {
			rPhysPositionY = lPhysPositionY;
			rPhysPositionX = lPhysPositionX;
			rPhysBounceRotation = lPhysBounceRotation;
		} else {
			GenderRenderState.BodyPhysicsState rightPhysicsState = genderState.rightBreastPhysics;
			rPhysPositionY = rightPhysicsState.getPositionY();
			rPhysPositionX = rightPhysicsState.getPositionX();
			rPhysBounceRotation = rightPhysicsState.getBounceRotation();
		}

		float poseSize = Math.min(bSize, DISPLAYED_FULL_SIZE);
		breastSize = Math.min(poseSize * 1.5f, 0.7f); // Limit the max size to 0.7f

		if (poseSize > 0.7f) {
			breastSize = poseSize; // If poseSize exceeds 0.7f, use poseSize
		}

		if (breastSize < 0.02f) {
			return false; // Return false if breastSize is too small
		}

		// The legacy pose reaches its intended maximum at the displayed 100%. Larger values
		// grow the geometry below instead of continuing to push the attachment point around.
		zOffset = 0.0625f - (poseSize * 0.0625f);
		breastSize += 0.5f * Math.abs(poseSize - 0.7f) * 2f;

		boolean physicsAllowed = !armorCoversBreasts || genderState.armorPhysicsOverride;
		breathingAnimation = physicsAllowed && genderState.isBreathing;
		breathingRotation = breathingAnimation
				? -Mth.cos(entityState.ageInTicks * 0.09F) * 0.45F + 0.45F
				: 0;
		bounceEnabled = genderState.hasBreastPhysics && physicsAllowed;
		cacheStaticDeformation = entityState instanceof ArmorStandRenderState
				&& !bounceEnabled && !breathingAnimation;
		return true;
	}

	protected void resizeBox(GenderRenderState state, float breastSize) {
		//TODO: Better way for this?
		if(!Objects.equals(this.prevLeftBreastUVLayout, state.leftBreastUVLayout)
				|| !Objects.equals(this.prevRightBreastUVLayout, state.rightBreastUVLayout)
				|| !Objects.equals(this.prevLeftBreastOverlayUVLayout, state.leftBreastOverlayUVLayout)
				|| !Objects.equals(this.prevRightBreastOverlayUVLayout, state.rightBreastOverlayUVLayout)
				|| previousRealisticModel != state.realisticModel) {

			this.prevLeftBreastUVLayout = state.leftBreastUVLayout;
			this.prevRightBreastUVLayout = state.rightBreastUVLayout;
			this.prevLeftBreastOverlayUVLayout = state.leftBreastOverlayUVLayout;
			this.prevRightBreastOverlayUVLayout = state.rightBreastOverlayUVLayout;
			this.previousRealisticModel = state.realisticModel;
			UVLayout leftSkin = displayedBreastLayout(state.leftBreastUVLayout, state.realisticModel);
			UVLayout rightSkin = displayedBreastLayout(state.rightBreastUVLayout, state.realisticModel);
			UVLayout leftOverlay = displayedBreastLayout(state.leftBreastOverlayUVLayout, state.realisticModel);
			UVLayout rightOverlay = displayedBreastLayout(state.rightBreastOverlayUVLayout, state.realisticModel);

			this.lBreast = new BreastModelBox(64, 64, -4F, 0.0F, 0F, 4, 5, 3, 0.0F,
					leftSkin, state.realisticModel);
			this.rBreast = new BreastModelBox(64, 64, 0F, 0.0F, 0F, 4, 5, 3, 0.0F,
					rightSkin, state.realisticModel);
			this.lBreastWear = new OverlayModelBox(64, 64, -4F, 0.0F, 0F, 4, 5, 3, 0.0F,
					leftOverlay, state.realisticModel);
			this.rBreastWear = new OverlayModelBox(64, 64, 0, 0.0F, 0F, 4, 5, 3, 0.0F,
					rightOverlay, state.realisticModel);
		}
	}

	private static UVLayout displayedBreastLayout(UVLayout source, boolean realistic) {
		if(!realistic) return source;

		// Move the displayed texture upward by sampling one row lower on the skin. Shift the
		// complete connected shell together so side/top/bottom seams remain continuous.
		UVLayout result = source.copy();
		for(var entry : source.getQuads().entrySet()) {
			var quad = entry.getValue();
			result.put(entry.getKey(), new UVQuad(
					quad.x1(), quad.y1() + 1, quad.x2(), quad.y2() + 1));
		}
		return result;
	}

	protected void setupTransformations(S state, M model, PoseStack matrixStack, BreastSide side) {
		if(state.isBaby) {
			matrixStack.scale(state.ageScale, state.ageScale, state.ageScale);
			matrixStack.translate(0f, 0.75f, 0f);
		}

		applyTorsoTransform(model, matrixStack);

		if(bounceEnabled && !realisticModel) {
			matrixStack.translate((side.isLeft ? lPhysPositionX : rPhysPositionX) / 32f, 0, 0);
			matrixStack.translate(0, (side.isLeft ? lPhysPositionY : rPhysPositionY) / 32f, 0);
		}

		float configuredX = realisticModel ? 0 : (side.isLeft ? -breastOffsetX : breastOffsetX) * 0.0625f;
		float configuredY = realisticModel ? 0 : breastOffsetY * 0.0625f;
		float configuredZ = realisticModel ? 0 : breastOffsetZ * 0.0425f;
		float realisticLift = realisticModel ? 0.5f / 16f : 0;
		matrixStack.translate(configuredX, 0.05625f + configuredY - realisticLift,
				zOffset - 0.0625f * 2f + configuredZ); //shift down to correct position

		if(!isUniboob) {
			matrixStack.translate(-0.0625f * 2 * (side.isLeft ? 1 : -1), 0, 0);
		}
		if(bounceEnabled && !realisticModel) {
			matrixStack.mulPose(new Quaternionf().rotationXYZ(0, (float)((side.isLeft ? lPhysBounceRotation : rPhysBounceRotation) * (Math.PI / 180f)), 0));
		}
		if(!isUniboob) {
			matrixStack.translate(0.0625f * 2 * (side.isLeft ? 1 : -1), 0, 0);
		}

		float rotation = breastSize;
		if(bounceEnabled && !realisticModel) {
			matrixStack.translate(0, -0.035f * breastSize, 0); //shift down to correct position
			rotation -= (side.isLeft ? lPhysPositionY : rPhysPositionY) / 12f;
		}

		rotation = Math.min(rotation, breastSize + 0.2f);
		rotation = Math.min(rotation, 1); //hard limit for MAX

		if(isChestplateOccupied) {
			matrixStack.translate(0, 0, 0.01f);
		}

		if(!realisticModel) {
			Quaternionf rotationTransform = new Quaternionf()
					.rotationY((side.isLeft ? outwardAngle : -outwardAngle) * DEG_TO_RAD)
					.rotateX((-35f * rotation + breathingRotation) * DEG_TO_RAD);
			matrixStack.mulPose(rotationTransform);
			float oversizeScale = Math.max(1f, configuredBreastSize / DISPLAYED_FULL_SIZE);
			matrixStack.scale(oversizeScale, oversizeScale, oversizeScale);
		}
		matrixStack.scale(0.9995f, 1f, 1f); //z-fighting FIXXX
	}

	private void renderBreast(S state, PoseStack matrixStack, SubmitNodeCollector queue, int overlay, BreastSide side) {
		RenderType renderLayer = getRenderLayer(state);
		if(renderLayer == null) return; // only render if the player is visible in some capacity

		int alpha = state.isInvisible ? ARGB.as8BitChannel(0.15f) : 255;
		int bodyShade = uvPreviewBaseShade(state);
		int color = ARGB.color(alpha, bodyShade, bodyShade, bodyShade);
		var faceColors = uvPreviewFaceColors(state, side, alpha, bodyShade);

		var model = side.isLeft ? lBreast : rBreast;
		SoftBodyDeformation deformation = deformationFor(side);
		queue.submitCustomGeometry(matrixStack, renderLayer,
				new BreastRenderCommand(model, state, overlay, color, deformation, faceColors));

		// The expanded jacket shell sits in front of the rounded armor shell. Keep it for
		// unarmored breasts, but let covering chest armor own the visible realistic surface.
		if(state instanceof AvatarRenderState playerState && playerState.showJacket
				&& (!realisticModel || armorStack.isEmpty() || !genderArmor.coversBreasts())) {
			matrixStack.translate(0, 0, -0.015f);
			matrixStack.scale(1.05f, 1.05f, 1.05f);
			var jacketModel = side.isLeft ? lBreastWear : rBreastWear;
			int overlayAlpha = uvPreviewOverlayAlpha(state, alpha);
			int overlayColor = ARGB.color(overlayAlpha, 255, 255, 255);
			var overlayFaceColors = uvPreviewFaceColors(state, side, overlayAlpha, 255);
			queue.submitCustomGeometry(matrixStack, renderLayer,
					new BreastRenderCommand(jacketModel, state, overlay, overlayColor, deformation,
							overlayFaceColors));
		}
	}

	protected SoftBodyDeformation deformationFor(BreastSide side) {
		return deformationFor(side, 1f, 0);
	}

	protected SoftBodyDeformation deformationFor(BreastSide side, float surfaceScale, float surfaceOffsetZ) {
		if(!realisticModel) return SoftBodyDeformation.NONE;
		float positionX = bounceEnabled ? (side.isLeft ? lPhysPositionX : rPhysPositionX) : 0;
		float positionY = bounceEnabled ? (side.isLeft ? lPhysPositionY : rPhysPositionY) : 0;
		float physicsRotation = bounceEnabled ? (side.isLeft ? lPhysBounceRotation : rPhysBounceRotation) : 0;
		float configuredZ = breastOffsetZ * 0.68f + surfaceOffsetZ;
		// The new displayed 100% is 110% of the preceding experimental revision.
		float shapeScale = Mth.clamp(configuredBreastSize * 1.447875f, 0.15f, 2f) * surfaceScale;
		// Height also shapes the experimental model's pitch: raising it eases the drop while
		// lowering it lets the soft form hang further down. The classic transform is untouched.
		float heightControlledPitch = breastOffsetY * 8f;
		float realisticDropAngle = 5f + heightControlledPitch;
		SoftBodyDeformation deformation = SoftBodyDeformation.breast(positionX, positionY, physicsRotation,
				physicsIntensity, physicsMomentum, 0, configuredZ,
				realisticDropAngle + breathingRotation,
				side.isLeft ? outwardAngle : -outwardAngle,
				shapeScale, breastOffsetX);
		return cacheStaticDeformation ? deformation.cacheResult() : deformation;
	}

}
