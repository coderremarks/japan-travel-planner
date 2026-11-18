/**
 * Day colours for the timeline dots and map routes: distinct hues with enough contrast against
 * white map tiles; days beyond the palette cycle.
 */
const DAY_COLORS = ["#235683", "#d9443a", "#0f766e", "#b45309", "#7c3aed", "#0369a1", "#be185d"];

export function dayColor(dayIndex: number): string {
  return DAY_COLORS[dayIndex % DAY_COLORS.length];
}
