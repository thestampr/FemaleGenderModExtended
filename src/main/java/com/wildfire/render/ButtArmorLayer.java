/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wildfire.main.WildfireHelper;
import com.wildfire.main.WildfireGender;
import com.wildfire.main.uvs.ButtUvLayouts;
import com.wildfire.mixins.accessors.EquipmentLayerRendererAccessor;
import com.wildfire.render.WildfireModelRenderer.ButtModelBox;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
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

/** Renders the leggings texture, trim, and glint over the configured butt geometry. */
@Environment(EnvType.CLIENT)
public final class ButtArmorLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends ButtLayer<S, M> {
	private static final EquipmentClientInfo.LayerType LAYER_TYPE = EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS;
	private ArmorModels leftArmor, rightArmor, leftTrim, rightTrim;
	private boolean previousRealisticModel;

	private final EquipmentLayerRenderer equipmentRenderer;
	private final EquipmentAssetManager equipmentAssets;

	public ButtArmorLayer(RenderLayerParent<S, M> render, EquipmentAssetManager equipmentAssets,
	                      EquipmentLayerRenderer equipmentRenderer) {
		super(render);
		this.equipmentAssets = equipmentAssets;
		this.equipmentRenderer = equipmentRenderer;
	}

	@Override
	public void submit(PoseStack matrixStack, SubmitNodeCollector queue, int light, S state,
	                   float limbAngle, float limbDistance) {
		GenderRenderState genderState = GenderRenderState.get(state);
		if(genderState == null) return;

		ItemStack leggings = state.legsEquipment;
		if(state instanceof ArmorStandRenderState
				&& !WildfireHelper.getArmorConfig(leggings).armorStandsCopySettings()) return;
		var equippable = leggings.get(DataComponents.EQUIPPABLE);
		if(equippable == null || equippable.slot() != EquipmentSlot.LEGS) return;

		ResourceKey<EquipmentAsset> asset = equippable.assetId().orElse(null);
		if(asset == null) return;

		var layers = equipmentAssets.get(asset).getLayers(LAYER_TYPE);
		if(layers.isEmpty() || !setupRender(state, genderState)) return;
		resizeArmor(genderState.realisticModel);

		try {
			int dyeColor = DyedItemColor.getOrDefault(leggings, 0);
			boolean glint = leggings.hasFoil();
			renderSides(state, getParentModel(), matrixStack, side -> {
				layers.forEach(layer -> renderArmorLayer(layer, dyeColor, glint, state, side, matrixStack, queue));
				ArmorTrim trim = leggings.get(DataComponents.TRIM);
				if(trim != null) renderTrim(asset, trim, glint, state, side, matrixStack, queue);
			});
		} catch(Exception exception) {
			WildfireGender.LOGGER.error("Failed to render butt armor", exception);
		}
	}

	private void resizeArmor(boolean realistic) {
		if(leftArmor != null && previousRealisticModel == realistic) return;
		previousRealisticModel = realistic;
		if(realistic) {
			leftArmor = realisticModels(BreastSide.LEFT, 0, true);
			rightArmor = realisticModels(BreastSide.RIGHT, 0, true);
			leftTrim = realisticModels(BreastSide.LEFT, 0.001f, false);
			rightTrim = realisticModels(BreastSide.RIGHT, 0.001f, false);
			return;
		}

		leftArmor = classicModels(true, 0);
		rightArmor = classicModels(false, 0);
		leftTrim = classicModels(true, 0.001f);
		rightTrim = classicModels(false, 0.001f);
	}

	private static ArmorModels classicModels(boolean left, float delta) {
		return new ArmorModels(new ButtModelBox(64, 32, -2, -1, 0, 4, 4, 3, delta,
				ButtUvLayouts.torsoWhole(left, false), false),
				null, null, null);
	}

	private static ArmorModels realisticModels(BreastSide side, float delta, boolean includeAttachment) {
		float x = side.isLeft ? -4 : 0;
		var sheet = ButtUvLayouts.torsoWhole(side.isLeft, false);
		var upperUV = realisticUpperLayout(sheet, side, true);
		var lowerUV = realisticLowerLayout(sheet, side, true);
		ButtModelBox upper = new ButtModelBox(64, 32, x, 9, 0, 4, 3, 3, delta,
				upperUV, true, 9, 14, 8, 5);
		ButtModelBox lower = new ButtModelBox(64, 32, x, 12, 0, 4, 2, 3, delta,
				lowerUV, true, 9, 14, 8, 3);
		if(!includeAttachment) return new ArmorModels(upper, lower, null, null);

		ButtModelBox upperAttachment = new ButtModelBox(64, 32, x, 9, REALISTIC_ATTACHMENT_Z, 4, 3, 2, delta, upperUV);
		ButtModelBox lowerAttachment = new ButtModelBox(64, 32, x, 12, REALISTIC_ATTACHMENT_Z, 4, 2, 2, delta, lowerUV);
		return new ArmorModels(upper, lower, upperAttachment, lowerAttachment);
	}

