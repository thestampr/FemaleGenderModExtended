/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.mixins.accessors.LivingEntityRendererAccessor;
import com.wildfire.physics.animation.CustomAnimationPhysics;
import com.wildfire.main.uvs.UVDirection;
import com.wildfire.main.uvs.UvEditorFeedback;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared torso anchoring and two-sided rendering for attached body-part layers. */
public abstract class BodyPartLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
	private static final Map<WildfireModelRenderer.ModelBox, DeformationCache> DEFORMATION_CACHE = new WeakHashMap<>();
	private final RenderLayerParent<S, M> context;

	protected BodyPartLayer(RenderLayerParent<S, M> render) {
		super(render);
		this.context = render;
	}

	protected @Nullable RenderType getRenderLayer(S state) {
		boolean bodyVisible = !state.isInvisible;
		boolean translucent = state.isInvisible && !state.isInvisibleToPlayer;
		boolean glowing = state.appearsGlowing();
		var renderer = (LivingEntityRenderer<?, ?, ?>) context;
		return ((LivingEntityRendererAccessor) renderer).invokeGetRenderType(state, bodyVisible, translucent, glowing);
	}

	protected boolean isLayerVisible(S state) {
		return !state.isInvisibleToPlayer || state.appearsGlowing();
	}

	/** Gives each physical face a stable color shared by its mirrored counterpart. */
	protected static @Nullable Function<UVDirection, Integer> uvPreviewFaceColors(
			HumanoidRenderState state, BreastSide side, int alpha, int baseShade) {
		GenderRenderState genderState = GenderRenderState.get(state);
		UvEditorFeedback.State feedbackState = genderState == null
				? UvEditorFeedback.State.IDLE : genderState.uvPreviewFeedbackState(side);
		float strength = feedbackState.meshTintStrength();
		if(feedbackState == UvEditorFeedback.State.IDLE) return null;
		return direction -> {
			int faceColor = UvEditorFeedback.faceColor(direction, !side.isLeft, feedbackState);
			int red = Math.round(Mth.lerp(strength, baseShade, faceColor >> 16 & 0xFF));
			int green = Math.round(Mth.lerp(strength, baseShade, faceColor >> 8 & 0xFF));
			int blue = Math.round(Mth.lerp(strength, baseShade, faceColor & 0xFF));
			return ARGB.color(alpha, red, green, blue);
		};
	}

	protected static int uvPreviewBaseShade(HumanoidRenderState state) {
		GenderRenderState genderState = GenderRenderState.get(state);
		return genderState != null && genderState.previewLayer == GenderRenderState.PreviewLayer.OUTER
				? 180 : 255;
	}

	protected static int uvPreviewOverlayAlpha(HumanoidRenderState state, int defaultAlpha) {
		GenderRenderState genderState = GenderRenderState.get(state);
		return genderState != null && genderState.previewLayer == GenderRenderState.PreviewLayer.OUTER
				? 255 : defaultAlpha;
	}

	protected void applyTorsoTransform(M model, PoseStack matrixStack) {
		// The vanilla model renders root -> body. Replaying both transforms is important because
		// animation mods may animate either part and commonly hook ModelPart#translateAndRotate.
		model.root().translateAndRotate(matrixStack);
		model.body.translateAndRotate(matrixStack);
	}

	protected void applyLegTransform(M model, PoseStack matrixStack, BreastSide side) {
		model.root().translateAndRotate(matrixStack);
		// The historical LEFT half occupies negative model X, which is the right-leg model part.
		(side.isLeft ? model.rightLeg : model.leftLeg).translateAndRotate(matrixStack);
	}

	protected abstract void setupTransformations(S state, M model, PoseStack matrixStack, BreastSide side);

	protected void renderSides(S state, M model, PoseStack matrixStack, Consumer<BreastSide> renderer) {
		GenderRenderState genderState = GenderRenderState.get(state);
		if(genderState != null) CustomAnimationPhysics.capture(genderState);

		for(BreastSide side : BreastSide.values()) {
			matrixStack.pushPose();
			try {
				setupTransformations(state, model, matrixStack, side);
				renderer.accept(side);
			} finally {
				matrixStack.popPose();
			}
		}
	}

	public static void renderBox(WildfireModelRenderer.ModelBox model, PoseStack.Pose entry, VertexConsumer vertexConsumer,
									int light, int overlay, int color) {
		renderBox(model, entry, vertexConsumer, light, overlay, color, SoftBodyDeformation.NONE);
	}

	public static void renderBox(WildfireModelRenderer.ModelBox model, PoseStack.Pose entry, VertexConsumer vertexConsumer,
	                             int light, int overlay, int color, SoftBodyDeformation deformation) {
		renderBox(model, entry, vertexConsumer, light, overlay, color, deformation, null);
	}

	public static void renderBox(WildfireModelRenderer.ModelBox model, PoseStack.Pose entry, VertexConsumer vertexConsumer,
	                             int light, int overlay, int color, SoftBodyDeformation deformation,
	                             @Nullable Function<UVDirection, Integer> faceColors) {
		Matrix4f pose = entry.pose();
		Matrix3f normal = entry.normal();
		boolean deform = deformation.active();
		DeformedGeometry cachedGeometry = deform && deformation.shouldCacheResult()
				? cachedGeometry(model, deformation) : null;
		Vector3f localNormal = new Vector3f();
		Vector3f localPosition = new Vector3f();
		Vector4f position = new Vector4f();
		int cachedVertexIndex = 0;
		for(var quad : model.quads) {
			if(quad.uvs[0] == 0.0F && quad.uvs[1] == 0.0F && quad.uvs[2] == 0.0F && quad.uvs[3] == 0.0F) continue;
			int faceColor = faceColors == null ? color : faceColors.apply(quad.direction);

			for(int vertexIndex = 0; vertexIndex < quad.vertexPositions.length; vertexIndex++) {
				var vertex = quad.vertexPositions[vertexIndex];
				var vertexNormal = quad.vertexNormals[vertexIndex];
				if(cachedGeometry != null) {
					cachedGeometry.position(cachedVertexIndex, localPosition);
					cachedGeometry.normal(cachedVertexIndex, localNormal);
				} else if(deform) {
					deformation.deformNormal(vertex, model, vertexNormal, localNormal);
					deformation.deform(vertex, model, localPosition);
				} else {
					localNormal.set(vertexNormal);
					localPosition.set(vertex.x(), vertex.y(), vertex.z());
				}
				localNormal.mul(normal).normalize();
				position.set(localPosition.x / 16.0F, localPosition.y / 16.0F,
						localPosition.z / 16.0F, 1.0F).mul(pose);
				vertexConsumer.addVertex(position.x(), position.y(), position.z(), faceColor, vertex.u(), vertex.v(), overlay, light,
						localNormal.x, localNormal.y, localNormal.z);
				cachedVertexIndex++;
			}
		}
	}

	private static DeformedGeometry cachedGeometry(WildfireModelRenderer.ModelBox model,
	                                               SoftBodyDeformation deformation) {
		DeformationCache cache = DEFORMATION_CACHE.computeIfAbsent(model, ignored -> new DeformationCache());
		return cache.computeIfAbsent(deformation.cacheKey(), ignored -> DeformedGeometry.create(model, deformation));
	}

	private static final class DeformationCache extends LinkedHashMap<SoftBodyDeformation.CacheKey, DeformedGeometry> {
		private static final int MAX_ENTRIES = 32;

		private DeformationCache() {
			super(8, 0.75f, true);
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<SoftBodyDeformation.CacheKey, DeformedGeometry> eldest) {
			return size() > MAX_ENTRIES;
		}
	}

	private record DeformedGeometry(float[] positions, float[] normals) {
		private static DeformedGeometry create(WildfireModelRenderer.ModelBox model,
		                                       SoftBodyDeformation deformation) {
			int vertexCount = model.quads.length * 4;
			float[] positions = new float[vertexCount * 3];
			float[] normals = new float[vertexCount * 3];
			Vector3f position = new Vector3f();
			Vector3f normal = new Vector3f();
			int vertexIndex = 0;
			for(var quad : model.quads) {
				for(int index = 0; index < quad.vertexPositions.length; index++) {
					deformation.deform(quad.vertexPositions[index], model, position);
					deformation.deformNormal(quad.vertexPositions[index], model,
							quad.vertexNormals[index], normal);
					int offset = vertexIndex++ * 3;
					positions[offset] = position.x;
					positions[offset + 1] = position.y;
					positions[offset + 2] = position.z;
					normals[offset] = normal.x;
					normals[offset + 1] = normal.y;
					normals[offset + 2] = normal.z;
				}
			}
			return new DeformedGeometry(positions, normals);
		}

		private void position(int vertexIndex, Vector3f destination) {
			int offset = vertexIndex * 3;
			destination.set(positions[offset], positions[offset + 1], positions[offset + 2]);
		}

		private void normal(int vertexIndex, Vector3f destination) {
			int offset = vertexIndex * 3;
			destination.set(normals[offset], normals[offset + 1], normals[offset + 2]);
		}
	}
}
