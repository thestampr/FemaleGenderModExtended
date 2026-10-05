/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.render;

import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Anchored soft-body deformation for the experimental renderer.
 *
 * <p>The attachment plane always has zero influence. Configuration offsets, pose bends, and
 * physics motion progressively affect the surface toward its free end, so the body part changes
 * shape without sliding its root across the player model.</p>
 */
public final class SoftBodyDeformation {
	public static final SoftBodyDeformation NONE = new SoftBodyDeformation(false, 0, 0, 0,
			0, 0.25f, 0, 0, 0, 0, 0, 1, Form.NONE, 0, 0);

	private enum Form { NONE, BREAST, BUTT }

	private final boolean anchored;
	private final float motionX;
	private final float motionY;
	private final float physicsRotation;
	private final float intensity;
	private final float momentum;
	private final float configuredX;
	private final float configuredY;
	private final float configuredZ;
	private final float bendXDegrees;
	private final float bendYDegrees;
	private final float shapeScale;
	private final Form form;
	private final float separation;
	private final float sideDirection;
	private final float response;
	private final float verticalImpulse;
	private final float lateralImpulse;
	private final float rotationalImpulse;
	private final float bendX;
	private final float bendXCos;
	private final float bendXSin;
	private final float bendY;
	private final float bendYCos;
	private final float bendYSin;
	private final float absoluteBendYSin;
	private boolean cacheResult;

	private SoftBodyDeformation(boolean anchored, float motionX, float motionY, float physicsRotation,
	                            float intensity, float momentum, float configuredX, float configuredY,
	                            float configuredZ, float bendXDegrees, float bendYDegrees, float shapeScale,
	                            Form form, float separation, float sideDirection) {
		this.anchored = anchored;
		this.motionX = motionX;
		this.motionY = motionY;
		this.physicsRotation = physicsRotation;
		this.intensity = intensity;
		this.momentum = momentum;
		this.configuredX = configuredX;
		this.configuredY = configuredY;
		this.configuredZ = configuredZ;
		this.bendXDegrees = bendXDegrees;
		this.bendYDegrees = bendYDegrees;
		this.shapeScale = shapeScale;
		this.form = form;
		this.separation = separation;
		this.sideDirection = sideDirection;
		float configuredIntensity = Mth.clamp(intensity * 3f, 0, 2f);
		float configuredSoftness = Mth.clamp((momentum - 0.25f) / 1.75f, 0, 1f);
		this.response = configuredIntensity * (0.035f + configuredSoftness * 0.105f);
		this.verticalImpulse = Mth.clamp(motionY / 1.5f, -1, 1);
		this.lateralImpulse = Mth.clamp(motionX / 1.5f, -1, 1);
		this.rotationalImpulse = Mth.clamp(physicsRotation / 25f, -1, 1);
		this.bendX = (bendXDegrees + physicsRotation * 0.35f) * Mth.DEG_TO_RAD;
		this.bendXCos = Mth.cos(this.bendX);
		this.bendXSin = Mth.sin(this.bendX);
		this.bendY = bendYDegrees * Mth.DEG_TO_RAD;
		this.bendYCos = Mth.cos(this.bendY);
		this.bendYSin = Mth.sin(this.bendY);
		this.absoluteBendYSin = Mth.sin(Math.abs(this.bendY));
	}

	public static SoftBodyDeformation breast(float motionX, float motionY, float physicsRotation,
	                                         float intensity, float momentum, float configuredY,
	                                         float configuredZ, float bendXDegrees, float bendYDegrees,
	                                         float shapeScale, float separation) {
		return new SoftBodyDeformation(true, motionX, motionY, physicsRotation, intensity, momentum,
				0, configuredY, configuredZ, bendXDegrees, bendYDegrees, shapeScale,
				Form.BREAST, separation, 0);
	}

