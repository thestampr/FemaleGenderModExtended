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

import com.wildfire.main.WildfireGender;
import com.wildfire.main.entitydata.PlayerConfig;
import io.netty.buffer.ByteBuf;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class ClientboundSyncPacket extends AbstractSyncPacket implements CustomPacketPayload {

	public static final Type<ClientboundSyncPacket> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(WildfireGender.MODID, "extended/sync"));
	public static final StreamCodec<ByteBuf, ClientboundSyncPacket> CODEC = codec(ClientboundSyncPacket::new);

	public ClientboundSyncPacket(PlayerConfig plr) {
		super(plr);
	}

	private ClientboundSyncPacket(CoreProfile core, BreastSync breast, ButtSync butt) {
		super(core, breast, butt);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	public static boolean canSend(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, ID);
	}

	@Environment(EnvType.CLIENT)
	public void handle(ClientPlayNetworking.Context context) {
		if(context.player().getUUID().equals(uuid)) {
			WildfireGender.LOGGER.warn("Ignoring sync packet referring to the client player");
			return;
		}

		PlayerConfig plr = WildfireGender.getOrAddPlayerById(uuid);
		updatePlayerFromPacket(plr);
		plr.syncStatus = PlayerConfig.SyncStatus.SYNCED;
	}
}
