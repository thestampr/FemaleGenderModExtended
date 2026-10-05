/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.physics.animation;

import com.wildfire.compat.PalAnimationPoseReader;
import com.wildfire.main.WildfireGender;
import com.wildfire.render.GenderRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Converts render-time custom-animation bone movement into tick-time body-physics impulses. */
@Environment(EnvType.CLIENT)
public final class CustomAnimationPhysics {
	private static final float MAX_SAMPLE_GAP_TICKS = 2.5f;
	private static final long STALE_SAMPLE_TICKS = 3;
	private static final long HISTORY_EXPIRY_TICKS = 100;
	private static final float DEG_TO_RAD = (float)(Math.PI / 180);
	private static final Map<UUID, MotionHistory> HISTORIES = new HashMap<>();

	private static @Nullable PoseReader poseReader = createPoseReader();
	private static long lastCleanupTime = Long.MIN_VALUE;

	private CustomAnimationPhysics() {}

	public static void capture(GenderRenderState state) {
		if(poseReader == null || state.avatar == null) return;
		MotionHistory existingHistory = HISTORIES.get(state.entityId);
		if(existingHistory != null && existingHistory.wasSampledAt(state.animationSampleTime)) {
			existingHistory.lastSeenGameTime = state.gameTime;
			return;
		}

		PoseSnapshot pose;
		try {
			pose = poseReader.read(state.avatar);
		} catch(LinkageError | RuntimeException exception) {
			WildfireGender.LOGGER.error("Disabling custom-animation physics after its compatibility provider failed", exception);
			poseReader = null;
			HISTORIES.clear();
			return;
		}

		cleanup(state.gameTime);
		if(pose == null) {
			HISTORIES.remove(state.entityId);
			return;
		}

		HISTORIES.computeIfAbsent(state.entityId, ignored -> new MotionHistory())
				.capture(state.animationSampleTime, state.gameTime, pose);
	}

	public static AnimationMotion consume(UUID entityId, long gameTime) {
		MotionHistory history = HISTORIES.get(entityId);
		if(history == null) return AnimationMotion.NONE;
		if(gameTime - history.lastSeenGameTime > STALE_SAMPLE_TICKS) {
			HISTORIES.remove(entityId);
			return AnimationMotion.NONE;
		}
		return history.consume();
	}

	private static @Nullable PoseReader createPoseReader() {
		if(!FabricLoader.getInstance().isModLoaded("player_animation_library")) return null;
		try {
			return new PalAnimationPoseReader();
		} catch(LinkageError exception) {
			WildfireGender.LOGGER.error("Player Animation Library is present but custom-animation physics could not start", exception);
			return null;
		}
	}