	@Override
	protected void setupTransformations(S state, M model, PoseStack matrixStack, BreastSide side) {
		super.setupTransformations(state, model, matrixStack, side);
		matrixStack.translate(0, 0, realisticModel ? 0.01f : 0.015f);
		if(!realisticModel) matrixStack.scale(1.05f, 1, 1.05f);
	}

	private void renderArmorLayer(EquipmentClientInfo.Layer layer, int dyeColor, boolean glint, S state,
	                              BreastSide side, PoseStack matrixStack, SubmitNodeCollector queue) {
		Identifier texture = layer.getTextureLocation(LAYER_TYPE);
		if(!GenderArmorLayer.textureExists(texture)) return;

		ArmorModels models = modelsFor(side, false);
		SoftBodyDeformation deformation = deformationFor(side);
		int color = ARGB.opaque(EquipmentLayerRenderer.getColorForLayer(layer, dyeColor));
		var renderType = RenderTypes.armorCutoutNoCull(texture);
		submitArmorModels(models, state, matrixStack, queue, renderType, color, deformation);
		if(glint) renderGlint(models, state, matrixStack, queue, deformation);
	}

	private void renderTrim(ResourceKey<EquipmentAsset> asset, ArmorTrim trim, boolean glint, S state,
	                        BreastSide side, PoseStack matrixStack, SubmitNodeCollector queue) {
		ArmorModels models = modelsFor(side, true);
		SoftBodyDeformation deformation = deformationFor(side);
		var key = new EquipmentLayerRenderer.TrimSpriteKey(trim, LAYER_TYPE, asset);
		var sprite = ((EquipmentLayerRendererAccessor) equipmentRenderer).getTrimSpriteLookup().apply(key);
		var renderType = Sheets.armorTrimsSheet(trim.pattern().value().decal());
		queue.submitCustomGeometry(matrixStack, renderType,
				BreastRenderCommand.trim(models.upper, state, sprite, deformation));
		if(models.lower != null) {
			queue.submitCustomGeometry(matrixStack, renderType,
					BreastRenderCommand.trim(models.lower, state, sprite, deformation));
		}
		if(glint) renderGlint(models, state, matrixStack, queue, deformation);
	}

	private ArmorModels modelsFor(BreastSide side, boolean trim) {
		if(trim) return side.isLeft ? leftTrim : rightTrim;
		return side.isLeft ? leftArmor : rightArmor;
	}

	private static void submitArmorModels(ArmorModels models, HumanoidRenderState state, PoseStack matrixStack,
	                                      SubmitNodeCollector queue, net.minecraft.client.renderer.rendertype.RenderType renderType,
	                                      int color, SoftBodyDeformation deformation) {
		if(models.upperAttachment != null) {
			queue.submitCustomGeometry(matrixStack, renderType,
					new BreastRenderCommand(models.upperAttachment, state, OverlayTexture.NO_OVERLAY, color,
							SoftBodyDeformation.NONE));
		}
		if(models.lowerAttachment != null) {
			queue.submitCustomGeometry(matrixStack, renderType,
					new BreastRenderCommand(models.lowerAttachment, state, OverlayTexture.NO_OVERLAY, color,
							SoftBodyDeformation.NONE));
		}
		queue.submitCustomGeometry(matrixStack, renderType,
				new BreastRenderCommand(models.upper, state, OverlayTexture.NO_OVERLAY, color, deformation));
		if(models.lower != null) {
			queue.submitCustomGeometry(matrixStack, renderType,
					new BreastRenderCommand(models.lower, state, OverlayTexture.NO_OVERLAY, color, deformation));
		}
	}

	private static void renderGlint(ArmorModels models, HumanoidRenderState state, PoseStack matrixStack,
	                                SubmitNodeCollector queue, SoftBodyDeformation deformation) {
		var renderType = RenderTypes.armorEntityGlint();
		queue.submitCustomGeometry(matrixStack, renderType,
				new BreastRenderCommand(models.upper, state, OverlayTexture.NO_OVERLAY, -1, deformation));
		if(models.lower != null) {
			queue.submitCustomGeometry(matrixStack, renderType,
					new BreastRenderCommand(models.lower, state, OverlayTexture.NO_OVERLAY, -1, deformation));
		}
	}

	private record ArmorModels(ButtModelBox upper, ButtModelBox lower,
	                           ButtModelBox upperAttachment, ButtModelBox lowerAttachment) {
	}
}
