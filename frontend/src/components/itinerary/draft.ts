import type { BaseLocation, Category, Itinerary, ItineraryRequest, PlaceSummary, TravelMode } from "@/lib/api/types";

/** Editable state of an itinerary in the browser; converted to the API request on save. */
export type Draft = {
  title: string;
  startDate: string;
  endDate: string;
  dayStart: string;
  dayEnd: string;
  budgetYen: number;
  interests: Category[];
  travelModes: TravelMode[];
  maxVisitsPerDay: number;
  baseLocationId: string;
  includeSuggestions: boolean;
  stops: DraftStop[];
};

export type DraftStop = { place: PlaceSummary; mandatory: boolean };

export function draftFromItinerary(itinerary: Itinerary): Draft {
  return {
    title: itinerary.title,
    startDate: itinerary.startDate,
    endDate: itinerary.endDate,
    dayStart: itinerary.dayStart,
    dayEnd: itinerary.dayEnd,
    budgetYen: itinerary.budgetYen,
    interests: [...itinerary.interests],
    travelModes: [...itinerary.travelModes],
    maxVisitsPerDay: itinerary.maxVisitsPerDay,
    baseLocationId: itinerary.baseLocation.id,
    includeSuggestions: itinerary.includeSuggestions,
    stops: itinerary.stops.map((stop) => ({ place: stop.place, mandatory: stop.mandatory })),
  };
}

export function toRequest(draft: Draft): ItineraryRequest {
  return {
    title: draft.title.trim(),
    startDate: draft.startDate,
    endDate: draft.endDate,
    dayStart: draft.dayStart,
    dayEnd: draft.dayEnd,
    budgetYen: draft.budgetYen,
    interests: draft.interests,
    travelModes: draft.travelModes,
    maxVisitsPerDay: draft.maxVisitsPerDay,
    baseLocationId: draft.baseLocationId,
    includeSuggestions: draft.includeSuggestions,
    stops: draft.stops.map((stop) => ({ placeId: stop.place.id, mandatory: stop.mandatory })),
  };
}

/** True when the draft differs from what the server has (compared in request form). */
export function isDirty(draft: Draft, saved: Itinerary): boolean {
  return JSON.stringify(toRequest(draft)) !== JSON.stringify(toRequest(draftFromItinerary(saved)));
}

export function addStop(stops: DraftStop[], place: PlaceSummary): DraftStop[] {
  return stops.some((stop) => stop.place.id === place.id) ? stops : [...stops, { place, mandatory: false }];
}

export function removeStop(stops: DraftStop[], placeId: string): DraftStop[] {
  return stops.filter((stop) => stop.place.id !== placeId);
}

export function toggleMandatory(stops: DraftStop[], placeId: string): DraftStop[] {
  return stops.map((stop) => (stop.place.id === placeId ? { ...stop, mandatory: !stop.mandatory } : stop));
}

/** Moves the stop at {@code index} by {@code delta} positions; out-of-range moves are ignored. */
export function moveStop(stops: DraftStop[], index: number, delta: -1 | 1): DraftStop[] {
  const target = index + delta;
  if (index < 0 || index >= stops.length || target < 0 || target >= stops.length) {
    return stops;
  }
  const next = [...stops];
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

export function toggleValue<T>(values: T[], value: T): T[] {
  return values.includes(value) ? values.filter((v) => v !== value) : [...values, value];
}

/** Local calendar date (YYYY-MM-DD) in Japan, `days` from now. */
export function japanDate(now: Date, days: number): string {
  const tokyo = new Date(now.getTime() + 9 * 60 * 60 * 1000 + days * 24 * 60 * 60 * 1000);
  return tokyo.toISOString().slice(0, 10);
}

/** A sensible starting point for "New trip": three days, two weeks from now, staying in Tokyo. */
export function defaultTrip(title: string, bases: BaseLocation[], now: Date): ItineraryRequest {
  const base = bases.find((b) => b.slug === "tokyo-station") ?? bases[0];
  return {
    title,
    startDate: japanDate(now, 14),
    endDate: japanDate(now, 16),
    dayStart: "09:00",
    dayEnd: "19:00",
    budgetYen: 30000,
    interests: ["CULTURE", "FOOD"],
    travelModes: ["WALK", "TRANSIT"],
    maxVisitsPerDay: 4,
    baseLocationId: base.id,
    includeSuggestions: false,
    stops: [],
  };
}