	private static void cleanup(long gameTime) {
		if(lastCleanupTime == Long.MIN_VALUE) {
			lastCleanupTime = gameTime;
			return;
		}
		if(gameTime < lastCleanupTime) {
			HISTORIES.clear();
			lastCleanupTime = gameTime;
			return;
		}
		if(gameTime - lastCleanupTime < HISTORY_EXPIRY_TICKS) return;

		lastCleanupTime = gameTime;
		HISTORIES.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenGameTime > HISTORY_EXPIRY_TICKS);
	}

	public interface PoseReader {
		@Nullable PoseSnapshot read(Avatar avatar);
	}

	public record BonePose(float x, float y, float z, float xRotation, float yRotation, float zRotation) {
		private BoneDelta delta(BonePose previous) {
			return new BoneDelta(
					x - previous.x,
					y - previous.y,
					z - previous.z,
					wrapRadians(xRotation - previous.xRotation),
					wrapRadians(yRotation - previous.yRotation),
					wrapRadians(zRotation - previous.zRotation)
			);
		}
	}

	public record PoseSnapshot(BonePose body, BonePose torso, BonePose leftLeg, BonePose rightLeg) {}

	public record AnimationImpulse(float vertical, float horizontal, float rotation) {
		public static final AnimationImpulse NONE = new AnimationImpulse(0, 0, 0);
	}

	public record AnimationMotion(AnimationImpulse breasts, AnimationImpulse realisticButt,
	                              AnimationImpulse leftClassicButt, AnimationImpulse rightClassicButt) {
		public static final AnimationMotion NONE = new AnimationMotion(AnimationImpulse.NONE, AnimationImpulse.NONE,
				AnimationImpulse.NONE, AnimationImpulse.NONE);
	}

	private record BoneDelta(float x, float y, float z, float xRotation, float yRotation, float zRotation) {
		private static final BoneDelta ZERO = new BoneDelta(0, 0, 0, 0, 0, 0);

		private BoneDelta add(BoneDelta other) {
			return new BoneDelta(x + other.x, y + other.y, z + other.z,
					xRotation + other.xRotation, yRotation + other.yRotation, zRotation + other.zRotation);
		}

		private AnimationImpulse toImpulse() {
			float vertical = Mth.clamp(y * 0.25f - xRotation * 0.9f, -1.25f, 1.25f);
			float horizontal = Mth.clamp(-x * 0.25f + zRotation * 0.7f, -0.85f, 0.85f);
			float rotation = Mth.clamp(-(float)Math.toDegrees(yRotation) / 15f, -2.5f, 2.5f);
			return new AnimationImpulse(vertical, horizontal, rotation);
		}
	}

	private static final class MotionHistory {
		private @Nullable PoseSnapshot previousPose;
		private float previousSampleTime;
		private long lastSeenGameTime;
		private BoneDelta pendingBody = BoneDelta.ZERO;
		private BoneDelta pendingTorso = BoneDelta.ZERO;
		private BoneDelta pendingLeftLeg = BoneDelta.ZERO;
		private BoneDelta pendingRightLeg = BoneDelta.ZERO;

		private void capture(float sampleTime, long gameTime, PoseSnapshot pose) {
			lastSeenGameTime = gameTime;
			if(previousPose == null) {
				resetBaseline(sampleTime, pose);
				return;
			}

			float elapsedTicks = sampleTime - previousSampleTime;
			if(elapsedTicks <= 0) return;
			if(elapsedTicks > MAX_SAMPLE_GAP_TICKS) {
				resetBaseline(sampleTime, pose);
				return;
			}

			pendingBody = pendingBody.add(pose.body().delta(previousPose.body()));
			pendingTorso = pendingTorso.add(pose.torso().delta(previousPose.torso()));
			pendingLeftLeg = pendingLeftLeg.add(pose.leftLeg().delta(previousPose.leftLeg()));
			pendingRightLeg = pendingRightLeg.add(pose.rightLeg().delta(previousPose.rightLeg()));
			previousPose = pose;
			previousSampleTime = sampleTime;
		}

		private boolean wasSampledAt(float sampleTime) {
			return previousPose != null && Float.compare(previousSampleTime, sampleTime) == 0;
		}

		private AnimationMotion consume() {
			BoneDelta upperBody = pendingBody.add(pendingTorso);
			AnimationMotion motion = new AnimationMotion(
					upperBody.toImpulse(),
					pendingBody.toImpulse(),
					pendingBody.add(pendingRightLeg).toImpulse(),
					pendingBody.add(pendingLeftLeg).toImpulse()
			);
			clearPendingMotion();
			return motion;
		}

		private void resetBaseline(float sampleTime, PoseSnapshot pose) {
			previousPose = pose;
			previousSampleTime = sampleTime;
			clearPendingMotion();
		}

		private void clearPendingMotion() {
			pendingBody = BoneDelta.ZERO;
			pendingTorso = BoneDelta.ZERO;
			pendingLeftLeg = BoneDelta.ZERO;
			pendingRightLeg = BoneDelta.ZERO;
		}
	}

	private static float wrapRadians(float radians) {
		return Mth.wrapDegrees((float)Math.toDegrees(radians)) * DEG_TO_RAD;
	}
}
