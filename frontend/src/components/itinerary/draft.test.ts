import { describe, expect, it } from "vitest";
import type { Itinerary, PlaceSummary } from "@/lib/api/types";
import {
  addStop,
  defaultTrip,
  draftFromItinerary,
  isDirty,
  japanDate,
  moveStop,
  removeStop,
  toRequest,
  toggleMandatory,
} from "./draft";

function place(id: string): PlaceSummary {
  return {
    id,
    slug: id,
    areaCode: "TOKYO",
    category: "CULTURE",
    name: { en: id, ja: id },
    summary: { en: "", ja: "" },
    lat: 35.7,
    lng: 139.7,
    visitMinutes: 60,
    admissionYen: 0,
    dataSource: "SAMPLE_UNVERIFIED",
  };
}

export const sampleItinerary: Itinerary = {
  id: "trip-1",
  title: "Trip",
  startDate: "2026-12-08",
  endDate: "2026-12-09",
  dayStart: "09:00",
  dayEnd: "19:00",
  budgetYen: 30000,
  interests: ["CULTURE"],
  travelModes: ["WALK", "TRANSIT"],
  maxVisitsPerDay: 4,
  baseLocation: {
    id: "base-1",
    slug: "ueno",
    areaCode: "TOKYO",
    name: { en: "Ueno", ja: "上野" },
    lat: 35.7,
    lng: 139.7,
  },
  includeSuggestions: false,
  stops: [
    { place: place("a"), mandatory: true },
    { place: place("b"), mandatory: false },
  ],
  createdAt: "2026-11-18T00:00:00Z",
  updatedAt: "2026-11-18T00:00:00Z",
  version: 3,
};

describe("draft", () => {
  it("round-trips an itinerary into the request shape", () => {
    expect(toRequest(draftFromItinerary(sampleItinerary)).stops).toEqual([
      { placeId: "a", mandatory: true },
      { placeId: "b", mandatory: false },
    ]);
    expect(isDirty(draftFromItinerary(sampleItinerary), sampleItinerary)).toBe(false);
  });

  it("detects edits as unsaved changes", () => {
    const draft = draftFromItinerary(sampleItinerary);
    expect(isDirty({ ...draft, stops: moveStop(draft.stops, 0, 1) }, sampleItinerary)).toBe(true);
    expect(isDirty({ ...draft, title: "Other" }, sampleItinerary)).toBe(true);
  });

  it("adds places once, removes, toggles and reorders", () => {
    let stops = draftFromItinerary(sampleItinerary).stops;
    stops = addStop(stops, place("c"));
    expect(addStop(stops, place("c"))).toHaveLength(3);
    stops = toggleMandatory(stops, "c");
    expect(stops[2].mandatory).toBe(true);
    stops = moveStop(stops, 2, -1);
    expect(stops.map((s) => s.place.id)).toEqual(["a", "c", "b"]);
    expect(moveStop(stops, 0, -1)).toBe(stops);
    expect(removeStop(stops, "a").map((s) => s.place.id)).toEqual(["c", "b"]);
  });

  it("computes Japan dates and a default trip", () => {
    expect(japanDate(new Date("2026-11-18T16:00:00Z"), 0)).toBe("2026-11-19");
    const trip = defaultTrip("My trip", [sampleItinerary.baseLocation], new Date("2026-11-18T00:00:00Z"));
    expect(trip.startDate).toBe("2026-12-02");
    expect(trip.endDate).toBe("2026-12-04");
    expect(trip.baseLocationId).toBe("base-1");
  });
});
