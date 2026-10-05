/*
 * Wildfire's Female Gender Mod is a female gender mod created for Minecraft.
 * Copyright (C) 2023-present WildfireRomeo
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */
package com.wildfire.gui.screen;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.wildfire.gui.GuiUtils;
import com.wildfire.gui.WildfireButton;
import com.wildfire.main.config.Configuration;
import com.wildfire.main.entitydata.PlayerConfig;
import com.wildfire.main.uvs.BreastTypes;
import com.wildfire.main.uvs.UVDirection;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.main.uvs.UVQuad;
import com.wildfire.main.uvs.UvEditorFeedback;
import com.wildfire.render.BreastSide;
import com.wildfire.render.GenderRenderState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A two-pane pixel workbench for previewing and editing body-part UV layouts. */
public class WildfireBreastUVEditorScreen extends BaseWildfireScreen {
	private static final int ACCENT = UvEditorFeedback.SELECTION;
	private static final int PANEL = 0xCC202328;
	private static final int CANVAS = 0xFF141820;
	private static final int BORDER = 0xFF4B5360;
	private static final int SHORTCUT_BORDER = 0xFF9CA6B3;
	private static final int BUTTON_BACKGROUND_ALPHA = 150;
	private static final int TEXTURE_SIZE = 64;
	private static final float MIN_ZOOM = 1.5f;
	private static final float MAX_ZOOM = 12f;
	private static final float MESH_HIT_VERTICAL_OFFSET = 0.27f;

	private final BodyPart bodyPart;
	private final EnumMap<BreastTypes, UVLayout> originalLayouts = new EnumMap<>(BreastTypes.class);
	private final EnumMap<BreastTypes, UVLayout> workingLayouts = new EnumMap<>(BreastTypes.class);
	private final LinkedHashSet<FaceRef> selectedFaces = new LinkedHashSet<>();

	private BodySide selectedSide = BodySide.LEFT;
	private LayerFilter layerFilter = LayerFilter.ALL;
	private DragMode dragMode = DragMode.NONE;
	private final Map<FaceRef, UVQuad> dragStartQuads = new LinkedHashMap<>();
	private @Nullable SelectionBounds dragStartBounds;
	private double dragStartMouseX;
	private double dragStartMouseY;
	private float dragStartPanX;
	private float dragStartPanY;

	private int contentX;
	private int contentTop;
	private int contentBottom;
	private int paperX;
	private int paperWidth;
	private int canvasX;
	private int canvasY;
	private int canvasWidth;
	private int canvasHeight;
	private int previousCanvasWidth;
	private int previousCanvasHeight;
	private float canvasZoom = 4f;
	private float canvasPanX;
	private float canvasPanY;
	private boolean canvasViewInitialized;
	private boolean dirty;

	private @Nullable WildfireButton cancelButton;
	private @Nullable WildfireButton applyButton;

	public WildfireBreastUVEditorScreen(Screen parent, UUID uuid, BodyPart bodyPart) {
		super(Component.translatable(bodyPart.titleKey), parent, uuid);
		this.bodyPart = bodyPart;
	}

	@Override
	public void init() {
		ensureWorkingLayouts();
		calculateLayout();
		addBackButton();

		WildfireButton resetButton = addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.uv_editor.reset_defaults_all"))
				.position(width - 150, 8)
				.size(142, 20)
				.onPress(button -> resetWorkingLayouts()));
		resetButton.setBackgroundAlpha(BUTTON_BACKGROUND_ALPHA);

		int tabWidth = Math.min(108, Math.max(72, (Math.min(width - 32, 340) - 8) / 3));
		int tabsWidth = tabWidth * 3 + 8;
		int tabX = (width - tabsWidth) / 2;
		addFilterButton(LayerFilter.ALL, tabX, tabWidth);
		addFilterButton(LayerFilter.BODY, tabX + tabWidth + 4, tabWidth);
		addFilterButton(LayerFilter.OUTER, tabX + (tabWidth + 4) * 2, tabWidth);

