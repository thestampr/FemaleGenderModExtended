/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.compat;

import com.wildfire.physics.animation.CustomAnimationPhysics.BonePose;
import com.wildfire.physics.animation.CustomAnimationPhysics.PoseReader;
import com.wildfire.physics.animation.CustomAnimationPhysics.PoseSnapshot;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Avatar;
import org.jetbrains.annotations.Nullable;

/** Optional Player Animation Library bridge. This class is loaded only when PAL is installed. */
@Environment(EnvType.CLIENT)
public final class PalAnimationPoseReader implements PoseReader {
	@Override
	public @Nullable PoseSnapshot read(Avatar avatar) {
		AvatarAnimManager manager = PlayerAnimationAccess.getPlayerAnimManager(avatar);
		if(!manager.isActive()) return null;

		return new PoseSnapshot(
				readBone(manager, "body"),
				readBone(manager, "torso"),
				readBone(manager, "left_leg"),
				readBone(manager, "right_leg")
		);
	}

	private static BonePose readBone(AvatarAnimManager manager, String name) {
		PlayerAnimBone bone = manager.get3DTransform(new PlayerAnimBone(name));
		return new BonePose(bone.getPosX(), bone.getPosY(), bone.getPosZ(),
				bone.getRotX(), bone.getRotY(), bone.getRotZ());
	}
}
