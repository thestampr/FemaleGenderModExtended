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

package com.wildfire.main.networking;

import com.mojang.datafixers.util.Function3;
import com.wildfire.main.config.enums.Gender;
import com.wildfire.main.entitydata.Breasts;
import com.wildfire.main.entitydata.Butts;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.uvs.UVDirection;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.main.uvs.UVQuad;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.EnumMap;
import java.util.UUID;

abstract class AbstractSyncPacket {

	// remember to update SyncHelloPacket.VERSION when modifying this codec if the changes result in a change
	// to the underlying packet structure
	protected static <T extends AbstractSyncPacket> StreamCodec<ByteBuf, T> codec(SyncPacketConstructor<T> constructor) {
		return StreamCodec.composite(
				CORE_PROFILE_CODEC, p -> p.core,
				BREAST_SYNC_CODEC, p -> p.breast,
				BUTT_SYNC_CODEC, p -> p.butt,
				constructor
		);
	}

	protected final UUID uuid;
	protected final CoreProfile core;
	protected final BreastSync breast;
	protected final ButtSync butt;

	protected AbstractSyncPacket(CoreProfile core, BreastSync breast, ButtSync butt) {
		this.uuid = core.uuid;
		this.core = core;
		this.breast = breast;
		this.butt = butt;
	}

	protected AbstractSyncPacket(PlayerConfig plr) {
		this(CoreProfile.from(plr), BreastSync.from(plr), ButtSync.from(plr));
	}

	// TODO add support for mannequins?
	protected void updatePlayerFromPacket(PlayerConfig plr) {
		core.applyTo(plr);
		breast.applyTo(plr);
		butt.applyTo(plr);
	}

	protected record CoreProfile(UUID uuid, Gender gender, float bustSize, boolean hurtSounds, float voicePitch,
	                             boolean realisticModel, boolean hipsEnabled) {
		private static CoreProfile from(PlayerConfig player) {
			return new CoreProfile(player.uuid, player.getGender(), player.getBustSize(), player.hasHurtSounds(),
					player.getVoicePitch(), player.usesRealisticModel(), player.hasHipsEnabled());
		}

		private void applyTo(PlayerConfig player) {
			player.updateGender(gender);
			player.updateBustSize(bustSize);
			player.updateHurtSounds(hurtSounds);
			player.updateVoicePitch(voicePitch);
			player.updateRealisticModel(realisticModel);
			player.updateHipsEnabled(hipsEnabled);
		}
	}

