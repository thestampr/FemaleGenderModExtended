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

package com.wildfire.gui.screen;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.wildfire.events.EntityHurtSoundEvent;
import com.wildfire.gui.WildfireButton;
import com.wildfire.gui.WildfireSlider;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.cloud.CloudSync;
import com.wildfire.main.cloud.SyncingTooFrequentlyException;
import com.wildfire.main.config.ClientConfig;
import com.wildfire.main.config.Configuration;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.networking.ServerboundSyncPacket;
import com.wildfire.main.networking.WildfireSync;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Environment(EnvType.CLIENT)
public class WildfireBreastCustomizationScreen extends BaseWildfireScreen {

	private static final int MAX_LAYOUT_WIDTH = 460;
	private static final int PAPER_DOLL_WIDTH = 144;
	private static final int PAPER_DOLL_RENDER_SIZE = 100;
	private static final int COLUMN_GAP = 16;
	private static final int PANEL_HEIGHT = 220;
	private static final float MAX_DRAG_X = 38;
	private static final float MAX_DRAG_Y = 30;
	private static final float MAX_VIEW_YAW = 45;
	private static final float MAX_VIEW_PITCH = 32;
	private static final float VIEW_YAW_SENSITIVITY = 0.55f;
	private static final float VIEW_PITCH_SENSITIVITY = 0.48f;

	private static final Component ENABLED = Component.translatable("wildfire_gender.label.enabled").withStyle(ChatFormatting.GREEN);
	private static final Component DISABLED = Component.translatable("wildfire_gender.label.disabled").withStyle(ChatFormatting.RED);

	private Tab currentTab = Tab.PROFILE;
	private ProfileSyncState profileSyncState = ProfileSyncState.READY;
	private int layoutX;
	private int layoutWidth;
	private int controlsX;
	private int controlsWidth;
	private int halfWidth;
	private int tabY;
	private int panelTop;
	private int panelHeight;
	private int paperDollWidth;
	private int paperDollCenterX;
	private int paperDollTop;
	private int paperDollBottom;
	private boolean draggingPaperDoll;
	private double dragAnchorX;
	private double dragAnchorY;
	private float dragStartOffsetX;
	private float dragStartOffsetY;
	private float paperDollOffsetX;
	private float paperDollOffsetY;
	private boolean rotatingPaperDoll;
	private double rotationAnchorX;
	private double rotationAnchorY;
	private float rotationStartYaw;
	private float rotationStartPitch;
	private float paperDollYaw;
	private float paperDollPitch;
	private float previewPhysicsX;
	private float previewPhysicsY;
	private float previewRotation;
	private float previewVelocityX;
	private float previewVelocityY;
	private float previewRotationVelocity;

	public WildfireBreastCustomizationScreen(Screen parent, UUID uuid) {
		super(Component.translatable("wildfire_gender.appearance_settings.title"), parent, uuid);
	}

	@Override
	public void init() {
		layoutWidth = Math.min(MAX_LAYOUT_WIDTH, Math.max(300, this.width - 24));
		layoutX = (this.width - layoutWidth) / 2;
		paperDollWidth = Math.min(PAPER_DOLL_WIDTH, Math.max(76, layoutWidth - 220 - COLUMN_GAP));
		controlsX = layoutX + paperDollWidth + COLUMN_GAP;
		controlsWidth = layoutWidth - paperDollWidth - COLUMN_GAP;
		halfWidth = controlsWidth / 2 - 2;

		panelHeight = Math.min(PANEL_HEIGHT, Math.max(194, this.height - 46));
		tabY = Math.max(18, (this.height - panelHeight - 28) / 2);
		panelTop = tabY + 28;
		paperDollCenterX = layoutX + paperDollWidth / 2;
		paperDollTop = panelTop - 4;
		paperDollBottom = Math.min(this.height - 8, panelTop + panelHeight);
		int tabWidth = (layoutWidth - 12) / 4;

		addBackButton();

		addTabButton(Tab.PROFILE, "wildfire_gender.breast_customization.tab_profile", layoutX, tabWidth);
		addTabButton(Tab.BREAST, "wildfire_gender.breast_customization.tab_breast", layoutX + tabWidth + 4, tabWidth);
		addTabButton(Tab.BUTT, "wildfire_gender.breast_customization.tab_butt", layoutX + (tabWidth + 4) * 2, tabWidth);
		addTabButton(Tab.MISC, "wildfire_gender.breast_customization.tab_miscellaneous",
				layoutX + (tabWidth + 4) * 3, layoutWidth - (tabWidth + 4) * 3);

		final int tabOffsetY = panelTop + 14;
		switch(currentTab) {
			case PROFILE -> initProfileTab(tabOffsetY);
			case BREAST -> initBreastTab(tabOffsetY);
			case BUTT -> initButtTab(tabOffsetY);
			case MISC -> initMiscTab(tabOffsetY);
		}

		if(minecraft.options.keyJump.isDown()) {
			minecraft.options.keyJump.setDown(false);
		}
	}

