"use client";

import dynamic from "next/dynamic";
import type { DraftStop } from "@/components/itinerary/draft";
import type { BaseLocation, PlanDocument } from "@/lib/api/types";

export type TripMapProps = { base: BaseLocation; stops: DraftStop[]; plan?: PlanDocument };

/** Leaflet touches `window` on import, so the map is only loaded in the browser. */
const TripMap = dynamic(() => import("./TripMap").then((module) => module.TripMap), {
  ssr: false,
  loading: () => <div className="h-full w-full animate-pulse rounded-xl bg-stone-200" />,
});

export function TripMapLoader(props: TripMapProps) {
  return <TripMap {...props} />;
}
