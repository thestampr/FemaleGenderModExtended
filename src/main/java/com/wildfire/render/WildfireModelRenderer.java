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

import com.google.common.base.Preconditions;
import com.wildfire.main.uvs.UVDirection;
import com.wildfire.main.uvs.UVLayout;
import com.wildfire.main.uvs.UVQuad;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public final class WildfireModelRenderer {
	private WildfireModelRenderer() {
		throw new UnsupportedOperationException();
	}

	public static class ModelBox {
		private static final int ROUNDED_SUBDIVISIONS = 8;
		public final WildfireModelRenderer.TexturedQuad[] quads;
		public final float posX1;
		public final float posY1;
		public final float posZ1;
		public final float posX2;
		public final float posY2;
		public final float posZ2;
		public final boolean rearFacing;
		public final float smoothY1;
		public final float smoothY2;

		protected final UVLayout dynamicUvLayouts;

		protected ModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, int quads, UVLayout dynamicUvLayouts) {
			this(tW, tH, x, y, z, dx, dy, dz, delta, quads, dynamicUvLayouts, false);
		}

		protected ModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta,
		                   int quads, UVLayout dynamicUvLayouts, boolean rearFacing) {
			this(tW, tH, x, y, z, dx, dy, dz, delta, quads, dynamicUvLayouts, rearFacing, false);
		}

		protected ModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta,
		                   int quads, UVLayout dynamicUvLayouts, boolean rearFacing, boolean rounded) {
			this(tW, tH, x, y, z, dx, dy, dz, delta, quads, dynamicUvLayouts, rearFacing, rounded, y, y + dy);
		}

		protected ModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta,
		                   int quads, UVLayout dynamicUvLayouts, boolean rearFacing, boolean rounded,
		                   float smoothMinY, float smoothMaxY) {
			this(tW, tH, x, y, z, dx, dy, dz, delta, quads, dynamicUvLayouts, rearFacing, rounded,
					smoothMinY, smoothMaxY, ROUNDED_SUBDIVISIONS, ROUNDED_SUBDIVISIONS);
		}

		protected ModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta,
		                   int quads, UVLayout dynamicUvLayouts, boolean rearFacing, boolean rounded,
		                   float smoothMinY, float smoothMaxY, int horizontalSubdivisions,
		                   int verticalSubdivisions) {
			this.posX1 = x;
			this.posY1 = y;
			this.posZ1 = z;
			this.posX2 = x + (float) dx;
			this.posY2 = y + (float) dy;
			this.posZ2 = z + (float) dz;
			this.rearFacing = rearFacing;
			this.smoothY1 = smoothMinY;
			this.smoothY2 = smoothMaxY;
			this.dynamicUvLayouts = dynamicUvLayouts;
			int visibleFaces = (int)dynamicUvLayouts.getAllSides().values().stream()
					.filter(ModelBox::hasArea)
					.count();
			this.quads = new TexturedQuad[visibleFaces * (rounded
					? horizontalSubdivisions * verticalSubdivisions : 1)];

			float f = x + (float) dx;
			float f1 = y + (float) dy;
			float f2 = z + (float) dz;
			x = x - delta;
			y = y - delta;
			z = z - delta;
			f = f + delta;
			f1 = f1 + delta;
			f2 = f2 + delta;

			PositionTextureVertex[] vertices = {
					new PositionTextureVertex(f, y, z, 0.0F, 8.0F),
					new PositionTextureVertex(f, f1, z, 8.0F, 8.0F),
					new PositionTextureVertex(x, f1, z, 8.0F, 0.0F),
					new PositionTextureVertex(x, y, f2, 0.0F, 0.0F),
					new PositionTextureVertex(f, y, f2, 0.0F, 8.0F),
					new PositionTextureVertex(f, f1, f2, 8.0F, 8.0F),
					new PositionTextureVertex(x, f1, f2, 8.0F, 0.0F),
					new PositionTextureVertex(x, y, z, 0.0F, 0.0F)
			};
			if(rounded) {
				initRoundedQuads(tW, tH, rearFacing, x, smoothMinY - delta, z,
						f, smoothMaxY + delta, f2, vertices, horizontalSubdivisions, verticalSubdivisions);
			} else {
				initQuads(tW, tH, dx, dy, dz, quads, rearFacing, vertices[0], vertices[1], vertices[2],
						vertices[3], vertices[4], vertices[5], vertices[6], vertices[7]);
			}
		}

		protected void initQuads(int tW, int tH, int dx, int dy, int dz, int quads, boolean rearFacing,
								 PositionTextureVertex vertex, PositionTextureVertex vertex1, PositionTextureVertex vertex2,
								 PositionTextureVertex vertex3, PositionTextureVertex vertex4, PositionTextureVertex vertex5,
								 PositionTextureVertex vertex6, PositionTextureVertex vertex7) {
			PositionTextureVertex[][] faceVertices = {
					{vertex4, vertex, vertex1, vertex5},	// EAST
					{vertex7, vertex3, vertex6, vertex2},	// WEST
					{vertex4, vertex3, vertex7, vertex},	// DOWN
					{vertex1, vertex2, vertex6, vertex5},	// UP
					rearFacing ? new PositionTextureVertex[]{vertex3, vertex4, vertex5, vertex6}
							: new PositionTextureVertex[]{vertex, vertex7, vertex2, vertex1},
					{vertex3, vertex4, vertex5, vertex6}	// SOUTH
			};

			int i = 0;
			for(var entry : dynamicUvLayouts.getAllSides().entrySet()) {
				UVDirection direction = entry.getKey();
				UVQuad quad = entry.getValue();
				if(!hasArea(quad)) continue;
				PositionTextureVertex[] positions = faceVertices[direction.ordinal()];

				this.quads[i] = new TexturedQuad(
						quad.x1(), quad.y1(), quad.x2(), quad.y2(),
						tW, tH,
						direction,
						rearFacing && direction == UVDirection.NORTH ? new org.joml.Vector3f(0, 0, 1) : direction.getUnitVector(),
						positions[0], positions[1], positions[2], positions[3]
				);
				i++;
			}
		}

		private void initRoundedQuads(int textureWidth, int textureHeight, boolean rearFacing,
		                               float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
		                               PositionTextureVertex[] vertices, int horizontalSubdivisions,
		                               int verticalSubdivisions) {
			PositionTextureVertex[][] faceVertices = {
					{vertices[4], vertices[0], vertices[1], vertices[5]},
					{vertices[7], vertices[3], vertices[6], vertices[2]},
					{vertices[4], vertices[3], vertices[7], vertices[0]},
					{vertices[1], vertices[2], vertices[6], vertices[5]},
					rearFacing ? new PositionTextureVertex[]{vertices[3], vertices[4], vertices[5], vertices[6]}
							: new PositionTextureVertex[]{vertices[0], vertices[7], vertices[2], vertices[1]}
			};

			int index = 0;
			for(var entry : dynamicUvLayouts.getAllSides().entrySet()) {
				UVDirection direction = entry.getKey();
				UVQuad uv = entry.getValue();
				if(!hasArea(uv)) continue;
				PositionTextureVertex[] face = faceVertices[direction.ordinal()];
				for(int row = 0; row < verticalSubdivisions; row++) {
					float top = row / (float)verticalSubdivisions;
					float bottom = (row + 1) / (float)verticalSubdivisions;
					for(int column = 0; column < horizontalSubdivisions; column++) {
						float left = column / (float)horizontalSubdivisions;
						float right = (column + 1) / (float)horizontalSubdivisions;
						PositionTextureVertex p0 = roundedVertex(bilinear(face, left, top), rearFacing,
								minX, minY, minZ, maxX, maxY, maxZ);
						PositionTextureVertex p1 = roundedVertex(bilinear(face, right, top), rearFacing,
								minX, minY, minZ, maxX, maxY, maxZ);
						PositionTextureVertex p2 = roundedVertex(bilinear(face, right, bottom), rearFacing,
								minX, minY, minZ, maxX, maxY, maxZ);
						PositionTextureVertex p3 = roundedVertex(bilinear(face, left, bottom), rearFacing,
								minX, minY, minZ, maxX, maxY, maxZ);
						Vector3fc[] normals = {
								smoothNormal(p0, rearFacing, minX, minY, minZ, maxX, maxY, maxZ),
								smoothNormal(p1, rearFacing, minX, minY, minZ, maxX, maxY, maxZ),
								smoothNormal(p2, rearFacing, minX, minY, minZ, maxX, maxY, maxZ),
								smoothNormal(p3, rearFacing, minX, minY, minZ, maxX, maxY, maxZ)
						};
						float u1 = Mth.lerp(right, uv.x2(), uv.x1());
						float u2 = Mth.lerp(left, uv.x2(), uv.x1());
						float v1 = Mth.lerp(top, uv.y1(), uv.y2());
						float v2 = Mth.lerp(bottom, uv.y1(), uv.y2());
						this.quads[index++] = new TexturedQuad(u1, v1, u2, v2, textureWidth, textureHeight,
								direction, normals, p0, p1, p2, p3);
					}
				}
			}
		}

		private static boolean hasArea(UVQuad quad) {
			return quad != null && quad.x1() != quad.x2() && quad.y1() != quad.y2();
		}

		private static PositionTextureVertex bilinear(PositionTextureVertex[] face, float x, float y) {
			float topX = Mth.lerp(x, face[0].x(), face[1].x());
			float topY = Mth.lerp(x, face[0].y(), face[1].y());
			float topZ = Mth.lerp(x, face[0].z(), face[1].z());
			float bottomX = Mth.lerp(x, face[3].x(), face[2].x());
			float bottomY = Mth.lerp(x, face[3].y(), face[2].y());
			float bottomZ = Mth.lerp(x, face[3].z(), face[2].z());
			return new PositionTextureVertex(Mth.lerp(y, topX, bottomX), Mth.lerp(y, topY, bottomY),
					Mth.lerp(y, topZ, bottomZ), 0, 0);
		}

		private static PositionTextureVertex roundedVertex(PositionTextureVertex vertex, boolean rearFacing,
		                                                     float minX, float minY, float minZ,
		                                                     float maxX, float maxY, float maxZ) {
			float halfWidth = Math.max((maxX - minX) * 0.5f, 0.001f);
			float halfHeight = Math.max((maxY - minY) * 0.5f, 0.001f);
			float depth = Math.max(maxZ - minZ, 0.001f);
			float centerX = (minX + maxX) * 0.5f;
			float centerY = (minY + maxY) * 0.5f;
			float cubeX = Mth.clamp((vertex.x() - centerX) / halfWidth, -1, 1);
			float cubeY = Mth.clamp((vertex.y() - centerY) / halfHeight, -1, 1);
			float cubeZ = rearFacing
					? Mth.clamp((vertex.z() - minZ) / depth, 0, 1)
					: Mth.clamp((maxZ - vertex.z()) / depth, 0, 1);
			float attachmentZ = rearFacing ? minZ : maxZ;

			// Spherify the five visible faces of the open cube. Shared cube edges map to exactly the
			// same point, so the resulting dome has one continuous surface instead of stitched boxes.
			float xSquared = cubeX * cubeX;
			float ySquared = cubeY * cubeY;
			float zSquared = cubeZ * cubeZ;
			float sphereX = cubeX * safeSqrt(1f - ySquared * 0.5f - zSquared * 0.5f
					+ ySquared * zSquared / 3f);
			float sphereY = cubeY * safeSqrt(1f - zSquared * 0.5f - xSquared * 0.5f
					+ zSquared * xSquared / 3f);
			float radialSquared = Mth.clamp(sphereX * sphereX + sphereY * sphereY, 0, 1);
			// A power above one makes the slope reach zero at the attachment ring. That lets the
			// dome flow into the torso/legs without the hard equator ridge of a hemisphere.
			float profilePower = 1.36f;
			float domeDepth = (float)Math.pow(1f - radialSquared, profilePower);
			float widthScale = 1.48f;
			float heightScale = 1.46f;
			float volumeScale = 1f + domeDepth * 0.3f;
			float lowerFullness = 1f + Math.max(sphereY, 0) * domeDepth * 0.22f;
			float drop = halfHeight * domeDepth * 0.28f;
			float roundedX = centerX + sphereX * halfWidth * widthScale * volumeScale * lowerFullness;
			float roundedY = centerY + sphereY * halfHeight * heightScale * volumeScale + drop;
			float roundedZ = attachmentZ + (rearFacing ? 1f : -1f) * depth * domeDepth
					* 1.55f;
			return new PositionTextureVertex(roundedX, roundedY, roundedZ, vertex.u(), vertex.v());
		}

		private static float safeSqrt(float value) {
			return (float)Math.sqrt(Math.max(value, 0));
		}

		private static Vector3f smoothNormal(PositionTextureVertex vertex, boolean rearFacing,
		                                     float minX, float minY, float minZ,
		                                     float maxX, float maxY, float maxZ) {
			float halfWidth = Math.max((maxX - minX) * 0.5f, 0.001f);
			float halfHeight = Math.max((maxY - minY) * 0.5f, 0.001f);
			float depth = Math.max(maxZ - minZ, 0.001f);
			float widthScale = 1.48f;
			float heightScale = 1.46f;
			float normalizedX = (vertex.x() - (minX + maxX) * 0.5f) / (halfWidth * widthScale);
			float normalizedY = (vertex.y() - (minY + maxY) * 0.5f) / (halfHeight * heightScale);
			float radialSquared = Mth.clamp(normalizedX * normalizedX + normalizedY * normalizedY, 0, 1);
			float remaining = Math.max(1f - radialSquared, 0.0001f);
			float profilePower = 1.36f;
			float profileDerivative = 2f * profilePower
					* (float)Math.pow(remaining, profilePower - 1f);
			Vector3f normal = new Vector3f(
					depth * profileDerivative * normalizedX / (halfWidth * widthScale),
					depth * profileDerivative * normalizedY / (halfHeight * heightScale),
					rearFacing ? 1f : -1f);
			if(normal.lengthSquared() < 0.000001f) {
				return new Vector3f(0, 0, rearFacing ? 1 : -1);
			}
			return normal.normalize();
		}
	}

	public static class OverlayModelBox extends ModelBox {
		public OverlayModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, UVLayout dynamicUvLayouts) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts);
		}

		public OverlayModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz,
		                       float delta, UVLayout dynamicUvLayouts, boolean rounded) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, false, rounded);
		}
	}

	public static class BreastModelBox extends ModelBox {
		public BreastModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, UVLayout dynamicUvLayouts) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts);
		}

		public BreastModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz,
		                      float delta, UVLayout dynamicUvLayouts, boolean rounded) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, false, rounded);
		}
	}

	public static class ButtModelBox extends ModelBox {
		public ButtModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, UVLayout dynamicUvLayouts) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, true);
		}

		public ButtModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz,
		                    float delta, UVLayout dynamicUvLayouts, boolean rounded) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, true, rounded, -1, 3);
		}

		public ButtModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz,
		                    float delta, UVLayout dynamicUvLayouts, boolean rounded,
		                    float smoothMinY, float smoothMaxY) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, true, rounded,
					smoothMinY, smoothMaxY);
		}

		public ButtModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz,
		                    float delta, UVLayout dynamicUvLayouts, boolean rounded,
		                    float smoothMinY, float smoothMaxY, int horizontalSubdivisions,
		                    int verticalSubdivisions) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, true, rounded,
					smoothMinY, smoothMaxY, horizontalSubdivisions, verticalSubdivisions);
		}

	}

	public static class ButtOverlayModelBox extends ModelBox {
		public ButtOverlayModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz, float delta, UVLayout dynamicUvLayouts) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, true);
		}

		public ButtOverlayModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz,
		                           float delta, UVLayout dynamicUvLayouts, boolean rounded) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, true, rounded, -1, 3);
		}

		public ButtOverlayModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz,
		                           float delta, UVLayout dynamicUvLayouts, boolean rounded,
		                           float smoothMinY, float smoothMaxY) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, true, rounded,
					smoothMinY, smoothMaxY);
		}

		public ButtOverlayModelBox(int tW, int tH, float x, float y, float z, int dx, int dy, int dz,
		                           float delta, UVLayout dynamicUvLayouts, boolean rounded,
		                           float smoothMinY, float smoothMaxY, int horizontalSubdivisions,
		                           int verticalSubdivisions) {
			super(tW, tH, x, y, z, dx, dy, dz, delta, 5, dynamicUvLayouts, true, rounded,
					smoothMinY, smoothMaxY, horizontalSubdivisions, verticalSubdivisions);
		}

	}

	public record PositionTextureVertex(float x, float y, float z, float u, float v) {
		public PositionTextureVertex withTexturePosition(float texU, float texV) {
			return new PositionTextureVertex(x, y, z, texU, texV);
		}
	}

	public static class TexturedQuad {
		public final WildfireModelRenderer.PositionTextureVertex[] vertexPositions;
		public final UVDirection direction;
		public final Vector3fc normal;
		public final Vector3fc[] vertexNormals;
		public final float[] uvs;

		public TexturedQuad(float u1, float v1, float u2, float v2, float texWidth, float texHeight,
		                    UVDirection direction, Vector3fc normal, PositionTextureVertex... positionsIn) {
			this(u1, v1, u2, v2, texWidth, texHeight,
					direction, new Vector3fc[]{normal, normal, normal, normal}, positionsIn);
		}

		public TexturedQuad(float u1, float v1, float u2, float v2, float texWidth, float texHeight,
		                    UVDirection direction, Vector3fc[] vertexNormals, PositionTextureVertex... positionsIn) {
			Preconditions.checkArgument(positionsIn.length == 4, "Incorrect number of vertices; expected 4, got %s", positionsIn.length);
			Preconditions.checkArgument(vertexNormals.length == 4, "Incorrect number of normals; expected 4, got %s", vertexNormals.length);

			//Set UVs in array to reference in render side.
			this.uvs = new float[]{ u1, v1, u2, v2 };

			this.vertexPositions = positionsIn;
			this.direction = direction;
			float f = 0.0F / texWidth;
			float f1 = 0.0F / texHeight;
			positionsIn[0] = positionsIn[0].withTexturePosition(u2 / texWidth - f, v1 / texHeight + f1);
			positionsIn[1] = positionsIn[1].withTexturePosition(u1 / texWidth + f, v1 / texHeight + f1);
			positionsIn[2] = positionsIn[2].withTexturePosition(u1 / texWidth + f, v2 / texHeight - f1);
			positionsIn[3] = positionsIn[3].withTexturePosition(u2 / texWidth - f, v2 / texHeight - f1);
			this.vertexNormals = vertexNormals;
			this.normal = vertexNormals[0];
		}
	}
}
