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

import com.wildfire.gui.FakeGUIPlayer;
import com.wildfire.gui.GuiUtils;
import com.wildfire.main.GenderConfigs;
import com.wildfire.main.contributors.Contributor;
import com.wildfire.main.contributors.Contributors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class WildfireCreditsScreen extends BaseWildfireScreen {
	private static final int PANEL_MAX_WIDTH = 620;
	private static final int PANEL_MARGIN = 16;
	private static final int PANEL_TOP = 38;
	private static final int CARD_HEIGHT = 72;
	private static final int CARD_GAP = 7;
	private static final int SECTION_HEADER_HEIGHT = 20;
	private static final int SECTION_GAP = 9;
	private static final int SCROLL_STEP = 28;

	private final FakeGUIPlayer[] generalCredits = createCredits(false);
	private final FakeGUIPlayer[] translatorCredits = createCredits(true);
	private final FakeGUIPlayer[] extendedCredits = createCredits(Contributor.Role.MOD_EXTENDER);

	private int scrollOffset;
	private int maxScroll;

	public WildfireCreditsScreen(Screen parent, UUID uuid) {
		super(Component.translatable("wildfire_gender.credits.title"), parent, uuid);
	}

	private static FakeGUIPlayer[] createCredits(boolean translators) {
		return Contributors.getContributors().entrySet().stream()
				.filter(entry -> entry.getValue().name() != null)
				.filter(entry -> Boolean.TRUE.equals(entry.getValue().showInCredits()))
				.filter(entry -> entry.getValue().getRole() != Contributor.Role.MOD_EXTENDER)
				.filter(entry -> (entry.getValue().getRole() == Contributor.Role.TRANSLATOR) == translators)
				.sorted(Comparator
						.comparing((java.util.Map.Entry<UUID, Contributor> entry) -> entry.getValue().getRole())
						.thenComparing(entry -> entry.getValue().name(), String.CASE_INSENSITIVE_ORDER))
				.map(entry -> new FakeGUIPlayer(entry.getValue().name(), entry.getKey(), GenderConfigs.DEFAULT_FEMALE))
				.toArray(FakeGUIPlayer[]::new);
	}

	private static FakeGUIPlayer[] createCredits(Contributor.Role role) {
		return Contributors.getContributors().entrySet().stream()
				.filter(entry -> entry.getValue().name() != null)
				.filter(entry -> Boolean.TRUE.equals(entry.getValue().showInCredits()))
				.filter(entry -> entry.getValue().getRole() == role)
				.sorted(Comparator.comparing(entry -> entry.getValue().name(), String.CASE_INSENSITIVE_ORDER))
				.map(entry -> new FakeGUIPlayer(entry.getValue().name(), entry.getKey(), GenderConfigs.DEFAULT_FEMALE))
				.toArray(FakeGUIPlayer[]::new);
	}

	@Override
	protected void init() {
		addBackButton();
		scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
	}

	@Override
	public void tick() {
		Arrays.stream(generalCredits).forEach(FakeGUIPlayer::tick);
		Arrays.stream(translatorCredits).forEach(FakeGUIPlayer::tick);
		Arrays.stream(extendedCredits).forEach(FakeGUIPlayer::tick);
	}

	@Override
	public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		renderTransparentBackground(ctx);
	}

	@Override
	public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
		int panelWidth = Math.min(PANEL_MAX_WIDTH, width - PANEL_MARGIN * 2);
		int panelLeft = (width - panelWidth) / 2;
		int panelRight = panelLeft + panelWidth;
		int panelBottom = height - PANEL_MARGIN;
		int contentTop = PANEL_TOP + 43;
		int contentBottom = panelBottom - 8;
		int innerLeft = panelLeft + 12;
		int innerRight = panelRight - 12;
		int innerWidth = innerRight - innerLeft;
		int columns = innerWidth >= 500 ? 2 : 1;

		maxScroll = Math.max(0, getContentHeight(columns) - (contentBottom - contentTop));
		scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

		ctx.fill(panelLeft, PANEL_TOP, panelRight, panelBottom, 0xCC181818);
		ctx.fill(panelLeft, PANEL_TOP, panelRight, PANEL_TOP + 1, 0xFF555555);
		ctx.drawString(font, title, innerLeft, PANEL_TOP + 9, ARGB.opaque(0xFFFFFF), false);
		ctx.drawString(font, Component.translatable("wildfire_gender.credits.description"), innerLeft,
				PANEL_TOP + 24, ARGB.opaque(0x999999), false);

		ctx.enableScissor(innerLeft, contentTop, innerRight, contentBottom);
		int contentY = contentTop - scrollOffset;
		contentY = renderSection(ctx, Component.translatable("wildfire_gender.credits.general"), generalCredits,
				innerLeft, innerWidth, contentY, columns, mouseX, mouseY);
		contentY = renderSection(ctx, Component.translatable("wildfire_gender.credits.translators"), translatorCredits,
				innerLeft, innerWidth, contentY, columns, mouseX, mouseY);
		renderSection(ctx, Component.translatable("wildfire_gender.credits.extended"), extendedCredits,
				innerLeft, innerWidth, contentY, columns, mouseX, mouseY);
		ctx.disableScissor();

		renderScrollbar(ctx, panelRight - 5, contentTop, contentBottom);
		super.render(ctx, mouseX, mouseY, delta);
	}

	private int renderSection(GuiGraphics ctx, Component heading, FakeGUIPlayer[] credits, int left, int width,
	                          int y, int columns, int mouseX, int mouseY) {
		ctx.drawString(font, heading, left, y + 4, ARGB.opaque(0xD0D0D0), false);
		ctx.fill(left, y + 16, left + width, y + 17, 0x44555555);
		y += SECTION_HEADER_HEIGHT;

		int cardWidth = (width - CARD_GAP * (columns - 1)) / columns;
		for(int i = 0; i < credits.length; i++) {
			int column = i % columns;
			int row = i / columns;
			int cardX = left + column * (cardWidth + CARD_GAP);
			int cardY = y + row * (CARD_HEIGHT + CARD_GAP);
			renderCredit(ctx, credits[i], cardX, cardY, cardWidth, mouseX, mouseY);
		}

		int rows = (credits.length + columns - 1) / columns;
		return y + rows * (CARD_HEIGHT + CARD_GAP) + SECTION_GAP;
	}

	private void renderCredit(GuiGraphics ctx, FakeGUIPlayer credit, int x, int y, int width,
	                          int mouseX, int mouseY) {
		Contributor.Role role = credit.getRoleOrGeneric();
		int roleColor = role.getColor() & 0xFFFFFF;
		int subtleRoleColor = 0x24000000 | roleColor;
		int portraitColor = 0x34000000 | roleColor;

		ctx.fill(x, y, x + width, y + CARD_HEIGHT, subtleRoleColor);
		ctx.fill(x, y, x + 2, y + CARD_HEIGHT, 0xCC000000 | roleColor);
		ctx.fill(x + 6, y + 5, x + 66, y + CARD_HEIGHT - 5, portraitColor);

		int portraitX = x + 36;
		int portraitAnchorY = y + 64;
		ctx.enableScissor(x + 6, y + 5, x + 66, y + CARD_HEIGHT - 5);
		GuiUtils.drawEntityOnScreen(ctx, portraitX - 38, portraitAnchorY - 79, portraitX + 38,
				portraitAnchorY + 69, 46, mouseX, mouseY + 35, credit.getEntity());
		ctx.disableScissor();

		int textLeft = x + 75;
		int textRight = x + width - 8;
		GuiUtils.drawScrollableTextWithoutShadow(GuiUtils.Justify.LEFT, ctx, font, Component.literal(credit.getName()),
				textLeft, y + 12, textRight, y + 23, ARGB.opaque(roleColor));
		ctx.drawString(font, role.shortName(), textLeft, y + 31, ARGB.opaque(0xB8B8B8), false);

		String description = credit.getDescription();
		if(description != null && !description.isBlank()) {
			ctx.drawString(font, Component.literal(description), textLeft, y + 45, ARGB.opaque(0x888888), false);
		}
	}

	private int getContentHeight(int columns) {
		return getSectionHeight(generalCredits.length, columns)
				+ getSectionHeight(translatorCredits.length, columns)
				+ getSectionHeight(extendedCredits.length, columns);
	}

	private static int getSectionHeight(int creditCount, int columns) {
		int rows = (creditCount + columns - 1) / columns;
		return SECTION_HEADER_HEIGHT + rows * (CARD_HEIGHT + CARD_GAP) + SECTION_GAP;
	}

	private void renderScrollbar(GuiGraphics ctx, int x, int top, int bottom) {
		if(maxScroll <= 0) return;
		int viewportHeight = bottom - top;
		int thumbHeight = Math.max(18, viewportHeight * viewportHeight / (viewportHeight + maxScroll));
		int thumbTravel = viewportHeight - thumbHeight;
		int thumbTop = top + Math.round(thumbTravel * (scrollOffset / (float)maxScroll));
		ctx.fill(x, top, x + 2, bottom, 0x55333333);
		ctx.fill(x, thumbTop, x + 2, thumbTop + thumbHeight, 0xCC999999);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if(maxScroll > 0 && verticalAmount != 0) {
			scrollOffset = Mth.clamp(scrollOffset - (int)Math.round(verticalAmount * SCROLL_STEP), 0, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}
}
