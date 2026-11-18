import { describe, expect, it } from "vitest";
import { formatDate, formatDistance, formatMinutes, formatYen } from "./format";

describe("formatting", () => {
  it("formats yen without decimals", () => {
    expect(formatYen(12300, "en")).toMatch(/12,300/);
    expect(formatYen(12300, "ja")).toMatch(/12,300/);
  });

  it("formats durations naturally in both languages", () => {
    expect(formatMinutes(45, "en")).toBe("45 min");
    expect(formatMinutes(120, "en")).toBe("2 h");
    expect(formatMinutes(85, "en")).toBe("1 h 25 min");
    expect(formatMinutes(85, "ja")).toBe("1時間25分");
    expect(formatMinutes(60, "ja")).toBe("1時間");
  });

  it("formats calendar dates without time-zone drift", () => {
    expect(formatDate("2026-11-23", "ja")).toContain("23");
    expect(formatDate("2026-11-23", "ja")).toContain("月");
    expect(formatDate("2026-11-23", "en")).toContain("Nov");
  });

  it("formats distances", () => {
    expect(formatDistance(850, "en")).toBe("850 m");
    expect(formatDistance(4321, "en")).toBe("4.3 km");
  });
});
