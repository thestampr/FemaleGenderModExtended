/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.main.config.functions;

import com.wildfire.main.entitydata.Butts;
import com.wildfire.main.entitydata.PlayerConfig;

@FunctionalInterface
public interface ButtSetter<T> extends PlayerSetter<T> {
	Object set(Butts butts, T value);

	@Override
	default Object set(PlayerConfig player, T value) {
		return set(player.getButts(), value);
	}
}
