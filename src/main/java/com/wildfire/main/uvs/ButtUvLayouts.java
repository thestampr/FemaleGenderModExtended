/*
 * FemaleGenderModExtended is based on Wildfire's Female Gender Mod.
 * Licensed under the GNU Lesser General Public License v3 or later.
 */
package com.wildfire.main.uvs;

/** One editable UV sheet per butt side, split only while rendering its two mesh pieces. */
public final class ButtUvLayouts {
	private ButtUvLayouts() {}

	public static UVLayout torsoWhole(boolean left, boolean overlay) {
		return torsoSection(left, overlay, 0, 4);
	}

	public static UVLayout meshSection(UVLayout sheet, boolean upper) {
		return meshSection(sheet, upper, 0.25f);
	}

	/** The realistic upper mesh is three pixels tall, so give it three texture rows. */
	public static UVLayout realisticMeshSection(UVLayout sheet, boolean upper) {
		UVLayout section = meshSection(sheet, upper, 0.75f);
		// Shift the rendered rows down, but never sample outside the editable face.
		// The last lower row already touches that boundary; repeat that row instead
		// of reading transparent pixels from the next area of the skin atlas.
		for(UVDirection face : UVDirection.values()) {
			UVQuad quad = section.get(face);
			UVQuad source = sheet.get(face);
			if(quad != null && source != null && quad.x1() != quad.x2() && quad.y1() != quad.y2()) {
				int span = quad.y2() - quad.y1();
				int low = Math.min(source.y1(), source.y2());
				int high = Math.max(source.y1(), source.y2());
				int minStart = span > 0 ? low : low - span;
				int maxStart = span > 0 ? high - span : high;
				int start = Math.max(minStart, Math.min(quad.y1() + 1, maxStart));
				section.put(face, new UVQuad(quad.x1(), start, quad.x2(), start + span));
			}
		}
		return section;
	}

	private static UVLayout meshSection(UVLayout sheet, boolean upper, float upperFraction) {
		UVLayout section = sheet.copy();
		for(UVDirection face : new UVDirection[]{UVDirection.EAST, UVDirection.WEST, UVDirection.NORTH}) {
			UVQuad quad = sheet.get(face);
			if(quad == null || quad.y1() == quad.y2()) continue;
			int span = quad.y2() - quad.y1();
			int firstRow = Integer.signum(span) * Math.max(1, Math.round(Math.abs(span) * upperFraction));
			int boundary = quad.y1() + firstRow;
			// A one-pixel custom UV cannot be divided into two integer rectangles;
			// both mesh pieces sample that row until the face is enlarged in the editor.
			if(boundary == quad.y2()) boundary = quad.y1();
			section.put(face, upper
					? new UVQuad(quad.x1(), quad.y1(), quad.x2(), boundary == quad.y1() ? quad.y2() : boundary)
					: new UVQuad(quad.x1(), boundary, quad.x2(), quad.y2()));
		}
		section.put(upper ? UVDirection.UP : UVDirection.DOWN, new UVQuad(0, 0, 0, 0));
		// Both horizontal faces run opposite to the rear face in ModelBox. The
		// upper piece exposes its top (DOWN), and the lower piece its bottom (UP).
		// Flip render coordinates only; the editable/saved UV sheet stays intact.
		UVDirection horizontalFace = upper ? UVDirection.DOWN : UVDirection.UP;
		UVQuad horizontal = section.get(horizontalFace);
		if(horizontal != null) {
			section.put(horizontalFace, new UVQuad(horizontal.x2(), horizontal.y1(),
					horizontal.x1(), horizontal.y2()));
		}
		return section;
	}

	private static UVLayout torsoSection(boolean left, boolean overlay, int startRow, int height) {
		int y = (overlay ? 44 : 28) + startRow;
		// The butt mesh's negative-X half appears on the viewer's right from behind.
		// The torso's rear UV runs in the opposite X direction, so use the other half.
		int rearX = left ? 36 : 32;
		int eastX = left ? 30 : 28;
		int westX = left ? 16 : 18;
		return new UVLayout(
				new UVQuad(eastX, y, eastX + 2, y + height),
				new UVQuad(westX, y, westX + 2, y + height),
				new UVQuad(rearX, y, rearX + 4, y + 1),
				new UVQuad(rearX, y + height - 1, rearX + 4, y + height),
				new UVQuad(rearX, y, rearX + 4, y + height)
		);
	}

}
