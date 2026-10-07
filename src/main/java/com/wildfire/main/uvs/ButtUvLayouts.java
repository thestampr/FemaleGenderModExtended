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
		UVLayout section = sheet.copy();
		for(UVDirection face : new UVDirection[]{UVDirection.EAST, UVDirection.WEST, UVDirection.NORTH}) {
			UVQuad quad = sheet.get(face);
			if(quad == null || quad.y1() == quad.y2()) continue;
			int span = quad.y2() - quad.y1();
			int firstRow = Integer.signum(span) * Math.max(1, Math.round(Math.abs(span) / 4f));
			int boundary = quad.y1() + firstRow;
			// A one-pixel custom UV cannot be divided into two integer rectangles;
			// both mesh pieces sample that row until the face is enlarged in the editor.
			if(boundary == quad.y2()) boundary = quad.y1();
			section.put(face, upper
					? new UVQuad(quad.x1(), quad.y1(), quad.x2(), boundary == quad.y1() ? quad.y2() : boundary)
					: new UVQuad(quad.x1(), boundary, quad.x2(), quad.y2()));
		}
		section.put(upper ? UVDirection.UP : UVDirection.DOWN, new UVQuad(0, 0, 0, 0));
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