	public static SoftBodyDeformation butt(float motionX, float motionY, float physicsRotation,
	                                       float intensity, float momentum, float configuredX,
	                                       float configuredY, float configuredZ, float bendXDegrees,
	                                       float bendYDegrees, float shapeScale, float separation,
	                                       float sideDirection) {
		return new SoftBodyDeformation(true, motionX, motionY, physicsRotation, intensity, momentum,
				configuredX, configuredY, configuredZ, bendXDegrees, bendYDegrees, shapeScale,
				Form.BUTT, separation, sideDirection);
	}

	public boolean active() {
		return anchored;
	}

	public SoftBodyDeformation cacheResult() {
		this.cacheResult = true;
		return this;
	}

	boolean shouldCacheResult() {
		return cacheResult;
	}

	CacheKey cacheKey() {
		return new CacheKey(motionX, motionY, physicsRotation, intensity, momentum,
				configuredX, configuredY, configuredZ, bendXDegrees, bendYDegrees,
				shapeScale, form, separation, sideDirection);
	}

	record CacheKey(float motionX, float motionY, float physicsRotation, float intensity, float momentum,
	                float configuredX, float configuredY, float configuredZ, float bendXDegrees,
	                float bendYDegrees, float shapeScale, Form form, float separation,
	                float sideDirection) {}

	public Vector3f deform(WildfireModelRenderer.PositionTextureVertex vertex,
	                       WildfireModelRenderer.ModelBox model) {
		return deform(vertex, model, new Vector3f());
	}

	public Vector3f deform(WildfireModelRenderer.PositionTextureVertex vertex,
	                       WildfireModelRenderer.ModelBox model, Vector3f result) {
		float influence = influenceFor(vertex, model);
		if(influence <= 0.00001f) {
			return result.set(vertex.x(), vertex.y(), vertex.z());
		}

		float centerX = (model.posX1 + model.posX2) * 0.5f;
		float centerY = (model.smoothY1 + model.smoothY2) * 0.5f;
		float attachmentZ = model.rearFacing ? model.posZ1 : model.posZ2;
		float originalX = vertex.x();
		float originalY = vertex.y();
		float originalZ = vertex.z();
		result.set(originalX - centerX, originalY - centerY, originalZ - attachmentZ);

		rotateX(result, bendXCos, bendXSin);
		rotateY(result, bendYCos, bendYSin);
		result.add(centerX, centerY, attachmentZ);
		// Rotation is deformation too: blend it from zero at the attachment ring to full at
		// the free surface. Applying the full angle to the first ring creates long spikes.
		result.set(
				Mth.lerp(influence, originalX, result.x),
				Mth.lerp(influence, originalY, result.y),
				Mth.lerp(influence, originalZ, result.z));

		float localX = result.x - centerX;
		float scaleOriginY = form == Form.BREAST ? model.smoothY1 : centerY;
		float distanceFromScaleOrigin = result.y - scaleOriginY;
		float scaledY = scaleOriginY + (form == Form.BREAST && distanceFromScaleOrigin < 0
				? distanceFromScaleOrigin
				: distanceFromScaleOrigin * Mth.lerp(influence, 1f, shapeScale));
		float localY = scaledY - centerY;
		float localZ = result.z - attachmentZ;
		float anchoredScale = Mth.lerp(influence, 1f, shapeScale);
		localX *= anchoredScale;
		localZ *= Mth.lerp(influence, 1f, 1f + (shapeScale - 1f) * 1.15f);
		float verticalScale = Mth.clamp(1f - verticalImpulse * response * influence, 0.78f, 1.22f);
		float volumeCompensation = (float)(1.0 / Math.sqrt(verticalScale));
		localX *= Mth.lerp(influence, 1f, volumeCompensation);
		localY *= verticalScale;
		localZ *= Mth.lerp(influence, 1f,
				volumeCompensation * (1f + Math.abs(rotationalImpulse) * response * 0.2f));
		localX += localY * lateralImpulse * response * 0.18f * influence;
		localZ += localY * rotationalImpulse * response * 0.28f * influence;

		float physicsX = motionX * 0.5f;
		float physicsY = motionY * 0.5f;
		result.set(
				centerX + localX + (configuredX + physicsX) * influence,
				centerY + localY + (configuredY + physicsY) * influence,
				attachmentZ + localZ + configuredZ * influence);
		if(form == Form.BREAST) {
			shapeBreastCleavage(result, vertex, model, influence);
		} else if(form == Form.BUTT) {
			shapeButtJoin(result, vertex, model, influence);
		}
		return result;
	}

