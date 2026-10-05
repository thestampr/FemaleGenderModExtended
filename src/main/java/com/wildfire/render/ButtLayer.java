/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.config.ClientConfig;
import com.wildfire.main.uvs.UVDirection;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.main.uvs.UVQuad;
import com.wildfire.render.WildfireModelRenderer.ButtModelBox;
import com.wildfire.render.WildfireModelRenderer.ButtOverlayModelBox;
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.joml.Quaternionf;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public class ButtLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends BodyPartLayer<S, M> {
	private static final float DEG_TO_RAD = (float)(Math.PI / 180);
	private static final float BASE_TILT_DEGREES = -24f;
	private static final UVQuad HIDDEN_FACE = new UVQuad(0, 0, 0, 0);
	private static final UVLayout RIGHT_LEG_BASE = legLayout(0, 16);
	private static final UVLayout LEFT_LEG_BASE = legLayout(16, 48);
	private static final UVLayout RIGHT_LEG_OVERLAY = legLayout(0, 32);
	private static final UVLayout LEFT_LEG_OVERLAY = legLayout(0, 48);

	@UnknownNullability("null until the first render")
	private ButtModelBox leftUpperButt, rightUpperButt;
	@UnknownNullability("null until the first render")
	private ButtModelBox leftLowerButt, rightLowerButt;
	@UnknownNullability("null until the first render")
	private ButtOverlayModelBox leftUpperButtWear, rightUpperButtWear;
	@UnknownNullability("null until the first render")
	private ButtOverlayModelBox leftLowerButtWear, rightLowerButtWear;
	private @Nullable ButtModelBox leftUpperAttachment, rightUpperAttachment,
			leftLowerAttachment, rightLowerAttachment;
	private @Nullable ButtOverlayModelBox leftUpperAttachmentWear, rightUpperAttachmentWear,
			leftLowerAttachmentWear, rightLowerAttachmentWear;

	private @Nullable UVLayout previousLeftUV, previousRightUV, previousLeftOverlayUV, previousRightOverlayUV;
	private boolean previousShowInnerFaces;
	private boolean previousRealisticModel;
	protected float size;
	protected float offsetX;
	protected float offsetY;
	protected float offsetZ;
	protected float outwardAngle;
	protected float physicsIntensity;
	protected float physicsMomentum;
	protected boolean bounceEnabled;
	protected boolean realisticModel;
	protected boolean cacheStaticDeformation;
	protected GenderRenderState.@UnknownNullability BodyPhysicsState leftPhysics;
	protected GenderRenderState.@UnknownNullability BodyPhysicsState rightPhysics;

	public ButtLayer(RenderLayerParent<S, M> render) {
		super(render);
	}

	@Override
	public void submit(PoseStack matrixStack, SubmitNodeCollector queue, int light, S state, float limbAngle, float limbDistance) {
		GenderRenderState genderState = GenderRenderState.get(state);
		if(genderState == null || !setupRender(state, genderState)) return;
		// When leggings are equipped, ButtArmorLayer owns the complete visible shell. Keeping the
		// skin shell underneath lets its upper edge leak through armor at oblique angles.
		if(!state.legsEquipment.isEmpty()) return;

		try {
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
			renderSides(state, getParentModel(), matrixStack,
					side -> renderButt(state, matrixStack, queue, overlay, side));
		} catch(Exception exception) {
			WildfireGender.LOGGER.error("Failed to render butt layer", exception);
		}
	}

	protected boolean setupRender(S state, GenderRenderState genderState) {
		if(!ClientConfig.RENDER_BUTTS) return false;
		if(!genderState.gender.displaysGenderedBodyParts()) return false;
		if(!isLayerVisible(state)) return false;
		if(!genderState.showButtInArmor && !state.legsEquipment.isEmpty()) return false;

		leftPhysics = genderState.leftButtPhysics;
		rightPhysics = genderState.butts.linkedPhysics ? leftPhysics : genderState.rightButtPhysics;
		size = leftPhysics.getSize();
		if(size < 0.02f) return false;

		offsetX = genderState.butts.xOffset;
		offsetY = genderState.butts.yOffset;
		offsetZ = genderState.butts.zOffset;
		outwardAngle = Math.min(Math.round(genderState.butts.cleavage * 100f), 10);
		float resistance = Mth.clamp(WildfireHelper.getArmorConfig(state.legsEquipment).physicsResistance(), 0, 1);
		boolean leggingsBlockPhysics = !state.legsEquipment.isEmpty()
				&& !genderState.armorPhysicsOverride && resistance >= 1;
		bounceEnabled = genderState.hasButtPhysics && !leggingsBlockPhysics;
		cacheStaticDeformation = state instanceof ArmorStandRenderState && !bounceEnabled;
		realisticModel = genderState.realisticModel;
		physicsIntensity = genderState.buttBounceMultiplier;
		physicsMomentum = genderState.buttFloppyMultiplier;
		resizeBoxes(genderState);
		return true;
	}

	private void resizeBoxes(GenderRenderState state) {
		// The center-facing side is visible when the halves are separated and still closes the
		// geometry when they meet or overlap. It must never disappear at separation <= 0.
		boolean showInnerFaces = true;
		if(Objects.equals(previousLeftUV, state.leftButtUVLayout)
				&& Objects.equals(previousRightUV, state.rightButtUVLayout)
				&& Objects.equals(previousLeftOverlayUV, state.leftButtOverlayUVLayout)
				&& Objects.equals(previousRightOverlayUV, state.rightButtOverlayUVLayout)
				&& previousShowInnerFaces == showInnerFaces
				&& previousRealisticModel == state.realisticModel) return;

		previousLeftUV = state.leftButtUVLayout;
		previousRightUV = state.rightButtUVLayout;
		previousLeftOverlayUV = state.leftButtOverlayUVLayout;
		previousRightOverlayUV = state.rightButtOverlayUVLayout;
		previousShowInnerFaces = showInnerFaces;
		previousRealisticModel = state.realisticModel;
		if(state.realisticModel) {
			// Torso-local experimental form: each half now uses the breast's exact 4x5x3
			// volume. The single torso texture row stretches over the three torso-local model
			// rows, followed by the first two leg texture rows below the body.
			leftUpperButt = new ButtModelBox(64, 64, -4, 9, 0, 4, 3, 3, 0,
					upperLayout(state.leftButtUVLayout, BreastSide.LEFT, showInnerFaces), true, 9, 14, 8, 5);
			rightUpperButt = new ButtModelBox(64, 64, 0, 9, 0, 4, 3, 3, 0,
					upperLayout(state.rightButtUVLayout, BreastSide.RIGHT, showInnerFaces), true, 9, 14, 8, 5);
			leftLowerButt = new ButtModelBox(64, 64, -4, 12, 0, 4, 2, 3, 0,
					lowerTwoRowsLayout(RIGHT_LEG_BASE, BreastSide.LEFT, showInnerFaces), true, 9, 14, 8, 3);
			rightLowerButt = new ButtModelBox(64, 64, 0, 12, 0, 4, 2, 3, 0,
					lowerTwoRowsLayout(LEFT_LEG_BASE, BreastSide.RIGHT, showInnerFaces), true, 9, 14, 8, 3);
			leftUpperButtWear = new ButtOverlayModelBox(64, 64, -4, 9, 0, 4, 3, 3, 0,
					upperLayout(state.leftButtOverlayUVLayout, BreastSide.LEFT, showInnerFaces), true, 9, 14, 8, 5);
			rightUpperButtWear = new ButtOverlayModelBox(64, 64, 0, 9, 0, 4, 3, 3, 0,
					upperLayout(state.rightButtOverlayUVLayout, BreastSide.RIGHT, showInnerFaces), true, 9, 14, 8, 5);
			leftLowerButtWear = new ButtOverlayModelBox(64, 64, -4, 12, 0, 4, 2, 3, 0,
					lowerTwoRowsLayout(RIGHT_LEG_OVERLAY, BreastSide.LEFT, showInnerFaces), true, 9, 14, 8, 3);
			rightLowerButtWear = new ButtOverlayModelBox(64, 64, 0, 12, 0, 4, 2, 3, 0,
					lowerTwoRowsLayout(LEFT_LEG_OVERLAY, BreastSide.RIGHT, showInnerFaces), true, 9, 14, 8, 3);

			// A fixed two-pixel-deep backing occupies only the torso's rear half. Unlike the rounded
			// shell it has a real rear face and side walls, so oblique views cannot see through the
			// attachment while the established outer silhouette remains unchanged.
			leftUpperAttachment = new ButtModelBox(64, 64, -4, 9, 0.01f, 4, 3, 2, 0,
					upperLayout(state.leftButtUVLayout, BreastSide.LEFT, showInnerFaces));
			rightUpperAttachment = new ButtModelBox(64, 64, 0, 9, 0.01f, 4, 3, 2, 0,
					upperLayout(state.rightButtUVLayout, BreastSide.RIGHT, showInnerFaces));
			leftLowerAttachment = new ButtModelBox(64, 64, -4, 12, 0.01f, 4, 2, 2, 0,
					lowerTwoRowsLayout(RIGHT_LEG_BASE, BreastSide.LEFT, showInnerFaces));
			rightLowerAttachment = new ButtModelBox(64, 64, 0, 12, 0.01f, 4, 2, 2, 0,
					lowerTwoRowsLayout(LEFT_LEG_BASE, BreastSide.RIGHT, showInnerFaces));
			leftUpperAttachmentWear = new ButtOverlayModelBox(64, 64, -4, 9, 0.01f, 4, 3, 2, 0,
					upperLayout(state.leftButtOverlayUVLayout, BreastSide.LEFT, showInnerFaces));
			rightUpperAttachmentWear = new ButtOverlayModelBox(64, 64, 0, 9, 0.01f, 4, 3, 2, 0,
					upperLayout(state.rightButtOverlayUVLayout, BreastSide.RIGHT, showInnerFaces));
			leftLowerAttachmentWear = new ButtOverlayModelBox(64, 64, -4, 12, 0.01f, 4, 2, 2, 0,
					lowerTwoRowsLayout(RIGHT_LEG_OVERLAY, BreastSide.LEFT, showInnerFaces));
			rightLowerAttachmentWear = new ButtOverlayModelBox(64, 64, 0, 12, 0.01f, 4, 2, 2, 0,
					lowerTwoRowsLayout(LEFT_LEG_OVERLAY, BreastSide.RIGHT, showInnerFaces));
			return;
		}

		leftUpperAttachment = rightUpperAttachment = leftLowerAttachment = rightLowerAttachment = null;
		leftUpperAttachmentWear = rightUpperAttachmentWear = leftLowerAttachmentWear = rightLowerAttachmentWear = null;
		// Classic keeps the established one torso row plus three leg rows. Its renderer now
		// reproduces the former leg pivot beneath the torso rather than inheriting leg rotation.
		leftUpperButt = new ButtModelBox(64, 64, -2, -1, 0, 4, 1, 3, 0,
				upperLayout(state.leftButtUVLayout, BreastSide.LEFT, showInnerFaces), false);
		rightUpperButt = new ButtModelBox(64, 64, -2, -1, 0, 4, 1, 3, 0,
				upperLayout(state.rightButtUVLayout, BreastSide.RIGHT, showInnerFaces), false);
		leftLowerButt = new ButtModelBox(64, 64, -2, 0, 0, 4, 3, 3, 0,
				lowerLayout(RIGHT_LEG_BASE, BreastSide.LEFT, showInnerFaces), false);
		rightLowerButt = new ButtModelBox(64, 64, -2, 0, 0, 4, 3, 3, 0,
				lowerLayout(LEFT_LEG_BASE, BreastSide.RIGHT, showInnerFaces), false);
		leftUpperButtWear = new ButtOverlayModelBox(64, 64, -2, -1, 0, 4, 1, 3, 0,
				upperLayout(state.leftButtOverlayUVLayout, BreastSide.LEFT, showInnerFaces), false);
		rightUpperButtWear = new ButtOverlayModelBox(64, 64, -2, -1, 0, 4, 1, 3, 0,
				upperLayout(state.rightButtOverlayUVLayout, BreastSide.RIGHT, showInnerFaces), false);
		leftLowerButtWear = new ButtOverlayModelBox(64, 64, -2, 0, 0, 4, 3, 3, 0,
				lowerLayout(RIGHT_LEG_OVERLAY, BreastSide.LEFT, showInnerFaces), false);
		rightLowerButtWear = new ButtOverlayModelBox(64, 64, -2, 0, 0, 4, 3, 3, 0,
				lowerLayout(LEFT_LEG_OVERLAY, BreastSide.RIGHT, showInnerFaces), false);
	}

	protected static UVLayout upperLayout(UVLayout source, BreastSide side, boolean showInnerFaces) {
		UVLayout result = bottomRow(source);
		// The lower cap touches the leg-sourced box and must not render as a loose plate.
		result.put(UVDirection.UP, HIDDEN_FACE);
		return hideInnerFace(result, side, showInnerFaces);
	}

	private static UVLayout lowerLayout(UVLayout source, BreastSide side, boolean showInnerFaces) {
		UVLayout result = source.copy();
		// The upper cap touches the torso-sourced box and is internal geometry.
		result.put(UVDirection.DOWN, HIDDEN_FACE);
		return hideInnerFace(result, side, showInnerFaces);
	}

	protected static UVLayout lowerTwoRowsLayout(UVLayout source, BreastSide side, boolean showInnerFaces) {
		UVLayout result = source.copy();
		for(var entry : source.getQuads().entrySet()) {
			UVQuad quad = entry.getValue();
			int direction = Integer.signum(quad.y2() - quad.y1());
			int height = Math.min(Math.abs(quad.y2() - quad.y1()), 2);
			result.put(entry.getKey(), new UVQuad(quad.x1(), quad.y1(), quad.x2(), quad.y1() + direction * height));
		}
		result.put(UVDirection.DOWN, HIDDEN_FACE);
		return hideInnerFace(result, side, showInnerFaces);
	}

	private static UVLayout hideInnerFace(UVLayout layout, BreastSide side, boolean showInnerFaces) {
		if(!showInnerFaces) {
			layout.put(side.isLeft ? UVDirection.EAST : UVDirection.WEST, HIDDEN_FACE);
		}
		return layout;
	}

	private static UVLayout bottomRow(UVLayout source) {
		UVLayout result = source.copy();
		for(var entry : source.getQuads().entrySet()) {
			UVQuad quad = entry.getValue();
			result.put(entry.getKey(), new UVQuad(quad.x1(), Math.max(quad.y1(), quad.y2() - 1), quad.x2(), quad.y2()));
		}
		return result;
	}

	protected static UVLayout legLayout(int textureX, int textureY) {
		return new UVLayout(
				new UVQuad(textureX, textureY + 4, textureX + 4, textureY + 7),
				new UVQuad(textureX + 8, textureY + 4, textureX + 12, textureY + 7),
				new UVQuad(textureX + 8, textureY, textureX + 12, textureY + 4),
				new UVQuad(textureX + 4, textureY, textureX + 8, textureY + 4),
				new UVQuad(textureX + 12, textureY + 4, textureX + 16, textureY + 7)
		);
	}

	@Override
	protected void setupTransformations(S state, M model, PoseStack matrixStack, BreastSide side) {
		if(state.isBaby) {
			matrixStack.scale(state.ageScale, state.ageScale, state.ageScale);
			matrixStack.translate(0, 0.75, 0);
		}

		if(realisticModel) {
			applyTorsoTransform(model, matrixStack);
			matrixStack.translate(0, -0.0625f, 0);
		} else {
			applyLegTransform(model, matrixStack, side);
		}
		GenderRenderState.BodyPhysicsState physics = side.isLeft ? leftPhysics : rightPhysics;
		if(bounceEnabled && !realisticModel) {
			matrixStack.translate(physics.getPositionX() / 32f, physics.getPositionY() / 32f, 0);
		}

		float sideDirection = side.isLeft ? -1 : 1;
		// Height zero is 1.5 model pixels above the original anchor. Depth zero is
		// half a pixel inward, matching the previous -5 slider position.
		if(!realisticModel) {
			float configuredX = sideDirection * offsetX * 0.0625f;
			float configuredY = -offsetY * 0.0625f;
			float configuredZ = offsetZ * 0.0625f;
			matrixStack.translate(configuredX, -1.5f * 0.0625f + configuredY,
					0.5f * 0.0625f + configuredZ);
		}

		float physicsRotation = bounceEnabled ? physics.getBounceRotation() : 0;
		if(!realisticModel) {
			matrixStack.mulPose(new Quaternionf()
					.rotationY(sideDirection * outwardAngle * DEG_TO_RAD)
					.rotateX((BASE_TILT_DEGREES + physicsRotation * 0.35f) * DEG_TO_RAD));
		}

		if(!realisticModel) {
			// 100% now uses the former 60% geometry, which is approximately leg-sized.
			float scaledSize = size * 0.6f;
			float horizontalScale = 0.85f + scaledSize * 0.35f;
			float depthScale = 0.45f + scaledSize * 1.1f;
			matrixStack.translate(0, 0.0625f, 0);
			matrixStack.scale(horizontalScale, horizontalScale, depthScale);
			matrixStack.translate(0, -0.0625f, 0);
		}
	}

	private void renderButt(S state, PoseStack matrixStack, SubmitNodeCollector queue, int overlay, BreastSide side) {
		RenderType renderLayer = getRenderLayer(state);
		if(renderLayer == null) return;

		int alpha = state.isInvisible ? ARGB.as8BitChannel(0.15f) : 255;
		int bodyShade = uvPreviewBaseShade(state);
		int color = ARGB.color(alpha, bodyShade, bodyShade, bodyShade);
		var faceColors = uvPreviewFaceColors(state, side, alpha, bodyShade);
		SoftBodyDeformation deformation = deformationFor(side);
		if(realisticModel) {
			queue.submitCustomGeometry(matrixStack, renderLayer,
					new BreastRenderCommand(side.isLeft ? leftUpperAttachment : rightUpperAttachment,
							state, overlay, color, SoftBodyDeformation.NONE, faceColors));
			queue.submitCustomGeometry(matrixStack, renderLayer,
					new BreastRenderCommand(side.isLeft ? leftLowerAttachment : rightLowerAttachment,
							state, overlay, color, SoftBodyDeformation.NONE, faceColors));
		}
		queue.submitCustomGeometry(matrixStack, renderLayer,
				new BreastRenderCommand(side.isLeft ? leftUpperButt : rightUpperButt, state, overlay,
						color, deformation, faceColors));
		queue.submitCustomGeometry(matrixStack, renderLayer,
				new BreastRenderCommand(side.isLeft ? leftLowerButt : rightLowerButt, state, overlay,
						color, deformation, faceColors));

		boolean showPantsOverlay = state instanceof AvatarRenderState playerState
				&& (side.isLeft ? playerState.showRightPants : playerState.showLeftPants);
		if(showPantsOverlay) {
			matrixStack.translate(0, 0, 0.01f);
			matrixStack.scale(1.04f, 1.04f, 1.04f);
			int overlayAlpha = uvPreviewOverlayAlpha(state, alpha);
			int overlayColor = ARGB.color(overlayAlpha, 255, 255, 255);
			var overlayFaceColors = uvPreviewFaceColors(state, side, overlayAlpha, 255);
			if(realisticModel) {
				queue.submitCustomGeometry(matrixStack, renderLayer,
						new BreastRenderCommand(side.isLeft ? leftUpperAttachmentWear : rightUpperAttachmentWear,
								state, overlay, overlayColor, SoftBodyDeformation.NONE, overlayFaceColors));
				queue.submitCustomGeometry(matrixStack, renderLayer,
						new BreastRenderCommand(side.isLeft ? leftLowerAttachmentWear : rightLowerAttachmentWear,
								state, overlay, overlayColor, SoftBodyDeformation.NONE, overlayFaceColors));
			}
			queue.submitCustomGeometry(matrixStack, renderLayer,
					new BreastRenderCommand(side.isLeft ? leftUpperButtWear : rightUpperButtWear, state,
							overlay, overlayColor, deformation, overlayFaceColors));
			queue.submitCustomGeometry(matrixStack, renderLayer,
					new BreastRenderCommand(side.isLeft ? leftLowerButtWear : rightLowerButtWear, state,
							overlay, overlayColor, deformation, overlayFaceColors));
		}
	}

	protected SoftBodyDeformation deformationFor(BreastSide side) {
		if(!realisticModel) return SoftBodyDeformation.NONE;
		GenderRenderState.BodyPhysicsState physics = side.isLeft ? leftPhysics : rightPhysics;
		float motionX = bounceEnabled ? physics.getPositionX() : 0;
		float motionY = bounceEnabled ? physics.getPositionY() : 0;
		float physicsRotation = bounceEnabled ? physics.getBounceRotation() : 0;
		float sideDirection = side.isLeft ? -1 : 1;
		// The new displayed 100% is 110% of the preceding experimental revision.
		float shapeScale = net.minecraft.util.Mth.clamp(size * 1.287f, 0.15f, 2f);
		float heightControlledPitch = offsetY * 8f;
		float realisticDropAngle = -13f + heightControlledPitch;
		float configuredZ = offsetZ * 0.68f;
		SoftBodyDeformation deformation = SoftBodyDeformation.butt(motionX, motionY, physicsRotation,
				physicsIntensity, physicsMomentum, 0, 0, configuredZ,
				realisticDropAngle, sideDirection * outwardAngle,
				shapeScale, offsetX, sideDirection);
		return cacheStaticDeformation ? deformation.cacheResult() : deformation;
	}
}
