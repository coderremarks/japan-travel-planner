"use client";

import "leaflet/dist/leaflet.css";
import L, { type LatLngTuple } from "leaflet";
import { Fragment, useEffect, useMemo } from "react";
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from "react-leaflet";
import { dayColor } from "@/components/plan/colors";
import { useI18n } from "@/i18n/I18nProvider";
import type { TripMapProps } from "./TripMapLoader";

const TILE_URL = process.env.NEXT_PUBLIC_MAP_TILE_URL ?? "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

/**
 * Leaflet's `divIcon` sets its `html` with innerHTML. Only numbers produced here go into it — never
 * place names or other user-influenced text — so it cannot become an XSS sink. Names are rendered
 * in React-managed <Popup> children, which React escapes.
 */
function numberIcon(label: number, color: string) {
  return L.divIcon({
    className: "",
    html: `<span class="stop-marker" style="background:${color}">${Number(label)}</span>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
}

const baseIcon = L.divIcon({
  className: "",
  html: '<span class="base-marker" aria-hidden="true">⌂</span>',
  iconSize: [30, 30],
  iconAnchor: [15, 15],
});

export function TripMap({ base, stops, plan }: TripMapProps) {
  const { t, locale } = useI18n();
  const basePoint = useMemo<LatLngTuple>(() => [base.lat, base.lng], [base.lat, base.lng]);

  const routes = useMemo(() => {
    if (plan) {
      return plan.days
        .filter((day) => day.visits.length > 0)
        .map((day) => ({
          key: `day-${day.dayIndex}`,
          color: dayColor(day.dayIndex),
          points: [basePoint, ...day.visits.map((v): LatLngTuple => [v.lat, v.lng]), basePoint],
          markers: day.visits.map((v, index) => ({
            id: `${day.dayIndex}-${v.placeId}`,
            position: [v.lat, v.lng] as LatLngTuple,
            label: index + 1,
            name: v.name[locale],
            detail: `${t("plan.day", { number: day.dayIndex + 1 })} · ${v.start}–${v.end}`,
          })),
        }));
    }
    return [
      {
        key: "stops",
        color: "#57534e",
        points: [] as LatLngTuple[],
        markers: stops.map((stop, index) => ({
          id: stop.place.id,
          position: [stop.place.lat, stop.place.lng] as LatLngTuple,
          label: index + 1,
          name: stop.place.name[locale],
          detail: t(`categories.${stop.place.category}`),
        })),
      },
    ];
  }, [plan, stops, basePoint, locale, t]);

  const allPoints = useMemo(
    () => [basePoint, ...routes.flatMap((route) => route.markers.map((m) => m.position))],
    [routes, basePoint],
  );

  return (
    <div className="relative h-full">
      <MapContainer center={basePoint} zoom={12} scrollWheelZoom className="h-full w-full">
        <TileLayer
          url={TILE_URL}
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          maxZoom={19}
        />
        <FitBounds points={allPoints} />
        <Marker position={basePoint} icon={baseIcon}>
          <Popup>{base.name[locale]}</Popup>
        </Marker>
        {routes.map((route) => (
          <Fragment key={route.key}>
            {route.points.length > 1 && (
              <Polyline positions={route.points} pathOptions={{ color: route.color, weight: 4, opacity: 0.8 }} />
            )}
            {route.markers.map((marker) => (
              <Marker key={marker.id} position={marker.position} icon={numberIcon(marker.label, route.color)}>
                <Popup>
                  <strong>{marker.name}</strong>
                  <br />
                  {marker.detail}
                </Popup>
              </Marker>
            ))}
          </Fragment>
        ))}
      </MapContainer>
      {plan && (
        <p className="absolute bottom-2 left-2 z-[400] max-w-[70%] rounded bg-white/90 px-2 py-1 text-[11px] text-stone-600 shadow">
          {t("plan.mapNote")}
        </p>
      )}
    </div>
  );
}

function FitBounds({ points }: { points: LatLngTuple[] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length > 1) {
      map.fitBounds(L.latLngBounds(points), { padding: [32, 32], maxZoom: 15 });
    }
  }, [map, points]);
  return null;
}