	public Vector3f deformNormal(WildfireModelRenderer.PositionTextureVertex vertex,
	                            WildfireModelRenderer.ModelBox model, Vector3fc sourceNormal) {
		return deformNormal(vertex, model, sourceNormal, new Vector3f());
	}

	public Vector3f deformNormal(WildfireModelRenderer.PositionTextureVertex vertex,
	                            WildfireModelRenderer.ModelBox model, Vector3fc sourceNormal,
	                            Vector3f result) {
		float influence = influenceFor(vertex, model);
		result.set(sourceNormal);
		if(influence <= 0.00001f) return result;

		float verticalScale = Mth.clamp(1f - verticalImpulse * response * influence, 0.78f, 1.22f);
		float volumeCompensation = (float)(1.0 / Math.sqrt(verticalScale));
		float anchoredScale = Mth.lerp(influence, 1f, shapeScale);

		float xScale = anchoredScale * Mth.lerp(influence, 1f, volumeCompensation);
		float yShapeScale = form == Form.BREAST && vertex.y() < model.smoothY1 ? 1f : anchoredScale;
		float yScale = yShapeScale * verticalScale;
		float zScale = Mth.lerp(influence, 1f, 1f + (shapeScale - 1f) * 1.15f)
				* Mth.lerp(influence, 1f,
				volumeCompensation * (1f + Math.abs(rotationalImpulse) * response * 0.2f));

		rotateX(result, bendX * influence);
		rotateY(result, bendY * influence);
		result.set(result.x / Math.max(xScale, 0.001f),
				result.y / Math.max(yScale, 0.001f),
				result.z / Math.max(zScale, 0.001f));
		return result.normalize();
	}

	private static float influenceFor(WildfireModelRenderer.PositionTextureVertex vertex,
	                                  WildfireModelRenderer.ModelBox model) {
		float depth = Math.max(model.posZ2 - model.posZ1, 0.001f);
		float freeProgress = model.rearFacing
				? Mth.clamp((vertex.z() - model.posZ1) / depth, 0, 1)
				: Mth.clamp((model.posZ2 - vertex.z()) / depth, 0, 1);
		return freeProgress * freeProgress * (3f - 2f * freeProgress);
	}

	private void shapeBreastCleavage(Vector3f result, WildfireModelRenderer.PositionTextureVertex vertex,
	                                 WildfireModelRenderer.ModelBox model, float influence) {
		boolean left = model.posX2 <= 0;
		float width = Math.max(model.posX2 - model.posX1, 0.001f);
		float innerProgress = left
				? Mth.clamp((vertex.x() - model.posX1) / width, 0, 1)
				: Mth.clamp((model.posX2 - vertex.x()) / width, 0, 1);
		float innerWeight = innerProgress * innerProgress;

		// Separation keeps the same direction as the classic model: negative closes the two
		// halves and positive opens them. At -10 the meshes overlap slightly below the surface
		// and the center-facing vertices are lifted, hiding the join without merging the meshes.
		// Shift the whole realistic scale by one range: +10 uses the former zero shape,
		// zero uses the former -10 shape, and -10 adds a deeper whole-lobe overlap.
		float sealStage = Mth.clamp(1f - separation, 0, 2);
		float extraSeal = smoothStep(Mth.clamp(sealStage, 0, 1));
		float deepSeal = smoothStep(Mth.clamp(sealStage - 1f, 0, 1));
		float grooveGap = 0;
		float boundary = left ? -grooveGap : grooveGap;
		result.x = left ? Math.min(result.x, boundary) : Math.max(result.x, boundary);
		float centerWeight = innerWeight * influence;
		float deepCenterWeight = smoothStep(innerProgress) * influence;
		float grooveDepth = Math.max(0, 0.18f - 0.16f * extraSeal - 0.02f * deepSeal);
		result.z += grooveDepth * centerWeight;
		float towardCenter = left ? 1f : -1f;
		result.x += towardCenter * (0.24f + 0.3f * extraSeal) * centerWeight;
		result.x += towardCenter * 1.15f * deepSeal * deepCenterWeight;
		result.z -= (0.35f + 0.2f * extraSeal) * centerWeight
				+ 0.25f * deepSeal * deepCenterWeight;
		// Y rotation pushes both inner rims toward the camera. Counter that exact component so
		// the joined center cannot become the visual peak of either lobe.
		float rotationCompensation = absoluteBendYSin * width * 0.5f;
		result.z += rotationCompensation * deepCenterWeight;
	}

