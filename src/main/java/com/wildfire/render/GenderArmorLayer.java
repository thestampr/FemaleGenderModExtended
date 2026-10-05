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

package com.wildfire.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wildfire.api.IBreastArmorTexture;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.mixins.accessors.EquipmentLayerRendererAccessor;
import com.wildfire.render.WildfireModelRenderer.BreastModelBox;
import com.wildfire.render.ducks.MissingTextureLogger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public class GenderArmorLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends GenderLayer<S, M> {
	private static final float REALISTIC_ARMOR_SCALE = 1.05f;
	private static final float REALISTIC_ARMOR_HEIGHT_SCALE = 1.06f;
	private static final float REALISTIC_ARMOR_SURFACE_OFFSET = -0.08f;

	private final EquipmentLayerRenderer equipmentRenderer;
	private final EquipmentAssetManager equipmentModelLoader;
	@UnknownNullability("null until #resizeBox() is first called")
	protected BreastModelBox lBoobArmor, rBoobArmor, lTrim, rTrim;
	@UnknownNullability("null until first render pass")
	private GenderRenderState genderRenderState;
	private @Nullable UVLayout previousLeftArmorUV, previousRightArmorUV;
	private boolean previousArmorRealistic;

	@SuppressWarnings({"unused", "FieldMayBeFinal"}) // TODO fix this
	private IBreastArmorTexture textureData = IBreastArmorTexture.DEFAULT;

	static boolean textureExists(Identifier texture) {
		var texManager = Minecraft.getInstance().getTextureManager();
		return !((MissingTextureLogger) texManager).wildfire_gender$missingTextures().contains(texture);
	}

	public GenderArmorLayer(RenderLayerParent<S, M> render, EquipmentAssetManager equipmentModelLoader, EquipmentLayerRenderer equipmentRenderer) {
		super(render);
		this.equipmentRenderer = equipmentRenderer;
		this.equipmentModelLoader = equipmentModelLoader;
	}

	@Override
	public void submit(PoseStack matrixStack, SubmitNodeCollector queue, int light, S state, float limbAngle, float limbDistance) {
		this.genderRenderState = GenderRenderState.get(state);
		if (this.genderRenderState == null) return;

		final ItemStack chestplate = state.chestEquipment;
		// Check if the worn item in the chest slot is actually equippable in the chest slot, and has a model to render
		var component = chestplate.get(DataComponents.EQUIPPABLE);
		if(component == null || component.slot() != EquipmentSlot.CHEST) return;
		var asset = component.assetId().orElse(null);
		if(asset == null) return;
		var layers = equipmentModelLoader.get(asset).getLayers(EquipmentClientInfo.LayerType.HUMANOID);
		if(layers.isEmpty()) return;

		try {
			if(!setupRender(state, this.genderRenderState)) return;
			if(state instanceof ArmorStandRenderState && !genderArmor.armorStandsCopySettings()) return;

			int color = DyedItemColor.getOrDefault(chestplate, 0);
			boolean glint = chestplate.hasFoil();

			renderSides(state, getParentModel(), matrixStack, side -> {
				// TODO is there still a need to allow for overriding the armor texture identifier?
				layers.forEach(layer -> {
					int layerColor = EquipmentLayerRenderer.getColorForLayer(layer, color);
					var texture = layer.getTextureLocation(EquipmentClientInfo.LayerType.HUMANOID);
					renderBreastArmor(texture, matrixStack, queue, state, side, layerColor, glint);
				});

				var trim = armorStack.get(DataComponents.TRIM);
				if(trim != null) {
					renderArmorTrim(asset, matrixStack, queue, state, trim, side, glint);
				}
			});
		} catch(Exception e) {
			WildfireGender.LOGGER.error("Failed to render breast armor", e);
		}
	}

	@Override
	protected boolean isLayerVisible(S state) {
		return genderArmor.coversBreasts();
	}

	@Override
	protected void resizeBox(GenderRenderState state, float breastSize) {
		/*if(genderArmor == null || Objects.equals(textureData, genderArmor.texture())) {
			return;
		}

		textureData = genderArmor.texture();
		var texSize = textureData.textureSize();
		var lUV = textureData.leftUv();
		var rUV = textureData.rightUv();
		var dim = textureData.dimensions();*/

		//lBoobArmor = new BreastModelBox(texSize.x(), texSize.y(), lUV.x(), lUV.y(), -4F, 0.0F, 0F, dim.x(), dim.y(), 4, 0.0F, false);
		//rBoobArmor = new BreastModelBox(texSize.x(), texSize.y(), rUV.x(), rUV.y(), 0, 0.0F, 0F, dim.x(), dim.y(), 4, 0.0F, false);

		// FIXME make this work with armor configs
		if(Objects.equals(previousLeftArmorUV, state.leftBreastArmorUVLayout)
				&& Objects.equals(previousRightArmorUV, state.rightBreastArmorUVLayout)
				&& previousArmorRealistic == state.realisticModel) return;

		previousLeftArmorUV = state.leftBreastArmorUVLayout;
		previousRightArmorUV = state.rightBreastArmorUVLayout;
		previousArmorRealistic = state.realisticModel;
		lBoobArmor = new BreastModelBox(64, 32, -4F, 0.0F, 0F, 4, 5, 3, 0.0F,
				state.leftBreastArmorUVLayout, state.realisticModel);
		rBoobArmor = new BreastModelBox(64, 32, 0, 0.0F, 0F, 4, 5, 3, 0.0F,
				state.rightBreastArmorUVLayout, state.realisticModel);
		// The slight delta and extra depth prevent trim z-fighting with the armor layer.
		int trimDepth = state.realisticModel ? 3 : 4;
		lTrim = new BreastModelBox(64, 32, -4F, 0.0F, 0F, 4, 5, trimDepth, 0.001F,
				state.leftBreastArmorUVLayout, state.realisticModel);
		rTrim = new BreastModelBox(64, 32, 0, 0.0F, 0F, 4, 5, trimDepth, 0.001F,
				state.rightBreastArmorUVLayout, state.realisticModel);
	}

	@Override
	protected void setupTransformations(S state, M model, PoseStack matrixStack, BreastSide side) {
		super.setupTransformations(state, model, matrixStack, side);
		// Classic armor historically followed the expanded jacket shell. On a rounded model that
		// scales the attachment ring as well, making it protrude through the torso behind the neck.
		if (genderRenderState.hasJacketLayer && !genderRenderState.realisticModel) {
			matrixStack.translate(0, 0, -0.015f);
			matrixStack.scale(1.05f, 1.05f, 1.05f);
		}
		matrixStack.translate(side.isLeft ? 0.001f : -0.001f,
				genderRenderState.realisticModel ? 0f : 0.015f, -0.015f);
		// The realistic armor already receives a small forward offset. Scaling its X axis would
		// widen the attachment ring beyond the torso, exposing it from the rear near the neck.
		if(genderRenderState.realisticModel) {
			// Model Y starts at the upper attachment. A small extension covers the
			// under-breast edge without making the armored breast visibly taller.
			matrixStack.scale(1f, REALISTIC_ARMOR_HEIGHT_SCALE, 1f);
		} else {
			matrixStack.scale(1.05f, 1, 1);
		}
	}

	// TODO eventually expose some way for mods to override this, maybe through a default impl in IGenderArmor or similar
	protected void renderBreastArmor(Identifier texture, PoseStack matrixStack, SubmitNodeCollector queue,
	                                 S state, BreastSide side, int color, boolean glint) {
		if(!textureExists(texture)) {
			return;
		}

		var model = side.isLeft ? lBoobArmor : rBoobArmor;
		var layer = RenderTypes.armorCutoutNoCull(texture);
		SoftBodyDeformation deformation = deformationFor(side);
		queue.submitCustomGeometry(matrixStack, layer,
				new BreastRenderCommand(model, state, OverlayTexture.NO_OVERLAY, ARGB.opaque(color), deformation));

		if(glint) {
			renderGlint(matrixStack, queue, state, model, deformation);
		}
	}

	@Override
	protected SoftBodyDeformation deformationFor(BreastSide side) {
		if(!realisticModel) return super.deformationFor(side);
		// Enlarge only the free surface; the torso attachment remains fixed so the
		// larger armor cannot reappear as a lump behind the neck.
		return super.deformationFor(side, REALISTIC_ARMOR_SCALE, REALISTIC_ARMOR_SURFACE_OFFSET);
	}

	protected void renderArmorTrim(ResourceKey<EquipmentAsset> armorModel, PoseStack matrixStack, SubmitNodeCollector queue,
								   S state, ArmorTrim trim, BreastSide side, boolean glint) {
		var model = side.isLeft ? lTrim : rTrim;

		// this sucks, but it sucks less than simply copy/pasting the entire relevant block of code, and is
		// (at least theoretically) more compatible with other mods, assuming they simply mixin to TrimSpriteKey
		// to modify the armor trim sprite location.
		var key = new EquipmentLayerRenderer.TrimSpriteKey(trim, EquipmentClientInfo.LayerType.HUMANOID, armorModel);
		TextureAtlasSprite sprite = ((EquipmentLayerRendererAccessor) equipmentRenderer).getTrimSpriteLookup().apply(key);

		var layer = Sheets.armorTrimsSheet(trim.pattern().value().decal());
		SoftBodyDeformation deformation = deformationFor(side);
		queue.submitCustomGeometry(matrixStack, layer, BreastRenderCommand.trim(model, state, sprite, deformation));

		if(glint) {
			renderGlint(matrixStack, queue, state, model, deformation);
		}
	}

	protected void renderGlint(PoseStack matrixStack, SubmitNodeCollector renderQueue, S state, BreastModelBox box,
	                           SoftBodyDeformation deformation) {
		var glintLayer = RenderTypes.armorEntityGlint();
		renderQueue.submitCustomGeometry(matrixStack, glintLayer,
				new BreastRenderCommand(box, state, OverlayTexture.NO_OVERLAY, -1, deformation));
	}
}
