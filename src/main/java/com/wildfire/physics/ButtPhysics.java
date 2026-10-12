/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.physics;

import com.wildfire.api.IGenderArmor;
import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.physics.animation.CustomAnimationPhysics.AnimationImpulse;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class ButtPhysics extends BodyPhysics {
	private static final float MAX_SAMPLE_GAP_TICKS = 2.5f;
	private float lastLegSampleTime = Float.NaN;
	private float sampledLegX, sampledLegY, sampledLegZ, sampledLegPitch;
	private float pendingLegX, pendingLegY, pendingLegZ, pendingLegPitch;

	public ButtPhysics(EntityConfig entityConfig) {
		super(entityConfig);
	}

	/** Receives the same small torso-relative displacement used to render this half of the butt. */
	@Environment(EnvType.CLIENT)
	public void captureLegPull(float x, float y, float z, float pitch, float sampleTime) {
		float elapsed = sampleTime - lastLegSampleTime;
		if(!Float.isNaN(lastLegSampleTime) && elapsed <= 0) return;
		if(!Float.isNaN(lastLegSampleTime) && elapsed <= MAX_SAMPLE_GAP_TICKS) {
			pendingLegX += x - sampledLegX;
			pendingLegY += y - sampledLegY;
			pendingLegZ += z - sampledLegZ;
			pendingLegPitch += pitch - sampledLegPitch;
		} else {
			pendingLegX = pendingLegY = pendingLegZ = pendingLegPitch = 0;
		}
		lastLegSampleTime = sampleTime;
		sampledLegX = x;
		sampledLegY = y;
		sampledLegZ = z;
		sampledLegPitch = pitch;
	}

	@Environment(EnvType.CLIENT)
	@Override
	public void update(LivingEntity entity, IGenderArmor armor, AnimationImpulse animationImpulse) {
		// The leg pulls the position directly; only the change in that pull excites the spring.
		// Keep the impulse modest because BodyPhysics multiplies it by bounce intensity.
		AnimationImpulse withLegMotion = new AnimationImpulse(
				animationImpulse.vertical() + Mth.clamp(pendingLegY, -1f, 1f) * 0.1f,
				animationImpulse.horizontal() + Mth.clamp(pendingLegX, -1f, 1f) * 0.1f,
				animationImpulse.rotation() + Mth.clamp(pendingLegZ, -1f, 1f) * 0.1f
						+ Mth.clamp(pendingLegPitch, -5f, 5f) * 0.02f);
		pendingLegX = pendingLegY = pendingLegZ = pendingLegPitch = 0;
		super.update(entity, armor, withLegMotion);
	}

	@Environment(EnvType.CLIENT)
	@Override
	public void updateSimplified(IGenderArmor armor) {
		pendingLegX = pendingLegY = pendingLegZ = pendingLegPitch = 0;
		lastLegSampleTime = Float.NaN;
		super.updateSimplified(armor);
	}

	@Override
	protected float configuredSize() {
		return entityConfig.getButtSize();
	}

	@Override
	protected float bounceMultiplier() {
		return entityConfig.getButtBounceMultiplier();
	}

	@Override
	protected float floppiness() {
		return entityConfig.getButtFloppiness();
	}

	@Override
	protected boolean linkedPhysics() {
		return entityConfig.getButts().isLinkedPhysics();
	}

	@Override
	protected boolean bodyPartVisible() {
		return entityConfig.getGender().displaysGenderedBodyParts();
	}

	public float getButtSize() {
		return getSize();
	}

	public float getPreviousButtSize() {
		return getPreviousSize();
	}
}
