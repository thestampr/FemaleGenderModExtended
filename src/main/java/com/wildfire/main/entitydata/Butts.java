/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.main.entitydata;

import com.wildfire.main.config.Configuration;
import com.wildfire.main.config.types.ConfigKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;

import java.util.function.Consumer;

/** Appearance and linked-physics settings for the two butt cheeks. */
@SuppressWarnings("UnusedReturnValue")
public final class Butts {
	public static final StreamCodec<ByteBuf, Butts> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Butts::getXOffset,
			ByteBufCodecs.FLOAT, Butts::getYOffset,
			ByteBufCodecs.FLOAT, Butts::getZOffset,
			ByteBufCodecs.BOOL, Butts::isLinkedPhysics,
			ByteBufCodecs.FLOAT, Butts::getCleavage,
			(x, y, z, linkedPhysics, cleavage) -> {
				var butts = new Butts();
				butts.xOffset = x;
				butts.yOffset = y;
				butts.zOffset = z;
				butts.linkedPhysics = linkedPhysics;
				butts.cleavage = cleavage;
				return butts;
			}
	);

	private float xOffset = Configuration.BUTTS_OFFSET_X.getDefault();
	private float yOffset = Configuration.BUTTS_OFFSET_Y.getDefault();
	private float zOffset = Configuration.BUTTS_OFFSET_Z.getDefault();
	private float cleavage = Configuration.BUTTS_CLEAVAGE.getDefault();
	private boolean linkedPhysics = Configuration.BUTTS_LINKED_PHYSICS.getDefault();

	private <T> boolean updateValue(ConfigKey<T> key, T value, Consumer<T> setter) {
		if(!key.validate(value)) return false;
		setter.accept(value);
		return true;
	}

	public Vector3f getOffsets() {
		return new Vector3f(xOffset, yOffset, zOffset);
	}

	public float getXOffset() { return xOffset; }
	public boolean updateXOffset(float value) { return updateValue(Configuration.BUTTS_OFFSET_X, value, v -> xOffset = v); }
	public float getYOffset() { return yOffset; }
	public boolean updateYOffset(float value) { return updateValue(Configuration.BUTTS_OFFSET_Y, value, v -> yOffset = v); }
	public float getZOffset() { return zOffset; }
	public boolean updateZOffset(float value) { return updateValue(Configuration.BUTTS_OFFSET_Z, value, v -> zOffset = v); }
	public float getCleavage() { return cleavage; }
	public boolean updateCleavage(float value) { return updateValue(Configuration.BUTTS_CLEAVAGE, value, v -> cleavage = v); }
	public boolean isLinkedPhysics() { return linkedPhysics; }
	public boolean updateLinkedPhysics(boolean value) { return updateValue(Configuration.BUTTS_LINKED_PHYSICS, value, v -> linkedPhysics = v); }

	public void copyFrom(Butts butts) {
		xOffset = butts.xOffset;
		yOffset = butts.yOffset;
		zOffset = butts.zOffset;
		cleavage = butts.cleavage;
		linkedPhysics = butts.linkedPhysics;
	}
}