		cancelButton = addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.cancel"))
				.position(width - 174, height - 28)
				.size(80, 20)
				.onPress(button -> cancelChanges()));
		cancelButton.setBackgroundAlpha(BUTTON_BACKGROUND_ALPHA);
		applyButton = addButton(builder -> builder
				.message(() -> Component.translatable("wildfire_gender.apply"))
				.position(width - 90, height - 28)
				.size(82, 20)
				.onPress(button -> applyChanges()));
		applyButton.setBackgroundAlpha(BUTTON_BACKGROUND_ALPHA);
		updateActionButtons();
	}

	private void calculateLayout() {
		int contentWidth = Math.min(960, Math.max(300, width - 32));
		contentX = (width - contentWidth) / 2;
		contentTop = 62;
		contentBottom = Math.max(contentTop + 140, height - 36);
		int gap = 14;
		paperWidth = Mth.clamp(Math.round(contentWidth * 0.34f), 145, 270);
		paperX = contentX;
		canvasX = paperX + paperWidth + gap;
		canvasY = contentTop;
		canvasWidth = Math.max(140, contentX + contentWidth - canvasX);
		canvasHeight = contentBottom - contentTop;
		if(canvasWidth != previousCanvasWidth || canvasHeight != previousCanvasHeight) {
			canvasViewInitialized = false;
			previousCanvasWidth = canvasWidth;
			previousCanvasHeight = canvasHeight;
		}
	}

	private void addFilterButton(LayerFilter filter, int x, int buttonWidth) {
		WildfireButton tabButton = addButton(builder -> builder
				.message(() -> Component.translatable(filter.translationKey(bodyPart)))
				.position(x, 34)
				.size(buttonWidth, 20)
				.renderer((tab, context, mouseX, mouseY, delta) ->
						renderFilterLabel(tab, context, layerFilter == filter))
				.onPress(button -> {
					if(layerFilter == filter) return;
					layerFilter = filter;
					selectedFaces.retainAll(Set.copyOf(activeFaces()));
					rebuildWidgets();
				}));
		tabButton.setBackgroundAlpha(BUTTON_BACKGROUND_ALPHA);
	}

	private void renderFilterLabel(WildfireButton button, GuiGraphics context, boolean selected) {
		GuiUtils.drawScrollableTextWithoutShadow(GuiUtils.Justify.CENTER, context, font,
				button.getMessage(), button.getX() + 3, button.getY(),
				button.getX() + button.getWidth() - 3, button.getY() + button.getHeight(),
				selected ? ACCENT : 0xFFFFFF);
	}

	private void ensureWorkingLayouts() {
		if(!workingLayouts.isEmpty()) return;
		PlayerConfig player = Objects.requireNonNull(getPlayer(), "getPlayer()");
		for(BreastTypes type : bodyPart.types()) {
			UVLayout layout = layoutFromPlayer(player, type).copy();
			originalLayouts.put(type, layout.copy());
			workingLayouts.put(type, layout);
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		if(minecraft.player == null || getPlayer() == null) return;
		renderTransparentBackground(context);
		context.drawCenteredString(font, getTitle(), width / 2, 14, 0xFFFFFFFF);

		context.fill(paperX, contentTop, paperX + paperWidth, contentBottom, PANEL);
		context.fill(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, CANVAS);
		drawPaperDoll(context, mouseX, mouseY);
		drawTextureCanvas(context, mouseX, mouseY);
		drawUsageHint(context);
		updateActionButtons();
		super.render(context, mouseX, mouseY, delta);
	}

	private void drawPaperDoll(GuiGraphics context, int mouseX, int mouseY) {
		int paperBottom = contentBottom;
		int entitySize = paperDollScale();
		GenderRenderState.PreviewLayer previewLayer = layerFilter.previewLayer;
		PaperHitArea hitArea = paperHitArea();
		BodySide hoveredSide = hitArea.left().contains(mouseX, mouseY) ? BodySide.LEFT
				: hitArea.right().contains(mouseX, mouseY) ? BodySide.RIGHT : null;
		GuiUtils.drawEntityFacing(context, paperX + 4, contentTop + 4, paperX + paperWidth - 4,
				paperBottom - 4, entitySize, bodyPart == BodyPart.BUTT, minecraft.player,
				0, 0, 0, 0, 0, previewLayer, state -> {
					if(bodyPart == BodyPart.BREAST) {
						state.applyBreastUvPreview(layout(BreastTypes.LEFT), layout(BreastTypes.RIGHT),
								layout(BreastTypes.LEFT_OVERLAY), layout(BreastTypes.RIGHT_OVERLAY));
					} else {
						state.applyButtUvPreview(layout(BreastTypes.LEFT_BUTT), layout(BreastTypes.RIGHT_BUTT),
								layout(BreastTypes.LEFT_BUTT_OVERLAY), layout(BreastTypes.RIGHT_BUTT_OVERLAY));
					}
					state.applyUvPreviewSelection(modelSideFor(selectedSide),
							hoveredSide == null ? null : modelSideFor(hoveredSide));
				});

		if(hoveredSide != null) context.requestCursor(CursorTypes.POINTING_HAND);
	}

	private int paperDollScale() {
		return Mth.clamp(Math.min(paperWidth - 18, contentBottom - contentTop - 18), 62, 116);
	}

	private PaperHitArea paperHitArea() {
		int centerX = paperX + paperWidth / 2;
		int areaWidth = Math.max(28, Math.min(48, paperWidth / 3));
		int areaHeight = bodyPart == BodyPart.BREAST ? 42 : 48;
		float yRatio = bodyPart == BodyPart.BREAST ? 0.34f : 0.55f;
		int entitySize = paperDollScale();
		int centerY = contentTop + Math.round((contentBottom - contentTop) * yRatio)
				+ Math.round(entitySize * MESH_HIT_VERTICAL_OFFSET);
		return new PaperHitArea(
				new ScreenRect(centerX - areaWidth, centerY - areaHeight / 2, centerX, centerY + areaHeight / 2),
				new ScreenRect(centerX, centerY - areaHeight / 2, centerX + areaWidth, centerY + areaHeight / 2));
	}

	private void drawTextureCanvas(GuiGraphics context, int mouseX, int mouseY) {
		ensureCanvasView();
		int textureX = Math.round(canvasX + canvasPanX);
		int textureY = Math.round(canvasY + canvasPanY);
		int drawSize = Math.round(TEXTURE_SIZE * canvasZoom);
		context.enableScissor(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight);
		context.blit(RenderPipelines.GUI_TEXTURED, minecraft.player.getSkin().body().id(),
				textureX, textureY, 0, 0, drawSize, drawSize, drawSize, drawSize);
		drawPixelGrid(context, textureX, textureY);

		@Nullable FaceRef hoveredFace = faceAt(mouseX, mouseY);
		for(FaceRef face : activeFaces()) {
			UVQuad quad = quad(face);
			if(isHidden(quad)) continue;
			ScreenRect rect = screenRect(quad);
			boolean selected = selectedFaces.contains(face);
			boolean hovered = face.equals(hoveredFace);
			UvEditorFeedback.State feedbackState = selected ? UvEditorFeedback.State.ACTIVE
					: hovered ? UvEditorFeedback.State.HOVERED : UvEditorFeedback.State.IDLE;
			int color = UvEditorFeedback.faceColor(face.direction(), !face.type().isLeft(), feedbackState);
			drawOutline(context, rect.x1(), rect.y1(), rect.x2(), rect.y2(), color, selected ? 2 : 1);
			if(selected || hovered) drawFaceLabel(context, face, rect, color);
		}

		SelectionBounds selection = selectionBounds();
		if(selection != null) {
			ScreenRect selectedRect = screenRect(selection);
			drawOutline(context, selectedRect.x1(), selectedRect.y1(), selectedRect.x2(), selectedRect.y2(), ACCENT, 1);
			drawResizeHandle(context, selectedRect);
		}
		context.disableScissor();
		drawOutline(context, canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, BORDER, 1);
		context.drawString(font, Math.round(canvasZoom * 100) + "%", canvasX + 7, canvasY + 7, 0xFFD5DBE4, true);

		if(hoveredFace != null) {
			UVQuad quad = quad(hoveredFace);
			int tooltipColor = UvEditorFeedback.faceColor(hoveredFace.direction(),
					!hoveredFace.type().isLeft(), UvEditorFeedback.State.ACTIVE) & 0xFFFFFF;
			List<FormattedCharSequence> tooltip = new ArrayList<>();
			tooltip.add(hoveredFace.direction().getDirectionText(hoveredFace.type())
					.copy().withColor(tooltipColor).getVisualOrderText());
			tooltip.add(Component.literal("[" + quad.x1() + ", " + quad.y1() + ", "
					+ quad.x2() + ", " + quad.y2() + "]").withColor(tooltipColor).getVisualOrderText());
			context.setTooltipForNextFrame(tooltip, mouseX, mouseY);
			context.requestCursor(CursorTypes.POINTING_HAND);
		} else if(isOverResizeHandle(mouseX, mouseY)) {
			context.requestCursor(CursorTypes.RESIZE_ALL);
		}
	}

	private void ensureCanvasView() {
		if(canvasViewInitialized) return;
		float fit = Math.min((canvasWidth - 24f) / TEXTURE_SIZE, (canvasHeight - 24f) / TEXTURE_SIZE);
		canvasZoom = Mth.clamp(fit, MIN_ZOOM, 7f);
		canvasPanX = (canvasWidth - TEXTURE_SIZE * canvasZoom) / 2f;
		canvasPanY = (canvasHeight - TEXTURE_SIZE * canvasZoom) / 2f;
		canvasViewInitialized = true;
	}

	private void drawPixelGrid(GuiGraphics context, int textureX, int textureY) {
		if(canvasZoom < 4f) return;
		int endX = Math.round(textureX + TEXTURE_SIZE * canvasZoom);
		int endY = Math.round(textureY + TEXTURE_SIZE * canvasZoom);
		for(int pixel = 0; pixel <= TEXTURE_SIZE; pixel++) {
			int x = Math.round(textureX + pixel * canvasZoom);
			int y = Math.round(textureY + pixel * canvasZoom);
			context.fill(x, textureY, x + 1, endY, 0x29344052);
			context.fill(textureX, y, endX, y + 1, 0x29344052);
		}
	}

	private void drawFaceLabel(GuiGraphics context, FaceRef face, ScreenRect rect, int color) {
		String label = face.direction().getShortName();
		int x = (rect.x1() + rect.x2() - font.width(label)) / 2;
		int y = (rect.y1() + rect.y2() - font.lineHeight) / 2;
		context.drawString(font, label, x, y, color, true);
	}

	private void drawResizeHandle(GuiGraphics context, ScreenRect rect) {
		int x = rect.x2();
		int y = rect.y2();
		context.fill(x - 3, y - 3, x + 4, y + 4, 0xFF141820);
		context.fill(x - 2, y - 2, x + 3, y + 3, ACCENT);
	}

	private void drawUsageHint(GuiGraphics context) {
		UsageHint[] hints = {
				new UsageHint(MousePart.LEFT, false, false, "wildfire_gender.uv_editor.hint.select"),
				new UsageHint(MousePart.LEFT, true, false, "wildfire_gender.uv_editor.hint.multi"),
				new UsageHint(MousePart.LEFT, false, true, "wildfire_gender.uv_editor.hint.move"),
				new UsageHint(MousePart.RIGHT, false, true, "wildfire_gender.uv_editor.hint.pan"),
				new UsageHint(MousePart.WHEEL, false, false, "wildfire_gender.uv_editor.hint.zoom")
		};
		int right = dirty ? width - 182 : width - 8;
		int availableWidth = right - contentX;
		int rows = 1;
		int rowWidth = 0;
		for(UsageHint hint : hints) {
			int itemWidth = usageHintWidth(hint);
			if(rowWidth > 0 && rowWidth + itemWidth > availableWidth) {
				rows++;
				rowWidth = 0;
			}
			rowWidth += itemWidth;
		}
		int x = contentX;
		int buttonY = cancelButton == null ? height - 28 : cancelButton.getY();
		int buttonHeight = cancelButton == null ? 20 : cancelButton.getHeight();
		int y = buttonY + (buttonHeight - 13) / 2 - (Math.min(rows, 2) - 1) * 14;
		for(UsageHint hint : hints) {
			Component action = Component.translatable(hint.actionKey());
			int itemWidth = usageHintWidth(hint);
			if(x + itemWidth > right && x > contentX) {
				x = contentX;
				y += 14;
			}
			if(x + itemWidth > right || y + 14 > height - 2) break;
			if(hint.ctrl()) {
				context.fill(x, y, x + 22, y + 13, 0xFF303741);
				drawOutline(context, x, y, x + 22, y + 13, SHORTCUT_BORDER, 1);
				context.fill(x, y + 10, x + 22, y + 13, SHORTCUT_BORDER);
				context.drawString(font, "Ctrl", x + 2, y + 2, 0xFFD5DBE4, false);
				x += 26;
				context.drawString(font, "+", x, y + 2, 0xFFD5DBE4, false);
				x += font.width("+") + 4;
			}
			drawMouseIcon(context, x, y, hint.mousePart());
			x += 19;
			if(hint.drag()) {
				drawDragIcon(context, x, y);
				x += 12;
			}
			context.drawString(font, action, x, y + 2, 0xFFD5DBE4, false);
			x += font.width(action) + 22;
		}
	}

	private int usageHintWidth(UsageHint hint) {
		return (hint.ctrl() ? 22 + 4 + font.width("+") + 4 : 0)
				+ 19 + (hint.drag() ? 12 : 0)
				+ font.width(Component.translatable(hint.actionKey())) + 22;
	}

	private static void drawMouseIcon(GuiGraphics context, int x, int y, MousePart activePart) {
		context.fill(x + 2, y, x + 12, y + 1, SHORTCUT_BORDER);
		context.fill(x, y + 2, x + 1, y + 10, SHORTCUT_BORDER);
		context.fill(x + 13, y + 2, x + 14, y + 10, SHORTCUT_BORDER);
		context.fill(x + 2, y + 12, x + 12, y + 13, SHORTCUT_BORDER);
		context.fill(x + 1, y + 10, x + 3, y + 12, SHORTCUT_BORDER);
		context.fill(x + 11, y + 10, x + 13, y + 12, SHORTCUT_BORDER);
		context.fill(x + 2, y + 2, x + 6, y + 6,
				activePart == MousePart.LEFT ? ACCENT : 0xFF4B5360);
		context.fill(x + 8, y + 2, x + 12, y + 6,
				activePart == MousePart.RIGHT ? ACCENT : 0xFF4B5360);
		context.fill(x + 6, y + 1, x + 8, y + 6,
				activePart == MousePart.WHEEL ? ACCENT : SHORTCUT_BORDER);
	}

	private static void drawDragIcon(GuiGraphics context, int x, int y) {
		context.fill(x + 1, y + 6, x + 9, y + 7, ACCENT);
		context.fill(x, y + 5, x + 1, y + 8, ACCENT);
		context.fill(x + 9, y + 5, x + 10, y + 8, ACCENT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		PaperHitArea paper = paperHitArea();
		if(event.button() == 0 && paper.left().contains(event.x(), event.y())) {
			selectSide(BodySide.LEFT);
			return true;
		}
		if(event.button() == 0 && paper.right().contains(event.x(), event.y())) {
			selectSide(BodySide.RIGHT);
			return true;
		}

		if(isInsideCanvas(event.x(), event.y())) {
			if(event.button() == 1 || event.button() == 2) {
				beginDrag(DragMode.PAN, event);
				return true;
			}
			if(event.button() == 0 && isOverResizeHandle(event.x(), event.y())) {
				beginSelectionDrag(DragMode.RESIZE, event);
				return true;
			}
			if(event.button() == 0) {
				FaceRef clicked = faceAt(event.x(), event.y());
				if(clicked == null) {
					selectedFaces.clear();
					return true;
				}

				boolean multiple = minecraft.hasControlDown() || minecraft.hasShiftDown();
				if(multiple) {
					if(!selectedFaces.add(clicked)) selectedFaces.remove(clicked);
				} else if(!selectedFaces.contains(clicked)) {
					selectedFaces.clear();
					selectedFaces.add(clicked);
				}
				if(selectedFaces.contains(clicked)) beginSelectionDrag(DragMode.MOVE, event);
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	private void selectSide(BodySide side) {
		if(selectedSide == side) return;
		selectedSide = side;
		selectedFaces.clear();
	}

	private void beginDrag(DragMode mode, MouseButtonEvent event) {
		dragMode = mode;
		dragStartMouseX = event.x();
		dragStartMouseY = event.y();
		dragStartPanX = canvasPanX;
		dragStartPanY = canvasPanY;
	}

	private void beginSelectionDrag(DragMode mode, MouseButtonEvent event) {
		beginDrag(mode, event);
		dragStartBounds = selectionBounds();
		dragStartQuads.clear();
		for(FaceRef face : selectedFaces) dragStartQuads.put(face, quad(face));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if(dragMode == DragMode.NONE) return super.mouseDragged(event, dragX, dragY);
		if(dragMode == DragMode.PAN) {
			canvasPanX = dragStartPanX + (float)(event.x() - dragStartMouseX);
			canvasPanY = dragStartPanY + (float)(event.y() - dragStartMouseY);
			clampPan();
			return true;
		}

		int deltaX = Math.round((float)(event.x() - dragStartMouseX) / canvasZoom);
		int deltaY = Math.round((float)(event.y() - dragStartMouseY) / canvasZoom);
		if(dragMode == DragMode.MOVE) moveSelection(deltaX, deltaY);
		if(dragMode == DragMode.RESIZE) resizeSelection(deltaX, deltaY);
		return true;
	}

	private void moveSelection(int deltaX, int deltaY) {
		if(dragStartBounds == null) return;
		deltaX = Mth.clamp(deltaX, -dragStartBounds.minX(), TEXTURE_SIZE - dragStartBounds.maxX());
		deltaY = Mth.clamp(deltaY, -dragStartBounds.minY(), TEXTURE_SIZE - dragStartBounds.maxY());
		for(var entry : dragStartQuads.entrySet()) {
			UVQuad quad = entry.getValue();
			putQuad(entry.getKey(), new UVQuad(quad.x1() + deltaX, quad.y1() + deltaY,
					quad.x2() + deltaX, quad.y2() + deltaY));
		}
		if(deltaX != 0 || deltaY != 0) markDirty();
	}

	private void resizeSelection(int deltaX, int deltaY) {
		if(dragStartBounds == null) return;
		int oldWidth = Math.max(1, dragStartBounds.maxX() - dragStartBounds.minX());
		int oldHeight = Math.max(1, dragStartBounds.maxY() - dragStartBounds.minY());
		int newWidth = Mth.clamp(oldWidth + deltaX, 1, TEXTURE_SIZE - dragStartBounds.minX());
		int newHeight = Mth.clamp(oldHeight + deltaY, 1, TEXTURE_SIZE - dragStartBounds.minY());
		for(var entry : dragStartQuads.entrySet()) {
			UVQuad original = entry.getValue();
			int x1 = scaledCoordinate(original.x1(), dragStartBounds.minX(), oldWidth, newWidth);
			int x2 = scaledCoordinate(original.x2(), dragStartBounds.minX(), oldWidth, newWidth);
			int y1 = scaledCoordinate(original.y1(), dragStartBounds.minY(), oldHeight, newHeight);
			int y2 = scaledCoordinate(original.y2(), dragStartBounds.minY(), oldHeight, newHeight);
			if(x1 == x2) x2 = Mth.clamp(x2 + Integer.signum(original.x2() - original.x1()), 0, TEXTURE_SIZE);
			if(y1 == y2) y2 = Mth.clamp(y2 + Integer.signum(original.y2() - original.y1()), 0, TEXTURE_SIZE);
			putQuad(entry.getKey(), new UVQuad(x1, y1, x2, y2));
		}
		if(newWidth != oldWidth || newHeight != oldHeight) markDirty();
	}

	private static int scaledCoordinate(int value, int origin, int oldSize, int newSize) {
		return origin + Math.round((value - origin) * (newSize / (float)oldSize));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if(dragMode != DragMode.NONE) {
			dragMode = DragMode.NONE;
			dragStartQuads.clear();
			dragStartBounds = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if(!isInsideCanvas(mouseX, mouseY) || verticalAmount == 0) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		ensureCanvasView();
		float textureU = (float)(mouseX - canvasX - canvasPanX) / canvasZoom;
		float textureV = (float)(mouseY - canvasY - canvasPanY) / canvasZoom;
		float newZoom = Mth.clamp(canvasZoom + (float)Math.signum(verticalAmount) * 0.5f,
				MIN_ZOOM, MAX_ZOOM);
		canvasPanX = (float)(mouseX - canvasX) - textureU * newZoom;
		canvasPanY = (float)(mouseY - canvasY) - textureV * newZoom;
		canvasZoom = newZoom;
		clampPan();
		return true;
	}

	private void clampPan() {
		float textureDrawSize = TEXTURE_SIZE * canvasZoom;
		canvasPanX = Mth.clamp(canvasPanX, -textureDrawSize + 24, canvasWidth - 24);
		canvasPanY = Mth.clamp(canvasPanY, -textureDrawSize + 24, canvasHeight - 24);
	}

	private @Nullable FaceRef faceAt(double mouseX, double mouseY) {
		List<FaceRef> faces = activeFaces();
		for(int index = faces.size() - 1; index >= 0; index--) {
			FaceRef face = faces.get(index);
			UVQuad quad = quad(face);
			if(!isHidden(quad) && screenRect(quad).contains(mouseX, mouseY)) return face;
		}
		return null;
	}

	private List<FaceRef> activeFaces() {
		List<FaceRef> result = new ArrayList<>();
		for(BreastTypes type : activeTypes()) {
			UVLayout layout = layout(type);
			for(var entry : layout.getQuads().entrySet()) {
				if(!isHidden(entry.getValue())) result.add(new FaceRef(type, entry.getKey()));
			}
		}
		return result;
	}

	private List<BreastTypes> activeTypes() {
		boolean modelLeft = modelSideFor(selectedSide) == BreastSide.LEFT;
		BreastTypes body = modelLeft ? bodyPart.left : bodyPart.right;
		BreastTypes outer = modelLeft ? bodyPart.leftOverlay : bodyPart.rightOverlay;
		return switch(layerFilter) {
			case ALL -> List.of(body, outer);
			case BODY -> List.of(body);
			case OUTER -> List.of(outer);
		};
	}

	private BreastSide modelSideFor(BodySide visibleSide) {
		// Translate the fixed front/rear camera's visible side to the renderer's model side.
		boolean modelLeft = bodyPart == BodyPart.BREAST
				? visibleSide == BodySide.LEFT
				: visibleSide == BodySide.RIGHT;
		return modelLeft ? BreastSide.LEFT : BreastSide.RIGHT;
	}

	private @Nullable SelectionBounds selectionBounds() {
		if(selectedFaces.isEmpty()) return null;
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for(FaceRef face : selectedFaces) {
			UVQuad quad = quad(face);
			if(isHidden(quad)) continue;
			minX = Math.min(minX, Math.min(quad.x1(), quad.x2()));
			minY = Math.min(minY, Math.min(quad.y1(), quad.y2()));
			maxX = Math.max(maxX, Math.max(quad.x1(), quad.x2()));
			maxY = Math.max(maxY, Math.max(quad.y1(), quad.y2()));
		}
		return minX == Integer.MAX_VALUE ? null : new SelectionBounds(minX, minY, maxX, maxY);
	}

	private boolean isOverResizeHandle(double mouseX, double mouseY) {
		SelectionBounds bounds = selectionBounds();
		if(bounds == null) return false;
		ScreenRect rect = screenRect(bounds);
		return mouseX >= rect.x2() - 5 && mouseX <= rect.x2() + 5
				&& mouseY >= rect.y2() - 5 && mouseY <= rect.y2() + 5;
	}

	private ScreenRect screenRect(UVQuad quad) {
		return new ScreenRect(
				Math.round(canvasX + canvasPanX + Math.min(quad.x1(), quad.x2()) * canvasZoom),
				Math.round(canvasY + canvasPanY + Math.min(quad.y1(), quad.y2()) * canvasZoom),
				Math.round(canvasX + canvasPanX + Math.max(quad.x1(), quad.x2()) * canvasZoom),
				Math.round(canvasY + canvasPanY + Math.max(quad.y1(), quad.y2()) * canvasZoom));
	}

	private ScreenRect screenRect(SelectionBounds bounds) {
		return new ScreenRect(
				Math.round(canvasX + canvasPanX + bounds.minX() * canvasZoom),
				Math.round(canvasY + canvasPanY + bounds.minY() * canvasZoom),
				Math.round(canvasX + canvasPanX + bounds.maxX() * canvasZoom),
				Math.round(canvasY + canvasPanY + bounds.maxY() * canvasZoom));
	}

	private boolean isInsideCanvas(double x, double y) {
		return x >= canvasX && x <= canvasX + canvasWidth && y >= canvasY && y <= canvasY + canvasHeight;
	}

	private UVLayout layout(BreastTypes type) {
		return Objects.requireNonNull(workingLayouts.get(type), "working layout " + type);
	}

	private UVQuad quad(FaceRef face) {
		return Objects.requireNonNull(layout(face.type()).get(face.direction()), "face " + face);
	}

	private void putQuad(FaceRef face, UVQuad quad) {
		layout(face.type()).put(face.direction(), quad);
	}

	private static boolean isHidden(UVQuad quad) {
		return quad.x1() == quad.x2() || quad.y1() == quad.y2();
	}

	private void resetWorkingLayouts() {
		for(BreastTypes type : bodyPart.types()) workingLayouts.put(type, defaultLayout(type));
		selectedFaces.clear();
		refreshDirty();
	}

	private void cancelChanges() {
		for(BreastTypes type : bodyPart.types()) {
			workingLayouts.put(type, Objects.requireNonNull(originalLayouts.get(type)).copy());
		}
		selectedFaces.clear();
		dirty = false;
		updateActionButtons();
	}

	private void applyChanges() {
		PlayerConfig player = Objects.requireNonNull(getPlayer(), "getPlayer()");
		for(BreastTypes type : bodyPart.types()) applyLayout(player, type, layout(type).copy());
		player.save();
		for(BreastTypes type : bodyPart.types()) originalLayouts.put(type, layout(type).copy());
		dirty = false;
		updateActionButtons();
	}

	private void markDirty() {
		refreshDirty();
		updateActionButtons();
	}

	private void refreshDirty() {
		dirty = bodyPart.types().stream().anyMatch(type -> !layout(type).equals(originalLayouts.get(type)));
	}

	private void updateActionButtons() {
		if(cancelButton != null) cancelButton.visible = dirty;
		if(applyButton != null) applyButton.visible = dirty;
	}

	private static UVLayout layoutFromPlayer(PlayerConfig player, BreastTypes type) {
		return switch(type) {
			case LEFT -> player.getLeftBreastUVLayout();
			case RIGHT -> player.getRightBreastUVLayout();
			case LEFT_OVERLAY -> player.getLeftBreastOverlayUVLayout();
			case RIGHT_OVERLAY -> player.getRightBreastOverlayUVLayout();
			case LEFT_BUTT -> player.getLeftButtUVLayout();
			case RIGHT_BUTT -> player.getRightButtUVLayout();
			case LEFT_BUTT_OVERLAY -> player.getLeftButtOverlayUVLayout();
			case RIGHT_BUTT_OVERLAY -> player.getRightButtOverlayUVLayout();
		};
	}

	private static void applyLayout(PlayerConfig player, BreastTypes type, UVLayout layout) {
		switch(type) {
			case LEFT -> player.updateLeftBreastUVLayout(layout);
			case RIGHT -> player.updateRightBreastUVLayout(layout);
			case LEFT_OVERLAY -> player.updateLeftBreastOverlayUVLayout(layout);
			case RIGHT_OVERLAY -> player.updateRightBreastOverlayUVLayout(layout);
			case LEFT_BUTT -> player.updateLeftButtUVLayout(layout);
			case RIGHT_BUTT -> player.updateRightButtUVLayout(layout);
			case LEFT_BUTT_OVERLAY -> player.updateLeftButtOverlayUVLayout(layout);
			case RIGHT_BUTT_OVERLAY -> player.updateRightButtOverlayUVLayout(layout);
		}
	}

	private static UVLayout defaultLayout(BreastTypes type) {
		return switch(type) {
			case LEFT -> Configuration.LEFT_BREAST_UV_LAYOUT.getDefault();
			case RIGHT -> Configuration.RIGHT_BREAST_UV_LAYOUT.getDefault();
			case LEFT_OVERLAY -> Configuration.LEFT_BREAST_OVERLAY_UV_LAYOUT.getDefault();
			case RIGHT_OVERLAY -> Configuration.RIGHT_BREAST_OVERLAY_UV_LAYOUT.getDefault();
			case LEFT_BUTT -> Configuration.LEFT_BUTT_UV_LAYOUT.getDefault();
			case RIGHT_BUTT -> Configuration.RIGHT_BUTT_UV_LAYOUT.getDefault();
			case LEFT_BUTT_OVERLAY -> Configuration.LEFT_BUTT_OVERLAY_UV_LAYOUT.getDefault();
			case RIGHT_BUTT_OVERLAY -> Configuration.RIGHT_BUTT_OVERLAY_UV_LAYOUT.getDefault();
		};
	}

	private static void drawOutline(GuiGraphics context, int x1, int y1, int x2, int y2,
	                                int color, int thickness) {
		context.fill(x1, y1, x2, y1 + thickness, color);
		context.fill(x1, y2 - thickness, x2, y2, color);
		context.fill(x1, y1, x1 + thickness, y2, color);
		context.fill(x2 - thickness, y1, x2, y2, color);
	}

	private record FaceRef(BreastTypes type, UVDirection direction) {}
	private record UsageHint(MousePart mousePart, boolean ctrl, boolean drag, String actionKey) {}
	private record SelectionBounds(int minX, int minY, int maxX, int maxY) {}
	private record PaperHitArea(ScreenRect left, ScreenRect right) {}
	private record ScreenRect(int x1, int y1, int x2, int y2) {
		private boolean contains(double x, double y) {
			return x >= x1 && x <= x2 && y >= y1 && y <= y2;
		}
	}

	private enum BodySide { LEFT, RIGHT }
	private enum MousePart { LEFT, RIGHT, WHEEL }
	private enum DragMode { NONE, MOVE, RESIZE, PAN }

	private enum LayerFilter {
		ALL("wildfire_gender.uv_editor.filter.all", GenderRenderState.PreviewLayer.ALL),
		BODY("wildfire_gender.uv_editor.filter.body", GenderRenderState.PreviewLayer.BODY),
		OUTER("", GenderRenderState.PreviewLayer.OUTER);

		private final String key;
		private final GenderRenderState.PreviewLayer previewLayer;

		LayerFilter(String key, GenderRenderState.PreviewLayer previewLayer) {
			this.key = key;
			this.previewLayer = previewLayer;
		}

		private String translationKey(BodyPart bodyPart) {
			if(this != OUTER) return key;
			return bodyPart == BodyPart.BREAST
					? "wildfire_gender.uv_editor.filter.jacket"
					: "wildfire_gender.uv_editor.filter.pants";
		}
	}

	public enum BodyPart {
		BREAST("wildfire_gender.uv_editor.breast", BreastTypes.LEFT, BreastTypes.RIGHT,
				BreastTypes.LEFT_OVERLAY, BreastTypes.RIGHT_OVERLAY),
		BUTT("wildfire_gender.uv_editor.butt", BreastTypes.LEFT_BUTT, BreastTypes.RIGHT_BUTT,
				BreastTypes.LEFT_BUTT_OVERLAY, BreastTypes.RIGHT_BUTT_OVERLAY);

		private final String titleKey;
		private final BreastTypes left;
		private final BreastTypes right;
		private final BreastTypes leftOverlay;
		private final BreastTypes rightOverlay;

		BodyPart(String titleKey, BreastTypes left, BreastTypes right,
		         BreastTypes leftOverlay, BreastTypes rightOverlay) {
			this.titleKey = titleKey;
			this.left = left;
			this.right = right;
			this.leftOverlay = leftOverlay;
			this.rightOverlay = rightOverlay;
		}

		private List<BreastTypes> types() {
			return List.of(left, right, leftOverlay, rightOverlay);
		}
	}
}
