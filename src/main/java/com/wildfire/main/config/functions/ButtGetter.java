/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.main.config.functions;

import com.wildfire.main.entitydata.Butts;
import com.wildfire.main.entitydata.PlayerConfig;

@FunctionalInterface
public interface ButtGetter<T> extends PlayerGetter<T> {
	T get(Butts butts);

	@Override
	default T get(PlayerConfig player) {
		return get(player.getButts());
	}
}
