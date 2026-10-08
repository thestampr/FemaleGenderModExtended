/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.main.WildfireGender;
import com.wildfire.mixins.accessors.LivingEntityRendererAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/** Two hip pieces per side: the waist follows the torso and the thigh follows its leg. */
@Environment(EnvType.CLIENT)
public final class HipLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
	private static final float CLASSIC_THIGH_VISIBLE_FRACTION = 4f / 5f;
	private static final float CLASSIC_THIGH_JOIN_INSET = 0.1f + (float)Math.pow(0.2, 1.25);
	private static final HipMesh[][][][] MESHES = createMeshes();
	private final RenderLayerParent<S, M> context;

	public HipLayer(RenderLayerParent<S, M> render) {
		super(render);
		this.context = render;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector queue, int light, S state,
	                   float limbAngle, float limbDistance) {
		GenderRenderState genderState = GenderRenderState.get(state);
		if(genderState == null || !genderState.hipsEnabled
				|| !genderState.gender.displaysGenderedBodyParts()
				|| state.isInvisibleToPlayer && !state.appearsGlowing()) return;

		boolean showWaist = state.chestEquipment.isEmpty();
		boolean showThighs = state.legsEquipment.isEmpty();
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
						submitMesh(poseStack, queue, renderType, state, overlay, color,
								mesh(side, true, false, genderState.realisticModel));
						if(state instanceof AvatarRenderState avatar && avatar.showJacket) {
							submitMesh(poseStack, queue, renderType, state, overlay, color,
									mesh(side, true, true, genderState.realisticModel));
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
						submitMesh(poseStack, queue, renderType, state, overlay, color,
								mesh(side, false, false, genderState.realisticModel));
						if(state instanceof AvatarRenderState avatar
								&& (side.isLeft ? avatar.showRightPants : avatar.showLeftPants)) {
							submitMesh(poseStack, queue, renderType, state, overlay, color,
									mesh(side, false, true, genderState.realisticModel));
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

	private static <S extends HumanoidRenderState> void submitMesh(PoseStack poseStack,
			SubmitNodeCollector queue, RenderType renderType, S state, int overlay, int color,
			HipMesh mesh) {
		int light = state.lightCoords;
		queue.submitCustomGeometry(poseStack, renderType,
				(pose, consumer) -> mesh.render(pose, consumer, light, overlay, color));
	}

	private static HipMesh mesh(BreastSide side, boolean waist, boolean overlay, boolean realistic) {
		return MESHES[side.ordinal()][waist ? 0 : 1][overlay ? 1 : 0][realistic ? 1 : 0];
	}

	private static HipMesh[][][][] createMeshes() {
		HipMesh[][][][] meshes = new HipMesh[2][2][2][2];
		for(BreastSide side : BreastSide.values()) {
			for(int piece = 0; piece < 2; piece++) {
				for(int layer = 0; layer < 2; layer++) {
					for(int style = 0; style < 2; style++) {
						meshes[side.ordinal()][piece][layer][style] =
								HipMesh.create(side, piece == 0, layer == 1, style == 1);
					}
				}
			}
		}
		return meshes;
	}

	private record HipVertex(float x, float y, float z, float u, float v,
	                         float normalX, float normalY, float normalZ) {}

	private record HipQuad(HipVertex a, HipVertex b, HipVertex c, HipVertex d) {
		private void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
		                    int color, Vector4f position, Vector3f normal) {
			renderVertex(a, pose, consumer, light, overlay, color, position, normal);
			renderVertex(b, pose, consumer, light, overlay, color, position, normal);
			renderVertex(c, pose, consumer, light, overlay, color, position, normal);
			renderVertex(d, pose, consumer, light, overlay, color, position, normal);
		}

		private static void renderVertex(HipVertex vertex, PoseStack.Pose pose, VertexConsumer consumer,
		                                 int light, int overlay, int color,
		                                 Vector4f position, Vector3f normal) {
			position.set(vertex.x / 16f, vertex.y / 16f, vertex.z / 16f, 1).mul(pose.pose());
			normal.set(vertex.normalX, vertex.normalY, vertex.normalZ).mul(pose.normal()).normalize();
			consumer.addVertex(position.x, position.y, position.z, color,
					vertex.u / 64f, vertex.v / 64f, overlay, light, normal.x, normal.y, normal.z);
		}
	}

	private record HipMesh(List<HipQuad> quads) {
		private void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay,
		                    int color) {
			Vector4f position = new Vector4f();
			Vector3f normal = new Vector3f();
			for(HipQuad quad : quads) {
				quad.render(pose, consumer, light, overlay, color, position, normal);
			}
		}

		private static HipMesh create(BreastSide side, boolean waist, boolean overlay, boolean realistic) {
			boolean negativeX = side.isLeft;
			int geometryRows = waist ? 4 : 5;
			int textureRows = geometryRows;
			int startY = waist ? 8 : 0;
			int textureY = waist ? (overlay ? 44 : 28)
					: negativeX ? (overlay ? 36 : 20) : 52;
			int frontU = waist ? (negativeX ? 20 : 27)
					: negativeX ? 4 : overlay ? 7 : 23;
			int backU = waist ? (negativeX ? 39 : 32)
					: negativeX ? 15 : overlay ? 12 : 28;
			int sideU = waist ? (negativeX ? 16 : 28)
					: negativeX ? 0 : overlay ? 8 : 24;
			// Vanilla leg pivots sit 1.9 px from center, so 2.1 px reaches the
			// torso's 4 px outer edge and both pieces meet in the standing pose.
			float edge = waist ? 4 : 2.1f;
			// The thigh shell meets the leg at its actual skin/pants surface. Only
			// the protruding strip is drawn; the leg already supplies the rest.
			float surface = waist ? (overlay ? 0.35f : 0.08f) : (overlay ? 0.25f : 0);
			float innerX = (negativeX ? -1 : 1) * (waist ? edge - 0.8f : 2 + surface);
			float frontZ = -2 - surface;
			float backZ = 2 + surface;
			int segments = realistic ? textureRows * 2 : waist ? 1 : textureRows;
			List<HipQuad> quads = new ArrayList<>(segments * (waist ? 3 : 5) + 2);
			float sideFrontU = negativeX ? sideU + 4 : sideU;
			float sideBackU = negativeX ? sideU : sideU + 4;
			for(int index = 0; index < segments; index++) {
				float fractionTop = index / (float) segments;
				float fractionBottom = (index + 1) / (float) segments;
				float yTop = startY + fractionTop * geometryRows;
				float yBottom = startY + fractionBottom * geometryRows;
				float xTop = outerX(negativeX, edge, overlay, waist, realistic, fractionTop);
				float xBottom = outerX(negativeX, edge, overlay, waist, realistic, fractionBottom);
				float vTop = textureY + fractionTop * textureRows;
				float vBottom = textureY + fractionBottom * textureRows;
				if(!waist && !realistic && index == segments - 1) continue;
				if(waist) {
					addFrontAndBack(quads, Math.min(innerX, xTop), Math.min(innerX, xBottom),
							Math.max(innerX, xTop), Math.max(innerX, xBottom),
							yTop, yBottom, frontZ, backZ, frontU, frontU + 1,
							backU + 1, backU, vTop, vBottom);
				} else if(negativeX) {
					addFrontAndBack(quads, xTop, xBottom, innerX, innerX, yTop, yBottom,
							frontZ, backZ, frontU + 0.5f, frontU,
							backU + 0.5f, backU + 1, vTop, vBottom);
				} else {
					addFrontAndBack(quads, innerX, innerX, xTop, xBottom, yTop, yBottom,
							frontZ, backZ, frontU + 1, frontU + 0.5f,
							backU, backU + 0.5f, vTop, vBottom);
				}
				float[] topNormal = sideNormal(negativeX, waist, realistic, fractionTop, geometryRows);
				float[] bottomNormal = sideNormal(negativeX, waist, realistic, fractionBottom, geometryRows);
				if(negativeX) {
					quads.add(new HipQuad(
						vertex(xTop, yTop, frontZ, sideFrontU, vTop, topNormal),
						vertex(xTop, yTop, backZ, sideBackU, vTop, topNormal),
						vertex(xBottom, yBottom, backZ, sideBackU, vBottom, bottomNormal),
						vertex(xBottom, yBottom, frontZ, sideFrontU, vBottom, bottomNormal)));
				} else {
					quads.add(new HipQuad(
						vertex(xTop, yTop, backZ, sideBackU, vTop, topNormal),
						vertex(xTop, yTop, frontZ, sideFrontU, vTop, topNormal),
						vertex(xBottom, yBottom, frontZ, sideFrontU, vBottom, bottomNormal),
						vertex(xBottom, yBottom, backZ, sideBackU, vBottom, bottomNormal)));
				}
			}
			// Keep the end faces that close the torso and leg cross-sections. They
			// are independent of the Classic thigh's visible four-pixel side slope.
			float outerTop = outerX(negativeX, edge, overlay, waist, realistic, 0);
			addEndCap(quads, innerX, outerTop, startY + 0.015f, frontZ, backZ,
					sideFrontU, sideBackU, textureY, textureY + 1, true);
			if(waist || realistic) {
				float outerBottom = outerX(negativeX, edge, overlay, waist, realistic, 1);
				addEndCap(quads, innerX, outerBottom, startY + geometryRows - 0.015f, frontZ, backZ,
						sideFrontU, sideBackU, textureY + textureRows, textureY + textureRows - 1, false);
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
		                                    float frontZ, float backZ, float frontLeftU, float frontRightU,
		                                    float backLeftU, float backRightU, float vTop, float vBottom) {
			quads.add(new HipQuad(
					vertex(leftTop, yTop, frontZ, frontLeftU, vTop, 0, 0, -1),
					vertex(leftBottom, yBottom, frontZ, frontLeftU, vBottom, 0, 0, -1),
					vertex(rightBottom, yBottom, frontZ, frontRightU, vBottom, 0, 0, -1),
					vertex(rightTop, yTop, frontZ, frontRightU, vTop, 0, 0, -1)));
			quads.add(new HipQuad(
					vertex(leftTop, yTop, backZ, backLeftU, vTop, 0, 0, 1),
					vertex(rightTop, yTop, backZ, backRightU, vTop, 0, 0, 1),
					vertex(rightBottom, yBottom, backZ, backRightU, vBottom, 0, 0, 1),
					vertex(leftBottom, yBottom, backZ, backLeftU, vBottom, 0, 0, 1)));
		}

		private static float outerX(boolean negativeX, float edge, boolean overlay, boolean waist,
		                            boolean realistic, float fraction) {
			float outward = waist ? fraction : (float)Math.pow(1 - fraction, 1.25);
			if(realistic) outward = outward * outward * (3 - 2 * outward);
			float surface = waist ? (overlay ? 0.27f : 0.03f) : (overlay ? 0.25f : 0);
			if(!waist && !realistic && fraction >= CLASSIC_THIGH_VISIBLE_FRACTION) {
				// The fifth pixel continues straight along the leg, with no angled
				// face or duplicate surface rendered over the leg itself.
				return (negativeX ? -1 : 1) * (2 + surface);
			}
			float classicThighInset = !waist && !realistic
					? CLASSIC_THIGH_JOIN_INSET * fraction / CLASSIC_THIGH_VISIBLE_FRACTION : 0;
			return (negativeX ? -1 : 1) * (edge + outward - classicThighInset + surface);
		}

		private static float[] sideNormal(boolean negativeX, boolean waist, boolean realistic,
		                                  float fraction, int rows) {
			if(!waist && !realistic && fraction >= CLASSIC_THIGH_VISIBLE_FRACTION)
				return new float[]{negativeX ? -1 : 1, 0, 0};
			float base = waist ? fraction : (float)Math.pow(1 - fraction, 1.25);
			float derivative = waist ? 1 : 1.25f * (float)Math.pow(1 - fraction, 0.25);
			float slope = (waist ? 1 : -1) * derivative
					* (realistic ? 6 * base * (1 - base) : 1) / rows;
			if(!waist && !realistic)
				slope -= CLASSIC_THIGH_JOIN_INSET / (CLASSIC_THIGH_VISIBLE_FRACTION * rows);
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
