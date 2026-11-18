"use client";

import { useEffect, useState } from "react";
import { PlanPanel } from "@/components/plan/PlanPanel";
import { TripMapLoader } from "@/components/map/TripMapLoader";
import { Alert, Button, Spinner } from "@/components/ui/primitives";
import { useI18n } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api/client";
import { useGeneratePlan, useItinerary, usePlan, useUpdateItinerary } from "@/lib/api/queries";
import type { Itinerary, PlanStrategy } from "@/lib/api/types";
import { draftFromItinerary, isDirty, toRequest, type Draft } from "./draft";
import { PlaceSearch } from "./PlaceSearch";
import { StopList } from "./StopList";
import { TripSettings } from "./TripSettings";

export function ItineraryWorkspace({ id }: { id: string }) {
  const { t, dynamic } = useI18n();
  const itinerary = useItinerary(id);

  if (itinerary.isPending) {
    return (
      <div className="flex justify-center py-24">
        <Spinner label={t("common.loading")} />
      </div>
    );
  }
  if (itinerary.error || !itinerary.data) {
    const code = itinerary.error instanceof ApiError ? itinerary.error.code : "generic";
    return (
      <div className="mx-auto max-w-xl px-4 py-16">
        <Alert tone="error">{dynamic(`errors.${code}`, t("errors.generic"))}</Alert>
      </div>
    );
  }
  return <Editor key={itinerary.data.id} saved={itinerary.data} />;
}

/**
 * Holds the local draft of one itinerary. After a successful save the draft is replaced by the
 * server's response (which may normalise values, e.g. trimmed titles), so "unsaved changes" is
 * always computed against what is actually stored.
 */
function Editor({ saved }: { saved: Itinerary }) {
  const { t, dynamic } = useI18n();
  const [draft, setDraft] = useState<Draft>(() => draftFromItinerary(saved));
  const update = useUpdateItinerary(saved.id);
  const generate = useGeneratePlan(saved.id);
  const plan = usePlan(saved.id);
  const dirty = isDirty(draft, saved);
  const fieldErrors = update.error instanceof ApiError ? update.error.fieldErrors() : {};
  const conflict = update.error instanceof ApiError && update.error.status === 412;

  useEffect(() => {
    if (!dirty) {
      return;
    }
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  async function save() {
    const updated = await update.mutateAsync({ body: toRequest(draft), version: saved.version });
    setDraft(draftFromItinerary(updated));
    return updated;
  }

  async function runPlan(strategy: PlanStrategy) {
    try {
      if (dirty) {
        await save();
      }
      await generate.mutateAsync(strategy);
    } catch {
      // Errors are rendered from the mutation state.
    }
  }

  const errorCode = update.error instanceof ApiError && !conflict ? update.error.code : update.error ? "network" : null;

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-stone-900">{saved.title}</h1>
        <div className="flex items-center gap-3">
          <span aria-live="polite" className="text-sm text-stone-600">
            {update.isPending
              ? t("common.saving")
              : dirty
                ? t("editor.unsaved")
                : update.isSuccess
                  ? t("editor.saved")
                  : ""}
          </span>
          <Button onClick={() => void save().catch(() => undefined)} disabled={!dirty || update.isPending}>
            {t("editor.save")}
          </Button>
        </div>
      </div>

      {conflict && (
        <div className="mb-4">
          <Alert tone="warning">
            {t("editor.conflict")}{" "}
            <button type="button" className="font-semibold underline" onClick={() => window.location.reload()}>
              {t("editor.reload")}
            </button>
          </Alert>
        </div>
      )}
      {errorCode && (
        <div className="mb-4">
          <Alert tone="error">
            {errorCode === "network" ? t("errors.network") : dynamic(`errors.${errorCode}`, t("errors.generic"))}
          </Alert>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
        <div className="flex flex-col gap-6">
          <TripSettings draft={draft} onChange={setDraft} errors={fieldErrors} />
          <StopList draft={draft} onChange={setDraft} errors={fieldErrors} />
          <PlaceSearch draft={draft} onChange={setDraft} />
        </div>
        <div className="flex flex-col gap-6">
          <section aria-label={t("plan.mapTitle")} className="h-[360px] lg:h-[440px]">
            <TripMapLoader base={saved.baseLocation} stops={draft.stops} plan={plan.data?.plan} />
          </section>
          <PlanPanel
            itineraryId={saved.id}
            plan={plan.data ?? null}
            loading={plan.isPending}
            running={generate.isPending || update.isPending}
            error={generate.error}
            baseName={saved.baseLocation.name}
            onPlan={(strategy) => void runPlan(strategy)}
          />
        </div>
      </div>
    </div>
  );
}
