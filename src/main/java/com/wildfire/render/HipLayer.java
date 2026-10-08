/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.main.WildfireGender;
import com.wildfire.mixins.accessors.EquipmentLayerRendererAccessor;
import com.wildfire.mixins.accessors.LivingEntityRendererAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/** Two hip pieces per side: the waist follows the torso and the thigh follows its leg. */
@Environment(EnvType.CLIENT)
public final class HipLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
	private static final float CLASSIC_THIGH_VISIBLE_FRACTION = 4f / 5f;
	private static final float CLASSIC_THIGH_JOIN_INSET = 0.1f + (float)Math.pow(0.2, 1.25);
	// Armor already stands off from the body; keep its hip bulge narrower than the skin mesh.
	private static final float ARMOR_PROTRUSION = 0.65f;
	private static final float ARMOR_THIGH_EDGE = 2.2f;
	private static final float ARMOR_SURFACE_EPSILON = 0.015f;
	private static final float ARMOR_ATTACHMENT_INSET = 0.1f;
	private static final float ARMOR_JOIN_SINK = 0.04f;
	private static final float ARMOR_THIGH_JOIN_INSET = (ARMOR_THIGH_EDGE - 2f)
			+ ARMOR_PROTRUSION * (float)Math.pow(0.2, 1.25);
	private static final HipMesh[][][][][] MESHES = createMeshes();
	private final RenderLayerParent<S, M> context;
	private final EquipmentAssetManager equipmentAssets;
	private final EquipmentLayerRenderer equipmentRenderer;

	public HipLayer(RenderLayerParent<S, M> render, EquipmentAssetManager equipmentAssets,
	                EquipmentLayerRenderer equipmentRenderer) {
		super(render);
		this.context = render;
		this.equipmentAssets = equipmentAssets;
		this.equipmentRenderer = equipmentRenderer;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector queue, int light, S state,
	                   float limbAngle, float limbDistance) {
		GenderRenderState genderState = GenderRenderState.get(state);
		if(genderState == null || !genderState.hipsEnabled
				|| !genderState.gender.displaysGenderedBodyParts()
				|| state.isInvisibleToPlayer && !state.appearsGlowing()) return;

		ArmorPiece chestArmor = armorPiece(state.chestEquipment, EquipmentSlot.CHEST,
				EquipmentClientInfo.LayerType.HUMANOID);
		ArmorPiece thighArmor = armorPiece(state.legsEquipment, EquipmentSlot.LEGS,
				EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS);
		// Leggings also render a torso section beneath the chestplate. Keep both
		// armor layers so transparent chest textures can reveal the inner layer.
		boolean showWaist = state.chestEquipment.isEmpty() || chestArmor != null || thighArmor != null;
		boolean showThighs = state.legsEquipment.isEmpty() || thighArmor != null;
		if(!showWaist && !showThighs) return;

		var renderer = (LivingEntityRenderer<?, ?, ?>) context;
		RenderType renderType = ((LivingEntityRendererAccessor) renderer).invokeGetRenderType(state,
				!state.isInvisible, state.isInvisible && !state.isInvisibleToPlayer, state.appearsGlowing());
		if(renderType == null) return;

		try {
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
			int alpha = state.isInvisible ? ARGB.as8BitChannel(0.15f) : 255;
			int color = ARGB.color(alpha, 255, 255, 255);
			M model = getParentModel();
			for(BreastSide side : BreastSide.values()) {
				if(showWaist) {
					poseStack.pushPose();
					try {
						model.root().translateAndRotate(poseStack);
						model.body.translateAndRotate(poseStack);
						if(thighArmor != null) {
							submitArmorPiece(poseStack, queue, state, side, true, genderState.realisticModel, thighArmor);
						}
						if(chestArmor != null) {
							submitArmorPiece(poseStack, queue, state, side, true, genderState.realisticModel, chestArmor);
						}
						if(thighArmor == null && chestArmor == null) {
							submitMesh(poseStack, queue, renderType, state, overlay, color,
									mesh(side, true, false, genderState.realisticModel, HipSurface.SKIN), 64);
						}
						if(thighArmor == null && chestArmor == null
								&& state instanceof AvatarRenderState avatar && avatar.showJacket) {
							submitMesh(poseStack, queue, renderType, state, overlay, color,
									mesh(side, true, true, genderState.realisticModel, HipSurface.SKIN), 64);
						}
					} finally {
						poseStack.popPose();
					}
				}
				if(showThighs) {
					poseStack.pushPose();
					try {
						model.root().translateAndRotate(poseStack);
						(side.isLeft ? model.rightLeg : model.leftLeg).translateAndRotate(poseStack);
						if(thighArmor != null) {
							submitArmorPiece(poseStack, queue, state, side, false, genderState.realisticModel, thighArmor);
						} else {
							submitMesh(poseStack, queue, renderType, state, overlay, color,
									mesh(side, false, false, genderState.realisticModel, HipSurface.SKIN), 64);
						}
						if(thighArmor == null && state instanceof AvatarRenderState avatar
								&& (side.isLeft ? avatar.showRightPants : avatar.showLeftPants)) {
							submitMesh(poseStack, queue, renderType, state, overlay, color,
									mesh(side, false, true, genderState.realisticModel, HipSurface.SKIN), 64);
						}
					} finally {
						poseStack.popPose();
					}
				}
			}
		} catch(Exception exception) {
			WildfireGender.LOGGER.error("Failed to render hips", exception);
		}
	}

	private @Nullable ArmorPiece armorPiece(ItemStack stack, EquipmentSlot slot,
	                                         EquipmentClientInfo.LayerType layerType) {
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		if(equippable == null || equippable.slot() != slot) return null;
		ResourceKey<EquipmentAsset> asset = equippable.assetId().orElse(null);
		if(asset == null) return null;
		var layers = equipmentAssets.get(asset).getLayers(layerType);
		return layers.isEmpty() ? null : new ArmorPiece(stack, asset, layerType, layers);
	}

	private void submitArmorPiece(PoseStack poseStack, SubmitNodeCollector queue, S state,
	                              BreastSide side, boolean waist, boolean realistic, ArmorPiece armor) {
		HipSurface surface = armor.layerType() == EquipmentClientInfo.LayerType.HUMANOID
				? HipSurface.OUTER_ARMOR : HipSurface.INNER_ARMOR;
		HipMesh mesh = mesh(side, waist, false, realistic, surface);
		int dyeColor = DyedItemColor.getOrDefault(armor.stack(), 0);
		for(EquipmentClientInfo.Layer layer : armor.layers()) {
			var texture = layer.getTextureLocation(armor.layerType());
			if(!GenderArmorLayer.textureExists(texture)) continue;
			int color = ARGB.opaque(EquipmentLayerRenderer.getColorForLayer(layer, dyeColor));
			submitMesh(poseStack, queue, RenderTypes.armorCutoutNoCull(texture), state,
					OverlayTexture.NO_OVERLAY, color, mesh, 32);
		}
		ArmorTrim trim = armor.stack().get(DataComponents.TRIM);
		if(trim != null) {
			var key = new EquipmentLayerRenderer.TrimSpriteKey(trim, armor.layerType(), armor.asset());
			TextureAtlasSprite sprite = ((EquipmentLayerRendererAccessor) equipmentRenderer)
					.getTrimSpriteLookup().apply(key);
			queue.submitCustomGeometry(poseStack, Sheets.armorTrimsSheet(trim.pattern().value().decal()),
					(pose, consumer) -> mesh.render(pose, sprite.wrap(consumer), state.lightCoords,
							OverlayTexture.NO_OVERLAY, -1, 32));
		}
		if(armor.stack().hasFoil()) {
			submitMesh(poseStack, queue, RenderTypes.armorEntityGlint(), state,
					OverlayTexture.NO_OVERLAY, -1, mesh, 32);
		}
	}

	private static <S extends HumanoidRenderState> void submitMesh(PoseStack poseStack,
			SubmitNodeCollector queue, RenderType renderType, S state, int overlay, int color,
			HipMesh mesh, int textureHeight) {
		int light = state.lightCoords;
		queue.submitCustomGeometry(poseStack, renderType,
				(pose, consumer) -> mesh.render(pose, consumer, light, overlay, color, textureHeight));
	}

	private record ArmorPiece(ItemStack stack, ResourceKey<EquipmentAsset> asset,
	                          EquipmentClientInfo.LayerType layerType,
	                          List<EquipmentClientInfo.Layer> layers) {}

	private enum HipSurface { SKIN, OUTER_ARMOR, INNER_ARMOR }

	private static HipMesh mesh(BreastSide side, boolean waist, boolean overlay, boolean realistic,
	                            HipSurface surface) {
		return MESHES[side.ordinal()][waist ? 0 : 1][overlay ? 1 : 0][realistic ? 1 : 0][surface.ordinal()];
	}

	private static HipMesh[][][][][] createMeshes() {
		HipMesh[][][][][] meshes = new HipMesh[2][2][2][2][HipSurface.values().length];
		for(BreastSide side : BreastSide.values()) {
			for(int piece = 0; piece < 2; piece++) {
				for(int layer = 0; layer < 2; layer++) {
					for(int style = 0; style < 2; style++) {
						for(HipSurface surface : HipSurface.values()) {
							meshes[side.ordinal()][piece][layer][style][surface.ordinal()] =
									HipMesh.create(side, piece == 0, layer == 1, style == 1, surface);
						}
					}
				}
			}
		}
		return meshes;
	}

	private record HipVertex(float x, float y, float z, float u, float v,
	                         float normalX, float normalY, float normalZ) {}

	private record DepthEdges(float frontLeft, float frontRight, float backLeft, float backRight) {}

	private record HipQuad(HipVertex a, HipVertex b, HipVertex c, HipVertex d) {
		private void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
		                    int color, int textureHeight, Vector4f position, Vector3f normal) {
			renderVertex(a, pose, consumer, light, overlay, color, textureHeight, position, normal);
			renderVertex(b, pose, consumer, light, overlay, color, textureHeight, position, normal);
			renderVertex(c, pose, consumer, light, overlay, color, textureHeight, position, normal);
			renderVertex(d, pose, consumer, light, overlay, color, textureHeight, position, normal);
		}

		private static void renderVertex(HipVertex vertex, PoseStack.Pose pose, VertexConsumer consumer,
		                                 int light, int overlay, int color, int textureHeight,
		                                 Vector4f position, Vector3f normal) {
			position.set(vertex.x / 16f, vertex.y / 16f, vertex.z / 16f, 1).mul(pose.pose());
			normal.set(vertex.normalX, vertex.normalY, vertex.normalZ).mul(pose.normal()).normalize();
			consumer.addVertex(position.x, position.y, position.z, color,
					vertex.u / 64f, vertex.v / textureHeight, overlay, light, normal.x, normal.y, normal.z);
		}
	}

	private record HipMesh(List<HipQuad> quads) {
		private void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
		                    int color, int textureHeight) {
			Vector4f position = new Vector4f();
			Vector3f normal = new Vector3f();
			for(HipQuad quad : quads) {
				quad.render(pose, consumer, light, overlay, color, textureHeight, position, normal);
			}
		}

		private static HipMesh create(BreastSide side, boolean waist, boolean overlay, boolean realistic,
		                              HipSurface surfaceType) {
			boolean armor = surfaceType != HipSurface.SKIN;
			float armorInflation = armorInflation(surfaceType, waist);
			boolean negativeX = side.isLeft;
			int geometryRows = waist ? 4 : 5;
			int textureRows = geometryRows;
			int startY = waist ? 8 : 0;
			int textureY = waist ? (overlay && !armor ? 44 : 28)
					: armor ? 20 : negativeX ? (overlay ? 36 : 20) : 52;
			int frontU = waist ? (negativeX ? 20 : 27)
					: armor ? (negativeX ? 4 : 7) : negativeX ? 4 : overlay ? 7 : 23;
			int backU = waist ? (negativeX ? 39 : 32)
					: armor ? (negativeX ? 15 : 12) : negativeX ? 15 : overlay ? 12 : 28;
			int sideU = waist ? (negativeX ? 16 : 28)
					: armor ? (negativeX ? 0 : 8) : negativeX ? 0 : overlay ? 8 : 24;
			// Vanilla leg pivots sit 1.9 px from center. Armor leg cubes are
			// 0.1 px narrower than the leggings torso, so their hip edge starts
			// 0.1 px farther out to meet the waist in the standing pose.
			float edge = waist ? 4 : armor ? ARMOR_THIGH_EDGE : 2.1f;
			// Armor corners are beveled from an anchor inside the vanilla cube to a
			// shell just above it. Skin retains its original flat texture surface.
			float surface = armor ? armorInflation + ARMOR_SURFACE_EPSILON
					: waist ? (overlay ? 0.35f : 0.08f)
					: (overlay ? 0.25f : 0);
			float attachmentSurface = armor ? armorInflation - ARMOR_ATTACHMENT_INSET : surface;
			float innerX = (negativeX ? -1 : 1) * (waist ? edge - 0.8f : 2 + attachmentSurface);
			float frontZ = -2 - surface;
			float backZ = 2 + surface;
			float innerFrontZ = -2 - attachmentSurface;
			int segments = realistic ? textureRows * 2 : waist ? 1 : textureRows;
			List<HipQuad> quads = new ArrayList<>(segments * (waist ? 3 : 5) + 2);
			float sideFrontU = negativeX ? sideU + 4 : sideU;
			float sideBackU = negativeX ? sideU : sideU + 4;
			for(int index = 0; index < segments; index++) {
				float fractionTop = index / (float) segments;
				float fractionBottom = (index + 1) / (float) segments;
				float yTop = startY + fractionTop * geometryRows;
				float yBottom = startY + fractionBottom * geometryRows;
				float xTop = outerX(negativeX, edge, overlay, waist, realistic, surfaceType, fractionTop);
				float xBottom = outerX(negativeX, edge, overlay, waist, realistic, surfaceType, fractionBottom);
				float outerDepthTop = outerDepth(surface, armor, waist, fractionTop);
				float outerDepthBottom = outerDepth(surface, armor, waist, fractionBottom);
				DepthEdges topDepth = depthEdges(negativeX, outerDepthTop, -innerFrontZ);
				DepthEdges bottomDepth = depthEdges(negativeX, outerDepthBottom, -innerFrontZ);
				float vTop = textureY + fractionTop * textureRows;
				float vBottom = textureY + fractionBottom * textureRows;
				// Skin already has the vanilla leg surface beneath the fifth row.
				// Armor needs its straight outer side through that row to meet the
				// leggings shell without an abrupt ledge.
				boolean straightJoinRow = !waist && !realistic && index == segments - 1;
				if(straightJoinRow && !armor) continue;
				// On Classic armor, only the side stays exposed here; the front and
				// back transition is inside the existing leggings leg cube.
				if(waist) {
					addFrontAndBack(quads, Math.min(innerX, xTop), Math.min(innerX, xBottom),
							Math.max(innerX, xTop), Math.max(innerX, xBottom),
							yTop, yBottom, topDepth, bottomDepth,
							frontU, frontU + 1,
							backU + 1, backU, vTop, vBottom);
				} else if(!straightJoinRow && negativeX) {
					addFrontAndBack(quads, xTop, xBottom, innerX, innerX, yTop, yBottom,
							topDepth, bottomDepth,
							frontU + 0.5f, frontU,
							backU + 0.5f, backU + 1, vTop, vBottom);
				} else if(!straightJoinRow) {
					addFrontAndBack(quads, innerX, innerX, xTop, xBottom, yTop, yBottom,
							topDepth, bottomDepth,
							frontU + 1, frontU + 0.5f,
							backU, backU + 0.5f, vTop, vBottom);
				}
				float[] topNormal = sideNormal(negativeX, waist, realistic, surfaceType, fractionTop, geometryRows);
				float[] bottomNormal = sideNormal(negativeX, waist, realistic, surfaceType, fractionBottom, geometryRows);
				if(negativeX) {
					quads.add(new HipQuad(
						vertex(xTop, yTop, -outerDepthTop, sideFrontU, vTop, topNormal),
						vertex(xTop, yTop, outerDepthTop, sideBackU, vTop, topNormal),
						vertex(xBottom, yBottom, outerDepthBottom, sideBackU, vBottom, bottomNormal),
						vertex(xBottom, yBottom, -outerDepthBottom, sideFrontU, vBottom, bottomNormal)));
				} else {
					quads.add(new HipQuad(
						vertex(xTop, yTop, outerDepthTop, sideBackU, vTop, topNormal),
						vertex(xTop, yTop, -outerDepthTop, sideFrontU, vTop, topNormal),
						vertex(xBottom, yBottom, -outerDepthBottom, sideFrontU, vBottom, bottomNormal),
						vertex(xBottom, yBottom, outerDepthBottom, sideBackU, vBottom, bottomNormal)));
				}
			}
			// The vanilla armor torso and legs already close the joint from inside.
			// Extra horizontal armor caps showed up as dark floating plates when
			// viewed from below; only the exposed skin cross-sections need them.
			if(!armor) {
				float outerTop = outerX(negativeX, edge, overlay, waist, realistic, surfaceType, 0);
				addEndCap(quads, innerX, outerTop, startY + 0.015f, frontZ, backZ,
						sideFrontU, sideBackU, textureY, textureY + 1, true);
				if(waist || realistic) {
					float outerBottom = outerX(negativeX, edge, overlay, waist, realistic, surfaceType, 1);
					addEndCap(quads, innerX, outerBottom, startY + geometryRows - 0.015f, frontZ, backZ,
							sideFrontU, sideBackU, textureY + textureRows, textureY + textureRows - 1, false);
				}
			}
			return new HipMesh(List.copyOf(quads));
		}

		private static void addEndCap(List<HipQuad> quads, float innerX, float outerX, float y,
		                              float frontZ, float backZ, float frontU, float backU,
		                              float edgeV, float insideV, boolean top) {
			float left = Math.min(innerX, outerX);
			float right = Math.max(innerX, outerX);
			float leftV = outerX < innerX ? edgeV : insideV;
			float rightV = outerX > innerX ? edgeV : insideV;
			float normalY = top ? -1 : 1;
			HipVertex leftFront = vertex(left, y, frontZ, frontU, leftV, 0, normalY, 0);
			HipVertex rightFront = vertex(right, y, frontZ, frontU, rightV, 0, normalY, 0);
			HipVertex rightBack = vertex(right, y, backZ, backU, rightV, 0, normalY, 0);
			HipVertex leftBack = vertex(left, y, backZ, backU, leftV, 0, normalY, 0);
			quads.add(top
					? new HipQuad(leftFront, rightFront, rightBack, leftBack)
					: new HipQuad(leftFront, leftBack, rightBack, rightFront));
		}

		private static void addFrontAndBack(List<HipQuad> quads, float leftTop, float leftBottom,
		                                    float rightTop, float rightBottom, float yTop, float yBottom,
		                                    DepthEdges topDepth, DepthEdges bottomDepth,
		                                    float frontLeftU, float frontRightU,
		                                    float backLeftU, float backRightU, float vTop, float vBottom) {
			quads.add(new HipQuad(
					vertex(leftTop, yTop, topDepth.frontLeft, frontLeftU, vTop, 0, 0, -1),
					vertex(leftBottom, yBottom, bottomDepth.frontLeft, frontLeftU, vBottom, 0, 0, -1),
					vertex(rightBottom, yBottom, bottomDepth.frontRight, frontRightU, vBottom, 0, 0, -1),
					vertex(rightTop, yTop, topDepth.frontRight, frontRightU, vTop, 0, 0, -1)));
			quads.add(new HipQuad(
					vertex(leftTop, yTop, topDepth.backLeft, backLeftU, vTop, 0, 0, 1),
					vertex(rightTop, yTop, topDepth.backRight, backRightU, vTop, 0, 0, 1),
					vertex(rightBottom, yBottom, bottomDepth.backRight, backRightU, vBottom, 0, 0, 1),
					vertex(leftBottom, yBottom, bottomDepth.backLeft, backLeftU, vBottom, 0, 0, 1)));
		}

		private static float outerDepth(float surface, boolean armor, boolean waist, float fraction) {
			if(!armor || waist) return 2 + surface;
			float join = Math.max(0, (fraction - CLASSIC_THIGH_VISIBLE_FRACTION)
					/ (1 - CLASSIC_THIGH_VISIBLE_FRACTION));
			return 2 + surface - (ARMOR_SURFACE_EPSILON + ARMOR_JOIN_SINK) * join;
		}

		private static DepthEdges depthEdges(boolean negativeX, float outerDepth, float innerDepth) {
			return new DepthEdges(negativeX ? -outerDepth : -innerDepth,
					negativeX ? -innerDepth : -outerDepth,
					negativeX ? outerDepth : innerDepth,
					negativeX ? innerDepth : outerDepth);
		}

		private static float outerX(boolean negativeX, float edge, boolean overlay, boolean waist,
		                            boolean realistic, HipSurface surfaceType, float fraction) {
			boolean armor = surfaceType != HipSurface.SKIN;
			float protrusion = armor ? ARMOR_PROTRUSION : 1f;
			float outward = waist ? fraction : (float)Math.pow(1 - fraction, 1.25);
			if(realistic) outward = outward * outward * (3 - 2 * outward);
			float surface = armor ? armorInflation(surfaceType, waist) + ARMOR_SURFACE_EPSILON
					: waist ? (overlay ? 0.27f : 0.03f)
					: (overlay ? 0.25f : 0);
			if(!waist && !realistic && fraction >= CLASSIC_THIGH_VISIBLE_FRACTION) {
				// The fifth pixel has no front/back face. Only the armor side tucks
				// into the leg to hide its lower cut; skin keeps its straight join.
				float join = armor ? ARMOR_JOIN_SINK
						* (fraction - CLASSIC_THIGH_VISIBLE_FRACTION)
						/ (1 - CLASSIC_THIGH_VISIBLE_FRACTION) : 0;
				return (negativeX ? -1 : 1) * (2 + surface - join);
			}
			float classicThighInset = !waist && !realistic
					? (armor ? ARMOR_THIGH_JOIN_INSET : CLASSIC_THIGH_JOIN_INSET)
							* fraction / CLASSIC_THIGH_VISIBLE_FRACTION : 0;
			float armorLegJoin = armor && !waist && realistic
					? (ARMOR_THIGH_EDGE - 2f) * fraction + ARMOR_JOIN_SINK * fraction * fraction : 0;
			return (negativeX ? -1 : 1) * (edge + outward * protrusion
					- classicThighInset - armorLegJoin + surface);
		}

		private static float armorInflation(HipSurface surfaceType, boolean waist) {
			// HumanoidModel.createBaseArmorMesh narrows leggings leg cubes by 0.1 px;
			// the leggings body still uses the full 0.5 px inner deformation.
			return surfaceType == HipSurface.OUTER_ARMOR ? 1f : waist ? 0.5f : 0.4f;
		}

		private static float[] sideNormal(boolean negativeX, boolean waist, boolean realistic,
		                                  HipSurface surfaceType, float fraction, int rows) {
			if(!waist && !realistic && fraction >= CLASSIC_THIGH_VISIBLE_FRACTION) {
				float slope = surfaceType == HipSurface.SKIN ? 0 : ARMOR_JOIN_SINK;
				float length = (float)Math.sqrt(1 + slope * slope);
				return new float[]{(negativeX ? -1 : 1) / length, slope / length, 0};
			}
			float base = waist ? fraction : (float)Math.pow(1 - fraction, 1.25);
			float derivative = waist ? 1 : 1.25f * (float)Math.pow(1 - fraction, 0.25);
			float protrusion = surfaceType == HipSurface.SKIN ? 1f : ARMOR_PROTRUSION;
			float slope = protrusion * (waist ? 1 : -1) * derivative
					* (realistic ? 6 * base * (1 - base) : 1) / rows;
			if(!waist && !realistic)
				slope -= (surfaceType == HipSurface.SKIN ? CLASSIC_THIGH_JOIN_INSET
					: ARMOR_THIGH_JOIN_INSET)
					/ (CLASSIC_THIGH_VISIBLE_FRACTION * rows);
			float length = (float) Math.sqrt(1 + slope * slope);
			return new float[]{(negativeX ? -1 : 1) / length, -slope / length, 0};
		}

		private static HipVertex vertex(float x, float y, float z, float u, float v,
		                                float nx, float ny, float nz) {
			return new HipVertex(x, y, z, u, v, nx, ny, nz);
		}

		private static HipVertex vertex(float x, float y, float z, float u, float v, float[] normal) {
			return vertex(x, y, z, u, v, normal[0], normal[1], normal[2]);
		}
	}
}