	private void shapeButtJoin(Vector3f result, WildfireModelRenderer.PositionTextureVertex vertex,
	                           WildfireModelRenderer.ModelBox model, float influence) {
		float width = Math.max(model.posX2 - model.posX1, 0.001f);
		float innerWeight = sideDirection < 0
				? Mth.clamp((vertex.x() - model.posX1) / width, 0, 1)
				: Mth.clamp((model.posX2 - vertex.x()) / width, 0, 1);
		innerWeight *= innerWeight;
		float sealStage = Mth.clamp(1f - separation, 0, 2);
		float extraSeal = smoothStep(Mth.clamp(sealStage, 0, 1));
		float deepSeal = smoothStep(Mth.clamp(sealStage - 1f, 0, 1));
		// The former -10 result combined 1.0 unit of positional movement with 1.55 units
		// of form deformation. It is now the zero point; -10 adds a third sealing stage.
		float joinStrength = 2.55f + 0.55f * extraSeal + 0.65f * deepSeal;
		float formWeight = innerWeight * (0.35f + influence * 0.65f);
		// Offset diagonally through the leg: toward the center, slightly upward, and inward.
		// The hidden overlap joins the silhouette without emerging through the opposite lobe.
		result.x += -sideDirection * joinStrength * formWeight;
		result.y -= (0.72f + 0.18f * extraSeal + 0.16f * deepSeal) * formWeight;
		result.z -= (0.72f + 0.2f * extraSeal + 0.22f * deepSeal) * formWeight;
		// Lift the visible center shoulder while the deeper overlap remains tucked through the
		// leg. This fills the central groove at -10 but keeps two independently animated meshes.
		result.z += (0.55f + 0.25f * extraSeal + 0.3f * deepSeal) * innerWeight * influence
				* (1f - influence * 0.3f);
		float rotationCompensation = absoluteBendYSin * width * 0.5f;
		result.z -= rotationCompensation * smoothStep(Mth.sqrt(innerWeight)) * influence;
	}

	private static float smoothStep(float value) {
		return value * value * (3f - 2f * value);
	}

	private static void rotateX(Vector3f vector, float radians) {
		rotateX(vector, Mth.cos(radians), Mth.sin(radians));
	}

	private static void rotateX(Vector3f vector, float cos, float sin) {
		float y = vector.y * cos - vector.z * sin;
		float z = vector.y * sin + vector.z * cos;
		vector.y = y;
		vector.z = z;
	}

	private static void rotateY(Vector3f vector, float radians) {
		rotateY(vector, Mth.cos(radians), Mth.sin(radians));
	}

	private static void rotateY(Vector3f vector, float cos, float sin) {
		float x = vector.x * cos + vector.z * sin;
		float z = -vector.x * sin + vector.z * cos;
		vector.x = x;
		vector.z = z;
	}
}