	private void addTabButton(Tab tab, String translationKey, int x, int width) {
		addButton(builder -> builder
				.message(() -> Component.translatable(translationKey))
				.position(x, tabY)
				.size(width, 20)
				.onPress(button -> {
					currentTab = tab;
					rebuildWidgets();
				})
				.active(currentTab != tab));
	}

	private void initProfileTab(final int tabOffsetY) {
		final var player = Objects.requireNonNull(getPlayer(), "getPlayer()");

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.profile.gender", player.getGender().getDisplayName()))
				.position(controlsX, tabOffsetY + 4)
				.size(controlsWidth, 20)
				.onPress(button -> {
					player.updateGender(player.getGender().next());
					player.save();
					button.updateMessage();
				}));

		addButton(builder -> builder
				.message(this::profileSyncButtonLabel)
				.position(controlsX, tabOffsetY + 36)
				.size(controlsWidth, 20)
				.onPress(button -> syncProfile(player, button))
				.active(profileSyncState != ProfileSyncState.SYNCING));

		var config = ClientConfig.INSTANCE;
		addRenderableWidget(Checkbox.builder(Component.translatable("wildfire_gender.profile.auto_sync"), font)
				.pos(controlsX, tabOffsetY + 84)
				.selected(config.get(ClientConfig.AUTOMATIC_CLOUD_SYNC))
				.onValueChange((checkbox, selected) -> {
					config.set(ClientConfig.AUTOMATIC_CLOUD_SYNC, selected);
					if(selected) config.set(ClientConfig.CLOUD_SYNC_ENABLED, true);
					config.save();
				})
				.maxWidth(controlsWidth)
				.build());

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.credits.title"))
				.position(controlsX, tabOffsetY + 116)
				.size(controlsWidth, 20)
				.onPress(button -> minecraft.setScreen(new WildfireCreditsScreen(this, playerUUID))));
	}

	private Component profileSyncButtonLabel() {
		String key = profileSyncState == ProfileSyncState.SYNCING
				? "wildfire_gender.profile.syncing"
				: "wildfire_gender.profile.sync";
		return Component.translatable(key);
	}

	private void syncProfile(PlayerConfig player, WildfireButton button) {
		profileSyncState = ProfileSyncState.SYNCING;
		button.active = false;
		button.updateMessage();

		player.save();
		boolean serverSyncAvailable = ServerboundSyncPacket.canSend();
		WildfireSync.sendToServer(player);

		if(!CloudSync.isAvailable()) {
			finishProfileSync(button, serverSyncAvailable ? ProfileSyncState.SERVER_SYNCED : ProfileSyncState.SAVED);
			return;
		}

		var config = ClientConfig.INSTANCE;
		config.set(ClientConfig.CLOUD_SYNC_ENABLED, true);
		config.save();
		CloudSync.sync(player).whenComplete((ignored, error) -> {
			var client = minecraft;
			if(client == null) return;
			client.execute(() -> {
				if(error == null) {
					player.needsCloudSync = false;
					finishProfileSync(button, ProfileSyncState.SYNCED);
					return;
				}

				Throwable cause = error;
				while(cause instanceof CompletionException && cause.getCause() != null) {
					cause = cause.getCause();
				}
				if(cause instanceof SyncingTooFrequentlyException) {
					finishProfileSync(button, ProfileSyncState.RATE_LIMITED);
				} else {
					WildfireGender.LOGGER.error("Failed to sync player profile", cause);
					finishProfileSync(button, ProfileSyncState.FAILED);
				}
			});
		});
	}

	private void finishProfileSync(WildfireButton button, ProfileSyncState state) {
		profileSyncState = state;
		button.active = true;
		button.updateMessage();
	}

	@Override
	public void removed() {
		draggingPaperDoll = false;
		rotatingPaperDoll = false;
		if(minecraft.options.keyJump.isDown()) {
			minecraft.options.keyJump.setDown(false);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if(!draggingPaperDoll) {
			float previousX = paperDollOffsetX;
			float previousY = paperDollOffsetY;
			paperDollOffsetX = easeToZero(paperDollOffsetX);
			paperDollOffsetY = easeToZero(paperDollOffsetY);
			applyPaperDollImpulse(paperDollOffsetX - previousX, paperDollOffsetY - previousY);
		}
		if(!rotatingPaperDoll) {
			float previousYaw = paperDollYaw;
			float previousPitch = paperDollPitch;
			paperDollYaw = easeToZero(paperDollYaw);
			paperDollPitch = easeToZero(paperDollPitch);
			applyPaperDollRotationImpulse(paperDollYaw - previousYaw, paperDollPitch - previousPitch);
		}

		// The body part reacts to movement velocity, never to the doll's absolute screen position.
		// Intensity controls displacement while Momentum controls spring tension and settling time.
		float intensity = previewIntensity();
		float momentumProgress = Mth.clamp((previewMomentum() - 0.25f) / 1.75f, 0, 1);
		float springScale = Mth.lerp(momentumProgress, 1.35f, 0.55f);
		float damping = Mth.lerp(momentumProgress, 0.55f, 0.9f);
		previewVelocityX = updateSpringVelocity(previewVelocityX, previewPhysicsX, 0, 0.38f * springScale, damping);
		previewVelocityY = updateSpringVelocity(previewVelocityY, previewPhysicsY, 0, 0.38f * springScale, damping);
		previewRotationVelocity = updateSpringVelocity(previewRotationVelocity, previewRotation, 0,
				0.28f * springScale, Math.min(0.92f, damping + 0.02f));
		float maxPosition = 0.5f + intensity * 0.75f;
		float maxRotation = 4f + intensity * 4f;
		previewPhysicsX = Mth.clamp(previewPhysicsX + previewVelocityX, -maxPosition, maxPosition);
		previewPhysicsY = Mth.clamp(previewPhysicsY + previewVelocityY, -maxPosition, maxPosition);
		previewRotation = Mth.clamp(previewRotation + previewRotationVelocity, -maxRotation, maxRotation);
	}

	private static float easeToZero(float value) {
		float eased = value * 0.68f;
		return Math.abs(eased) < 0.05f ? 0 : eased;
	}

	private static float updateSpringVelocity(float velocity, float position, float target,
	                                          float spring, float damping) {
		return (velocity + (target - position) * spring) * damping;
	}

	private float previewIntensity() {
		PlayerConfig player = getPlayer();
		if(player == null) return 1;
		float configured = currentTab == Tab.BUTT
				? player.getButtBounceMultiplier()
				: player.getBounceMultiplier();
		return Mth.clamp(configured * 3f, 0, 2f);
	}

	private float previewMomentum() {
		PlayerConfig player = getPlayer();
		if(player == null) return 0.75f;
		return currentTab == Tab.BUTT ? player.getButtFloppiness() : player.getFloppiness();
	}

	private void initBreastTab(final int tabOffsetY) {
		final var plr = Objects.requireNonNull(getPlayer(), "getPlayer()");
		final var breasts = plr.getBreasts();
		final var ref = new Object() {
			@UnknownNullability AbstractWidget bounceSlider, floppySlider, dualPhysics;
		};

		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.breast_size", Math.round(value * 1.25f * 100)))
				.position(controlsX, tabOffsetY + 4)
				.size(controlsWidth, 20)
				.range(Configuration.BUST_SIZE)
				.current(plr.getBustSize())
				.update(plr::updateBustSize)
				.step(0.01)
				.mouseStep(0.001));

		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.separation", Math.round((Math.round(value * 100f) / 100f) * 10)))
				.position(controlsX, tabOffsetY + 28)
				.size(halfWidth, 20)
				.range(Configuration.BREASTS_OFFSET_X)
				.current(breasts.getXOffset())
				.update(breasts::updateXOffset)
				.step(0.05)
				.mouseStep(0.05));

		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.height", Math.round((Math.round(value * 100f) / 100f) * 10)))
				.position(controlsX + halfWidth + 4, tabOffsetY + 28)
				.size(halfWidth, 20)
				.range(Configuration.BREASTS_OFFSET_Y)
				.current(breasts.getYOffset())
				.update(breasts::updateYOffset)
				.mouseStep(0.05));

		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.depth", Math.round((Math.round(value * 100f) / 100f) * 10)))
				.position(controlsX, tabOffsetY + 52)
				.size(halfWidth, 20)
				.range(Configuration.BREASTS_OFFSET_Z)
				.current(breasts.getZOffset())
				.update(breasts::updateZOffset)
				.step(0.1)
				.mouseStep(0.05));
		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.rotation", Math.round((Math.round(value * 100f) / 100f) * 100)))
				.position(controlsX + halfWidth + 4, tabOffsetY + 52)
				.size(halfWidth, 20)
				.range(Configuration.BREASTS_CLEAVAGE)
				.current(breasts.getCleavage())
				.update(breasts::updateCleavage)
				.step(0.1)
				.mouseStep(0.1));


		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.char_settings.physics", plr.hasBreastPhysics() ? ENABLED : DISABLED))
				.position(controlsX, tabOffsetY + 94)
				.size(halfWidth, 20)
				.onPress(button -> {
					plr.updateBreastPhysics(!plr.hasBreastPhysics());
					plr.save();
					button.updateMessage();
					boolean active = plr.hasBreastPhysics();
					ref.bounceSlider.active = active;
					ref.floppySlider.active = active;
					ref.dualPhysics.active = active;
				}));

		ref.dualPhysics = addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.breast_customization.dual_physics", Component.translatable(breasts.isUniboob() ? "wildfire_gender.label.no" : "wildfire_gender.label.yes")))
				.position(controlsX + halfWidth + 4, tabOffsetY + 94)
				.size(halfWidth, 20)
				.onPress(button -> {
					breasts.updateUniboob(!breasts.isUniboob());
					plr.save();
					button.updateMessage();
				})
				.active(plr.hasBreastPhysics()));

		ref.bounceSlider = addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.slider.bounce", Math.round((3 * value) * 100)))
				.position(controlsX, tabOffsetY + 118)
				.size(halfWidth, 20)
				.range(Configuration.BOUNCE_MULTIPLIER)
				.current(plr.getBounceMultiplier())
				.update(plr::updateBounceMultiplier)
				.step(0.005)
				.active(plr.hasBreastPhysics()));

		ref.floppySlider = addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.slider.floppy", Math.round(value * 100)))
				.position(controlsX + halfWidth + 4, tabOffsetY + 118)
				.size(halfWidth, 20)
				.range(Configuration.FLOPPY_MULTIPLIER)
				.current(plr.getFloppiness())
				.update(plr::updateFloppiness)
				.step(0.01)
				.active(plr.hasBreastPhysics()));

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.uv_editor"))
				.position(controlsX, tabOffsetY + 142)
				.size(halfWidth, 20)
				.onPress(button -> {
					minecraft.setScreen(new WildfireBreastUVEditorScreen(WildfireBreastCustomizationScreen.this, playerUUID,
							WildfireBreastUVEditorScreen.BodyPart.BREAST));
				}));

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.char_settings.hide_in_armor", plr.showBreastsInArmor() ? DISABLED : ENABLED))
				.position(controlsX + halfWidth + 4, tabOffsetY + 142)
				.size(halfWidth, 20)
				.onPress(button -> {
					plr.updateShowBreastsInArmor(!plr.showBreastsInArmor());
					plr.save();
					button.updateMessage();
				}));
	}

	private void initButtTab(final int tabOffsetY) {
		final var player = Objects.requireNonNull(getPlayer(), "getPlayer()");
		final var butts = player.getButts();
		final var ref = new Object() {
			@UnknownNullability AbstractWidget bounceSlider, floppySlider, dualPhysics;
		};

		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.butt_size", Math.round(value * 125)))
				.position(controlsX, tabOffsetY + 4)
				.size(controlsWidth, 20)
				.range(Configuration.BUTT_SIZE)
				.current(player.getButtSize())
				.update(player::updateButtSize)
				.step(0.01)
				.mouseStep(0.001));

		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.separation", Math.round(value * 10)))
				.position(controlsX, tabOffsetY + 28)
				.size(halfWidth, 20)
				.range(Configuration.BUTTS_OFFSET_X)
				.current(butts.getXOffset())
				.update(butts::updateXOffset)
				.mouseStep(0.05));

		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.height", Math.round(value * 10)))
				.position(controlsX + halfWidth + 4, tabOffsetY + 28)
				.size(halfWidth, 20)
				.range(Configuration.BUTTS_OFFSET_Y)
				.current(butts.getYOffset())
				.update(butts::updateYOffset)
				.mouseStep(0.05));

		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.depth", Math.round(value * 10)))
				.position(controlsX, tabOffsetY + 52)
				.size(halfWidth, 20)
				.range(Configuration.BUTTS_OFFSET_Z)
				.current(butts.getZOffset())
				.update(butts::updateZOffset)
				.mouseStep(0.05));

		addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.wardrobe.slider.rotation", Math.round(value * 100)))
				.position(controlsX + halfWidth + 4, tabOffsetY + 52)
				.size(halfWidth, 20)
				.range(Configuration.BUTTS_CLEAVAGE)
				.current(butts.getCleavage())
				.update(butts::updateCleavage)
				.mouseStep(0.01));

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.char_settings.butt_physics", player.hasButtPhysics() ? ENABLED : DISABLED))
				.position(controlsX, tabOffsetY + 94)
				.size(halfWidth, 20)
				.onPress(button -> {
					player.updateButtPhysics(!player.hasButtPhysics());
					player.save();
					button.updateMessage();
					boolean active = player.hasButtPhysics();
					ref.bounceSlider.active = active;
					ref.floppySlider.active = active;
					ref.dualPhysics.active = active;
				}));

		ref.dualPhysics = addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.butt_customization.dual_physics",
						Component.translatable(butts.isLinkedPhysics() ? "wildfire_gender.label.no" : "wildfire_gender.label.yes")))
				.position(controlsX + halfWidth + 4, tabOffsetY + 94)
				.size(halfWidth, 20)
				.onPress(button -> {
					butts.updateLinkedPhysics(!butts.isLinkedPhysics());
					player.save();
					button.updateMessage();
				})
				.active(player.hasButtPhysics()));

		ref.bounceSlider = addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.slider.bounce", Math.round(value * 300)))
				.position(controlsX, tabOffsetY + 118)
				.size(halfWidth, 20)
				.range(Configuration.BUTT_BOUNCE_MULTIPLIER)
				.current(player.getButtBounceMultiplier())
				.update(player::updateButtBounceMultiplier)
				.step(0.005)
				.active(player.hasButtPhysics()));

		ref.floppySlider = addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.slider.floppy", Math.round(value * 100)))
				.position(controlsX + halfWidth + 4, tabOffsetY + 118)
				.size(halfWidth, 20)
				.range(Configuration.BUTT_FLOPPY_MULTIPLIER)
				.current(player.getButtFloppiness())
				.update(player::updateButtFloppiness)
				.step(0.01)
				.active(player.hasButtPhysics()));

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.uv_editor"))
				.position(controlsX, tabOffsetY + 142)
				.size(halfWidth, 20)
				.onPress(button -> minecraft.setScreen(new WildfireBreastUVEditorScreen(
						WildfireBreastCustomizationScreen.this, playerUUID, WildfireBreastUVEditorScreen.BodyPart.BUTT))));

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.char_settings.hide_in_armor", player.showButtInArmor() ? DISABLED : ENABLED))
				.position(controlsX + halfWidth + 4, tabOffsetY + 142)
				.size(halfWidth, 20)
				.onPress(button -> {
					player.updateShowButtInArmor(!player.showButtInArmor());
					player.save();
					button.updateMessage();
				}));
	}

	private void initMiscTab(final int tabOffsetY) {
		final var plr = Objects.requireNonNull(getPlayer(), "getPlayer()");
		final var config = ClientConfig.INSTANCE;
		final var ref = new Object() {
			@UnknownNullability
			AbstractWidget pitchSlider;
		};

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.char_settings.hurt_sounds", plr.hasHurtSounds() ? ENABLED : DISABLED))
				.position(controlsX, tabOffsetY - 2)
				.size(controlsWidth, 20)
				.onPress(button -> {
					plr.updateHurtSounds(!plr.hasHurtSounds());
					plr.save();
					ref.pitchSlider.active = plr.hasHurtSounds();
					button.updateMessage();
				})
				.tooltip(Tooltip.create(Component.translatable("wildfire_gender.tooltip.hurt_sounds"))));

		ref.pitchSlider = addSlider(builder -> builder
				.message(value -> Component.translatable("wildfire_gender.slider.voice_pitch", Math.round(value * 100)))
				.position(controlsX, tabOffsetY + 22)
				.size(halfWidth, 20)
				.range(Configuration.VOICE_PITCH)
				.current(plr.getVoicePitch())
				.update(plr::updateVoicePitch)
				.save(value -> {
					plr.save();
					var clientPlayer = Objects.requireNonNull(minecraft).player;
					if(clientPlayer != null) {
						EntityHurtSoundEvent.EVENT.invoker().onHurt(clientPlayer, clientPlayer.damageSources().generic());
					}
				})
				.step(0.01)
				.active(plr.hasHurtSounds()));

		addButton(builder -> builder
				.message(() -> {
					var value = ClientConfig.INSTANCE.get(ClientConfig.ARMOR_PHYSICS_OVERRIDE);
					return Component.translatable("wildfire_gender.char_settings.override_armor_physics", value ? ENABLED : DISABLED);
				})
				.position(controlsX, tabOffsetY + 46)
				.size(halfWidth, 20)
				.onPress(button -> {
					ClientConfig.INSTANCE.toggle(ClientConfig.ARMOR_PHYSICS_OVERRIDE);
					ClientConfig.INSTANCE.save();
					button.updateMessage();
				})
				.tooltip(Tooltip.create(Component.translatable("wildfire_gender.tooltip.override_armor_physics.line1")
						.append("\n\n")
						.append(Component.translatable("wildfire_gender.tooltip.override_armor_physics.line2")))));

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.char_settings.show_armor_stat", config.get(ClientConfig.ARMOR_STAT) ? ENABLED : DISABLED))
				.position(controlsX + halfWidth + 4, tabOffsetY + 46)
				.size(halfWidth, 20)
				.onPress(button -> {
					config.toggle(ClientConfig.ARMOR_STAT);
					config.save();
					button.updateMessage();
				}));

		addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.misc.holiday_themes", plr.hasHolidayThemes() ? ENABLED : DISABLED))
				.position(controlsX, tabOffsetY + 70)
				.size(controlsWidth, 20)
				.onPress(button -> {
					plr.updateHolidayThemes(!plr.hasHolidayThemes());
					plr.save();
					button.updateMessage();
				})
				.tooltip(Tooltip.create(Component.translatable("wildfire_gender.tooltip.holiday_themes.line1"))));

		var realisticCheckbox = Checkbox.builder(Component.translatable("wildfire_gender.misc.realistic"), font)
				.pos(controlsX, tabOffsetY + 112)
				.selected(plr.usesRealisticModel())
				.onValueChange((checkbox, selected) -> {
					plr.updateRealisticModel(selected);
					plr.save();
				})
				.maxWidth(controlsWidth)
				.build();
		realisticCheckbox.setTooltip(Tooltip.create(Component.translatable("wildfire_gender.tooltip.realistic")));
		addRenderableWidget(realisticCheckbox);

		var armorStandPhysicsCheckbox = Checkbox.builder(
				Component.translatable("wildfire_gender.misc.armor_stand_physics"), font)
				.pos(controlsX, tabOffsetY + 136)
				.selected(config.get(ClientConfig.ARMOR_STAND_PHYSICS))
				.onValueChange((checkbox, selected) -> {
					config.set(ClientConfig.ARMOR_STAND_PHYSICS, selected);
					config.save();
				})
				.maxWidth(controlsWidth)
				.build();
		armorStandPhysicsCheckbox.setTooltip(Tooltip.create(
				Component.translatable("wildfire_gender.tooltip.armor_stand_physics")));
		addRenderableWidget(armorStandPhysicsCheckbox);
	}

	@Override
	public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		this.renderTransparentBackground(ctx);
		if(getPlayer() == null) return;

		ctx.fill(controlsX - 8, panelTop, controlsX + controlsWidth + 8, panelTop + panelHeight, 0xCC181818);
		ctx.fill(controlsX - 7, panelTop + 1, controlsX + controlsWidth + 7, panelTop + panelHeight - 1, 0x662C2C2C);
		ctx.drawCenteredString(font, getTitle(), controlsX + controlsWidth / 2, tabY - 18, 0xFFFFFF);

		if(currentTab == Tab.BREAST || currentTab == Tab.BUTT) {
			ctx.drawString(font, Component.translatable("wildfire_gender.section.customize"),
					controlsX, panelTop + 5, 0xFFC6C6C6, false);
			ctx.drawString(font, Component.translatable("wildfire_gender.section.physics"),
					controlsX, panelTop + 91, 0xFFC6C6C6, false);
		} else if(currentTab == Tab.PROFILE) {
			ctx.drawString(font, Component.translatable("wildfire_gender.breast_customization.tab_profile"),
					controlsX, panelTop + 5, 0xFFC6C6C6, false);
			ctx.drawCenteredString(font, Component.translatable(profileSyncState.statusKey),
					controlsX + controlsWidth / 2, panelTop + 76, profileSyncState.color);
		} else if(currentTab == Tab.MISC) {
			ctx.drawString(font, Component.translatable("wildfire_gender.section.experimental"),
					controlsX, panelTop + 111, 0xFFD9A45B, false);
		}

		boolean rearView = currentTab == Tab.BUTT;
		renderPaperDoll(ctx, paperDollCenterX, paperDollTop, paperDollBottom, paperDollWidth,
				Math.min(PAPER_DOLL_RENDER_SIZE, paperDollBottom - paperDollTop - 8), rearView,
				paperDollOffsetX, paperDollOffsetY, previewPhysicsX, previewPhysicsY, previewRotation,
				paperDollYaw, paperDollPitch);
		if(rotatingPaperDoll) {
			ctx.requestCursor(CursorTypes.RESIZE_ALL);
		} else if(draggingPaperDoll || isOverPaperDoll(mouseX, mouseY)) {
			ctx.requestCursor(CursorTypes.RESIZE_ALL);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if(click.button() == 0 && isOverPaperDoll(click.x(), click.y())) {
			draggingPaperDoll = true;
			dragAnchorX = click.x();
			dragAnchorY = click.y();
			dragStartOffsetX = paperDollOffsetX;
			dragStartOffsetY = paperDollOffsetY;
			return true;
		}
		if(click.button() == 1 && isOverPaperDoll(click.x(), click.y())) {
			rotatingPaperDoll = true;
			rotationAnchorX = click.x();
			rotationAnchorY = click.y();
			rotationStartYaw = paperDollYaw;
			rotationStartPitch = paperDollPitch;
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if(rotatingPaperDoll && event.button() == 1) {
			float previousYaw = paperDollYaw;
			float previousPitch = paperDollPitch;
			// Match vanilla's inventory preview: dragging right/down turns the doll right/down.
			paperDollYaw = Mth.clamp(rotationStartYaw - (float)(event.x() - rotationAnchorX) * VIEW_YAW_SENSITIVITY,
					-MAX_VIEW_YAW, MAX_VIEW_YAW);
			paperDollPitch = Mth.clamp(rotationStartPitch - (float)(event.y() - rotationAnchorY) * VIEW_PITCH_SENSITIVITY,
					-MAX_VIEW_PITCH, MAX_VIEW_PITCH);
			applyPaperDollRotationImpulse(paperDollYaw - previousYaw, paperDollPitch - previousPitch);
			return true;
		}
		if(!draggingPaperDoll || event.button() != 0) {
			return super.mouseDragged(event, dragX, dragY);
		}

		float nextX = Mth.clamp(dragStartOffsetX + (float)(event.x() - dragAnchorX), -MAX_DRAG_X, MAX_DRAG_X);
		float nextY = Mth.clamp(dragStartOffsetY + (float)(event.y() - dragAnchorY), -MAX_DRAG_Y, MAX_DRAG_Y);
		float movementX = nextX - paperDollOffsetX;
		float movementY = nextY - paperDollOffsetY;
		paperDollOffsetX = nextX;
		paperDollOffsetY = nextY;

		applyPaperDollImpulse(movementX, movementY);
		return true;
	}

	private void applyPaperDollImpulse(float movementX, float movementY) {
		float intensity = previewIntensity();
		previewVelocityX -= movementX * 0.025f * intensity;
		previewVelocityY -= movementY * 0.025f * intensity;
		previewRotationVelocity += (currentTab == Tab.BUTT ? movementY : -movementX) * 0.08f * intensity;
	}

	private void applyPaperDollRotationImpulse(float yawDelta, float pitchDelta) {
		// Mirror the live physics inputs: yaw produces sideways inertia and twist,
		// while pitch produces vertical inertia. Both settle through the same spring.
		float intensity = previewIntensity();
		previewVelocityX += yawDelta * 0.025f * intensity;
		previewVelocityY += pitchDelta * 0.03f * intensity;
		previewRotationVelocity -= yawDelta * 0.1f * intensity;
		previewRotationVelocity += pitchDelta * (currentTab == Tab.BUTT ? 0.05f : -0.05f) * intensity;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent arg) {
		if(arg.button() == 0 && draggingPaperDoll) {
			draggingPaperDoll = false;
			return true;
		}
		if(arg.button() == 1 && rotatingPaperDoll) {
			rotatingPaperDoll = false;
			return true;
		}
		//Ensure all sliders are saved
		children().forEach(child -> {
			if(child instanceof WildfireSlider slider) {
				slider.save();
			}
		});
		return super.mouseReleased(arg);
	}

	private boolean isOverPaperDoll(double mouseX, double mouseY) {
		double left = paperDollCenterX - paperDollWidth / 2.0 + paperDollOffsetX;
		double right = paperDollCenterX + paperDollWidth / 2.0 + paperDollOffsetX;
		double top = paperDollTop + paperDollOffsetY;
		double bottom = paperDollBottom + paperDollOffsetY;
		return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
	}

	private enum Tab {
		PROFILE,
		BREAST,
		BUTT,
		MISC
	}

	private enum ProfileSyncState {
		READY("wildfire_gender.profile.sync_ready", 0xFFAAAAAA),
		SYNCING("wildfire_gender.profile.syncing", 0xFFFFFF55),
		SYNCED("wildfire_gender.profile.sync_success", 0xFF55FF55),
		SERVER_SYNCED("wildfire_gender.profile.server_sync_success", 0xFF55FF55),
		SAVED("wildfire_gender.profile.saved_local", 0xFFFFAA00),
		RATE_LIMITED("wildfire_gender.profile.sync_rate_limited", 0xFFFFAA00),
		FAILED("wildfire_gender.profile.sync_failed", 0xFFFF5555);

		private final String statusKey;
		private final int color;

		ProfileSyncState(String statusKey, int color) {
			this.statusKey = statusKey;
			this.color = color;
		}
	}
}
