"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { defaultTrip } from "@/components/itinerary/draft";
import { Alert, Badge, Button, Spinner } from "@/components/ui/primitives";
import { formatDateRange, formatDateTime } from "@/i18n/format";
import { useI18n } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api/client";
import {
  useBaseLocations,
  useCreateItinerary,
  useDeleteItinerary,
  useDuplicateItinerary,
  useItineraries,
} from "@/lib/api/queries";
import type { ItinerarySummary } from "@/lib/api/types";

export function TripList() {
  const { t, locale, dynamic } = useI18n();
  const router = useRouter();
  const trips = useItineraries();
  const bases = useBaseLocations();
  const create = useCreateItinerary();

  function newTrip() {
    if (!bases.data?.length) {
      return;
    }
    create.mutate(defaultTrip(t("editor.defaultTitle"), bases.data, new Date()), {
      onSuccess: (itinerary) => router.push(`/${locale}/itineraries/${itinerary.id}`),
    });
  }

  const error = trips.error ?? create.error;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-stone-900">{t("trips.title")}</h1>
        <Button onClick={newTrip} disabled={create.isPending || !bases.data}>
          {create.isPending ? t("common.loading") : `＋ ${t("trips.new")}`}
        </Button>
      </div>
      {error && (
        <div className="mb-4">
          <Alert tone="error">
            {error instanceof ApiError ? dynamic(`errors.${error.code}`, t("errors.generic")) : t("errors.network")}
          </Alert>
        </div>
      )}
      {trips.isPending ? (
        <Spinner label={t("common.loading")} />
      ) : trips.data && trips.data.content.length === 0 ? (
        <div className="rounded-xl border border-dashed border-stone-300 bg-white px-6 py-16 text-center">
          <p aria-hidden="true" className="text-4xl">
            🗾
          </p>
          <h2 className="mt-3 text-lg font-semibold text-stone-900">{t("trips.emptyTitle")}</h2>
          <p className="mx-auto mt-2 max-w-md text-sm text-stone-600">{t("trips.emptyBody")}</p>
          <Button className="mt-6" onClick={newTrip} disabled={create.isPending || !bases.data}>
            {t("trips.new")}
          </Button>
        </div>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {trips.data?.content.map((trip) => (
            <li key={trip.id}>
              <TripCard trip={trip} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function TripCard({ trip }: { trip: ItinerarySummary }) {
  const { t, plural, locale } = useI18n();
  const duplicate = useDuplicateItinerary();
  const remove = useDeleteItinerary();

  return (
    <article className="flex h-full flex-col rounded-xl border border-stone-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <h2 className="font-semibold text-stone-900">
          <Link href={`/${locale}/itineraries/${trip.id}`} className="hover:underline">
            {trip.title}
          </Link>
        </h2>
        <PlanBadge trip={trip} />
      </div>
      <p className="mt-1 text-sm text-stone-600">{formatDateRange(trip.startDate, trip.endDate, locale)}</p>
      <p className="mt-1 text-sm text-stone-600">{plural("trips.places", trip.stopCount)}</p>
      <p className="mt-3 text-xs text-stone-600">
        {t("trips.updated", { date: formatDateTime(trip.updatedAt, locale) })}
      </p>
      <div className="mt-4 flex flex-wrap gap-2 border-t border-stone-100 pt-4">
        {/* Every card has the same three actions, so their accessible names include the trip title. */}
        <Link
          href={`/${locale}/itineraries/${trip.id}`}
          aria-label={`${t("trips.open")}: ${trip.title}`}
          className="bg-ai-600 hover:bg-ai-700 rounded-lg px-3 py-1.5 text-sm font-medium text-white"
        >
          {t("trips.open")}
        </Link>
        <Button
          variant="secondary"
          size="sm"
          aria-label={`${t("trips.duplicate")}: ${trip.title}`}
          disabled={duplicate.isPending}
          onClick={() => duplicate.mutate({ id: trip.id, lang: locale })}
        >
          {t("trips.duplicate")}
        </Button>
        <Button
          variant="danger"
          size="sm"
          aria-label={`${t("trips.delete")}: ${trip.title}`}
          disabled={remove.isPending}
          onClick={() => {
            if (window.confirm(t("trips.confirmDelete", { title: trip.title }))) {
              remove.mutate(trip.id);
            }
          }}
        >
          {t("trips.delete")}
        </Button>
      </div>
    </article>
  );
}

function PlanBadge({ trip }: { trip: ItinerarySummary }) {
  const { t, dynamic } = useI18n();
  if (!trip.plan) {
    return <Badge>{t("trips.notPlanned")}</Badge>;
  }
  if (trip.plan.stale) {
    return <Badge tone="amber">{t("trips.stale")}</Badge>;
  }
  const tone = trip.plan.status === "COMPLETE" ? "ai" : trip.plan.status === "PARTIAL" ? "amber" : "shu";
  return <Badge tone={tone}>{dynamic(`plan.status${trip.plan.status}`, trip.plan.status)}</Badge>;
}