	private static final StreamCodec<ByteBuf, CoreProfile> CORE_PROFILE_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, CoreProfile::uuid,
			Gender.CODEC, CoreProfile::gender,
			ByteBufCodecs.FLOAT, CoreProfile::bustSize,
			ByteBufCodecs.BOOL, CoreProfile::hurtSounds,
			ByteBufCodecs.FLOAT, CoreProfile::voicePitch,
			ByteBufCodecs.BOOL, CoreProfile::realisticModel,
			ByteBufCodecs.BOOL, CoreProfile::hipsEnabled,
			CoreProfile::new
	);

	protected record PhysicsSettings(boolean enabled, boolean showInArmor, float bounceMultiplier, float floppyMultiplier) {
		private static final StreamCodec<ByteBuf, PhysicsSettings> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, PhysicsSettings::enabled,
				ByteBufCodecs.BOOL, PhysicsSettings::showInArmor,
				ByteBufCodecs.FLOAT, PhysicsSettings::bounceMultiplier,
				ByteBufCodecs.FLOAT, PhysicsSettings::floppyMultiplier,
				PhysicsSettings::new
		);

		private static PhysicsSettings breast(PlayerConfig player) {
			return new PhysicsSettings(player.hasBreastPhysics(), player.showBreastsInArmor(), player.getBounceMultiplier(), player.getFloppiness());
		}

		private static PhysicsSettings butt(PlayerConfig player) {
			return new PhysicsSettings(player.hasButtPhysics(), player.showButtInArmor(), player.getButtBounceMultiplier(), player.getButtFloppiness());
		}

		private void applyToBreasts(PlayerConfig player) {
			player.updateBreastPhysics(enabled);
			player.updateShowBreastsInArmor(showInArmor);
			player.updateBounceMultiplier(bounceMultiplier);
			player.updateFloppiness(floppyMultiplier);
		}

		private void applyToButt(PlayerConfig player) {
			player.updateButtPhysics(enabled);
			player.updateShowButtInArmor(showInArmor);
			player.updateButtBounceMultiplier(bounceMultiplier);
			player.updateButtFloppiness(floppyMultiplier);
		}
	}

	@FunctionalInterface
	protected interface SyncPacketConstructor<T extends AbstractSyncPacket> extends Function3<CoreProfile, BreastSync, ButtSync, T> {
	}

	public record UVLayouts(Layer skin, Layer overlay) {
		public static UVLayouts breasts(PlayerConfig plr) {
			return new UVLayouts(
					/*skin = */ new Layer(plr.getLeftBreastUVLayout().copy(), plr.getRightBreastUVLayout().copy()),
					/*overlay = */ new Layer(plr.getLeftBreastOverlayUVLayout().copy(), plr.getRightBreastOverlayUVLayout().copy())
			);
		}

		public static UVLayouts butt(PlayerConfig plr) {
			return new UVLayouts(
					new Layer(plr.getLeftButtUVLayout().copy(), plr.getRightButtUVLayout().copy()),
					new Layer(plr.getLeftButtOverlayUVLayout().copy(), plr.getRightButtOverlayUVLayout().copy())
			);
		}

		private void applyToBreasts(PlayerConfig plr) {
			plr.updateLeftBreastUVLayout(skin.left);
			plr.updateRightBreastUVLayout(skin.right);
			plr.updateLeftBreastOverlayUVLayout(overlay.left);
			plr.updateRightBreastOverlayUVLayout(overlay.right);
		}

		private void applyToButt(PlayerConfig plr) {
			plr.updateLeftButtUVLayout(skin.left);
			plr.updateRightButtUVLayout(skin.right);
			plr.updateLeftButtOverlayUVLayout(overlay.left);
			plr.updateRightButtOverlayUVLayout(overlay.right);
		}

		public record Layer(UVLayout left, UVLayout right) {
		}
	}

	static final StreamCodec<ByteBuf, UVLayout> UV_CODEC = ByteBufCodecs.map(
			size -> new EnumMap<>(UVDirection.class),
			UVDirection.PACKET_CODEC,
			UVQuad.PACKET_CODEC,
			UVDirection.values().length
	).map(UVLayout::new, UVLayout::getQuads);

	static final StreamCodec<ByteBuf, UVLayouts.Layer> UV_LAYER_CODEC = StreamCodec.composite(
			UV_CODEC, UVLayouts.Layer::left,
			UV_CODEC, UVLayouts.Layer::right,
			UVLayouts.Layer::new
	);

	static final StreamCodec<ByteBuf, UVLayouts> UV_LAYOUTS_CODEC = StreamCodec.composite(
			UV_LAYER_CODEC, UVLayouts::skin,
			UV_LAYER_CODEC, UVLayouts::overlay,
			UVLayouts::new
	);

	protected record BreastSync(PhysicsSettings physics, Breasts appearance, UVLayouts uvLayouts) {
		private static BreastSync from(PlayerConfig player) {
			return new BreastSync(PhysicsSettings.breast(player), player.getBreasts(), UVLayouts.breasts(player));
		}

		private void applyTo(PlayerConfig player) {
			physics.applyToBreasts(player);
			player.getBreasts().copyFrom(appearance);
			uvLayouts.applyToBreasts(player);
		}
	}

	private static final StreamCodec<ByteBuf, BreastSync> BREAST_SYNC_CODEC = StreamCodec.composite(
			PhysicsSettings.CODEC, BreastSync::physics,
			Breasts.CODEC, BreastSync::appearance,
			UV_LAYOUTS_CODEC, BreastSync::uvLayouts,
			BreastSync::new
	);

	protected record ButtSync(PhysicsSettings physics, Butts appearance, UVLayouts uvLayouts) {
		private static ButtSync from(PlayerConfig player) {
			return new ButtSync(PhysicsSettings.butt(player), player.getButts(), UVLayouts.butt(player));
		}

		private void applyTo(PlayerConfig player) {
			physics.applyToButt(player);
			player.getButts().copyFrom(appearance);
			uvLayouts.applyToButt(player);
		}
	}

	private static final StreamCodec<ByteBuf, ButtSync> BUTT_SYNC_CODEC = StreamCodec.composite(
			PhysicsSettings.CODEC, ButtSync::physics,
			Butts.CODEC, ButtSync::appearance,
			UV_LAYOUTS_CODEC, ButtSync::uvLayouts,
			ButtSync::new
	);

}
