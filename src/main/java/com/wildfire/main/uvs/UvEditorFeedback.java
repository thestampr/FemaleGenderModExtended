/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.main.uvs;

/** Central color and state vocabulary for the interactive UV editor. */
public final class UvEditorFeedback {
	public static final int SELECTION = 0xFFFFD84A;

	private UvEditorFeedback() {}

	public enum State {
		IDLE(0x88, 0),
		HOVERED(0xDD, 0.3f),
		ACTIVE(0xFF, 0.62f);

		private final int alpha;
		private final float meshTintStrength;

		State(int alpha, float meshTintStrength) {
			this.alpha = alpha;
			this.meshTintStrength = meshTintStrength;
		}

		public float meshTintStrength() {
			return meshTintStrength;
		}
	}

	/**
	 * Returns the color for a physical face. Horizontal directions are mirrored so the
	 * same physical face on the left and right meshes always has the same color.
	 */
	public static int faceColor(UVDirection direction, boolean mirrorHorizontally, State state) {
		UVDirection visualDirection = mirroredDirection(direction, mirrorHorizontally);
		int rgb = switch(visualDirection) {
			case EAST -> 0xFF6B6B;  // inner
			case WEST -> 0x6EDB8F;  // outer
			case DOWN -> 0x5B8FF9;  // bottom
			case UP -> 0x51D6D6;    // top
			case NORTH -> 0xD968E8; // front / rear
		};
		return state.alpha << 24 | rgb;
	}

	private static UVDirection mirroredDirection(UVDirection direction, boolean mirrorHorizontally) {
		if(!mirrorHorizontally || direction != UVDirection.EAST && direction != UVDirection.WEST) {
			return direction;
		}
		return direction == UVDirection.EAST ? UVDirection.WEST : UVDirection.EAST;
	}
}
