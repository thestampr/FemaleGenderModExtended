/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.physics;

import com.wildfire.main.entitydata.EntityConfig;

public final class ButtPhysics extends BodyPhysics {
	public ButtPhysics(EntityConfig entityConfig) {
		super(entityConfig);
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
