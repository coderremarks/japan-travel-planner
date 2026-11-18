import type { components } from "./schema";

type Schemas = components["schemas"];

export type User = Schemas["UserResponse"];
export type Area = Schemas["AreaResponse"];
export type BaseLocation = Schemas["BaseLocationResponse"];
export type PlaceSummary = Schemas["PlaceSummaryResponse"];
export type ItineraryRequest = Schemas["ItineraryRequest"];
export type Itinerary = Schemas["ItineraryResponse"];
export type ItinerarySummary = Schemas["ItinerarySummaryResponse"];
export type PlanResponse = Schemas["PlanResponse"];
export type PlanDocument = Schemas["PlanDocument"];
export type PlanDay = Schemas["Day"];
export type PlanVisit = Schemas["Visit"];
export type PlanLeg = Schemas["Leg"];
export type Unscheduled = Schemas["Unscheduled"];
export type LocalizedText = Schemas["LocalizedText"];

export type Category = PlaceSummary["category"];
export type TravelMode = PlanLeg["mode"];
export type PlanStrategy = Schemas["PlanRequest"]["strategy"];

export const CATEGORIES: Category[] = ["CULTURE", "NATURE", "FOOD", "MUSEUM", "SHOPPING", "ENTERTAINMENT"];
export const TRAVEL_MODES: TravelMode[] = ["WALK", "TRANSIT", "TAXI"];
