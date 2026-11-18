"use client";

import clsx from "clsx";
import Link from "next/link";
import { Alert, Badge, Button, Card, Spinner } from "@/components/ui/primitives";
import { formatDate, formatMinutes, formatYen } from "@/i18n/format";
import { useI18n } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api/client";
import type { LocalizedText, PlanDay, PlanDocument, PlanLeg, PlanResponse, PlanStrategy } from "@/lib/api/types";
import { dayColor } from "./colors";

const MODE_ICON = { WALK: "🚶", TRANSIT: "🚆", TAXI: "🚕" } as const;

type Props = {
  itineraryId: string;
  plan: PlanResponse | null;
  loading: boolean;
  running: boolean;
  error: unknown;
  baseName: LocalizedText;
  onPlan: (strategy: PlanStrategy) => void;
};

export function PlanPanel({ itineraryId, plan, loading, running, error, baseName, onPlan }: Props) {
  const { t, dynamic } = useI18n();

  return (
    <Card
      title={t("plan.title")}
      actions={
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => onPlan("OPTIMIZE")} disabled={running}>
            {running ? t("plan.running") : `✨ ${t("plan.optimize")}`}
          </Button>
          <Button variant="secondary" onClick={() => onPlan("KEEP_ORDER")} disabled={running}>
            {t("plan.keepOrder")}
          </Button>
        </div>
      }
    >
      <div aria-live="polite" className="flex flex-col gap-4">
        {error != null && (
          <Alert tone="error">
            {error instanceof ApiError ? dynamic(`errors.${error.code}`, t("errors.generic")) : t("errors.network")}
          </Alert>
        )}
        {loading ? (
          <Spinner label={t("common.loading")} />
        ) : !plan?.plan ? (
          <p className="rounded-lg border border-dashed border-stone-300 px-4 py-6 text-center text-sm text-stone-600">
            {t("plan.none")}
          </p>
        ) : (
          <PlanView document={plan.plan} stale={plan.stale} itineraryId={itineraryId} baseName={baseName} />
        )}
      </div>
    </Card>
  );
}

export function PlanView({
  document,
  stale,
  itineraryId,
  baseName,
  printable = false,
}: {
  document: PlanDocument;
  stale: boolean;
  itineraryId: string;
  baseName: LocalizedText;
  printable?: boolean;
}) {
  const { t, locale, dynamic } = useI18n();
  const statusTone = document.status === "COMPLETE" ? "success" : document.status === "PARTIAL" ? "warning" : "error";

  return (
    <div className="flex flex-col gap-4">
      {stale && <Alert tone="warning">{t("plan.stale")}</Alert>}
      <Alert tone={statusTone}>
        <strong>{dynamic(`plan.status${document.status}`, document.status)}</strong>
      </Alert>
      <Summary document={document} />
      {document.notices.length > 0 && (
        <ul className="flex flex-col gap-1 text-xs text-stone-600">
          {document.notices.map((notice) => (
            <li key={notice}>ⓘ {t(`notices.${notice}`)}</li>
          ))}
        </ul>
      )}
      {document.days.map((day) => (
        <DayTimeline key={day.dayIndex} day={day} baseName={baseName[locale]} />
      ))}
      <Unscheduled document={document} />
      {!printable && <Exports itineraryId={itineraryId} />}
      <p className="text-xs text-stone-600">
        {t("plan.solver", { ms: document.solver.elapsedMillis, iterations: document.solver.iterations })}
      </p>
    </div>
  );
}

function Summary({ document }: { document: PlanDocument }) {
  const { t, plural, locale } = useI18n();
  const { summary, comparison } = document;
  const share = summary.budgetYen > 0 ? Math.min(100, (summary.totalCostYen / summary.budgetYen) * 100) : 100;

  let comparisonText: string | null = null;
  if (comparison) {
    const places =
      comparison.additionalPlaces > 0
        ? plural("plan.comparisonPlaces", comparison.additionalPlaces)
        : t("plan.comparisonSamePlaces");
    const minutes = formatMinutes(Math.abs(comparison.travelMinutesSaved), locale);
    const travel =
      comparison.travelMinutesSaved > 0
        ? t("plan.comparisonTravelSaved", { minutes })
        : comparison.travelMinutesSaved < 0
          ? t("plan.comparisonTravelMore", { minutes })
          : t("plan.comparisonTravelSame");
    comparisonText = t("plan.comparison", { places, travel });
  }

  return (
    <div className="flex flex-col gap-3">
      <dl className="grid grid-cols-3 gap-3 text-center">
        <Stat label={t("plan.places")} value={`${summary.scheduledCount}`} />
        <Stat label={t("plan.travel")} value={`≈ ${formatMinutes(summary.travelMinutes, locale)}`} />
        <Stat label={t("plan.cost")} value={`≈ ${formatYen(summary.totalCostYen, locale)}`} />
      </dl>
      <div>
        <div
          className="h-2 overflow-hidden rounded-full bg-stone-100"
          role="meter"
          aria-label={t("plan.cost")}
          aria-valuemin={0}
          aria-valuemax={summary.budgetYen}
          aria-valuenow={summary.totalCostYen}
        >
          <div
            className={clsx("h-full rounded-full", summary.withinBudget ? "bg-ai-500" : "bg-shu-500")}
            style={{ width: `${share}%` }}
          />
        </div>
        <p className="mt-1 text-right text-xs text-stone-600">
          {summary.withinBudget
            ? t("plan.budget", { budget: formatYen(summary.budgetYen, locale) })
            : t("plan.overBudget")}
        </p>
      </div>
      {comparisonText && <p className="bg-ai-50 text-ai-900 rounded-lg px-3 py-2 text-sm">{comparisonText}</p>}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-stone-50 px-2 py-3">
      <dt className="text-xs text-stone-600">{label}</dt>
      <dd className="mt-1 text-sm font-semibold text-stone-900 sm:text-base">{value}</dd>
    </div>
  );
}

function DayTimeline({ day, baseName }: { day: PlanDay; baseName: string }) {
  const { t, locale } = useI18n();
  return (
    <section className="rounded-lg border border-stone-200">
      <header className="flex flex-wrap items-center gap-2 border-b border-stone-200 px-4 py-3">
        <span aria-hidden="true" className="h-3 w-3 rounded-full" style={{ backgroundColor: dayColor(day.dayIndex) }} />
        <h3 className="font-semibold text-stone-900">
          {t("plan.day", { number: day.dayIndex + 1 })} · {formatDate(day.date, locale)}
        </h3>
        {day.holiday && <Badge tone="shu">{t("plan.holiday", { name: day.holiday[locale] ?? "" })}</Badge>}
        <span className="ml-auto text-xs text-stone-600">
          ≈ {formatMinutes(day.travelMinutes, locale)} · ≈ {formatYen(day.transportCostYen + day.admissionYen, locale)}
        </span>
      </header>
      {day.visits.length === 0 ? (
        <p className="px-4 py-3 text-sm text-stone-600">{t("plan.freeDay")}</p>
      ) : (
        <ol className="flex flex-col px-4 py-3">
          <li className="flex gap-3 text-sm text-stone-600">
            <span className="w-12 shrink-0 font-mono">{day.departure}</span>
            <span>
              {t("plan.depart")} · {baseName}
            </span>
          </li>
          {day.visits.map((visit) => (
            <li key={`${visit.placeId}-${visit.start}`} className="flex flex-col">
              <LegLine leg={visit.travel} />
              <div className="flex gap-3">
                <span className="w-12 shrink-0 font-mono text-sm font-semibold text-stone-900">{visit.start}</span>
                <div className="min-w-0 flex-1 rounded-lg bg-stone-50 px-3 py-2">
                  <p className="font-medium text-stone-900">
                    {visit.name[locale]} <span className="font-mono text-xs text-stone-600">– {visit.end}</span>
                  </p>
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    <Badge tone="ai">{t(`categories.${visit.category}`)}</Badge>
                    {visit.mandatory && <Badge tone="shu">★ {t("plan.mustVisit")}</Badge>}
                    {visit.suggested && <Badge tone="amber">{t("plan.suggested")}</Badge>}
                    {visit.admissionYen > 0 && <Badge>≈ {formatYen(visit.admissionYen, locale)}</Badge>}
                  </div>
                  {visit.waitMinutes > 0 && (
                    <p className="mt-1 text-xs text-stone-600">
                      {t("plan.wait", { time: visit.arrival, minutes: formatMinutes(visit.waitMinutes, locale) })}
                    </p>
                  )}
                </div>
              </div>
            </li>
          ))}
          <li className="flex flex-col">
            <LegLine leg={day.returnLeg} />
            <div className="flex gap-3 text-sm text-stone-600">
              <span className="w-12 shrink-0 font-mono">{day.returnArrival}</span>
              <span>{t("plan.returnTo", { place: baseName })}</span>
            </div>
          </li>
        </ol>
      )}
    </section>
  );
}

function LegLine({ leg }: { leg: PlanLeg }) {
  const { t, locale } = useI18n();
  if (leg.minutes === 0 && leg.source === "SAME_LOCATION") {
    return <div className="ml-6 h-3 border-l-2 border-dotted border-stone-300" />;
  }
  const via = leg.via.map((line) => line[locale]).join(" → ");
  return (
    <div className="my-1 ml-6 flex flex-wrap items-center gap-x-2 border-l-2 border-dotted border-stone-300 py-1 pl-6 text-xs text-stone-600">
      <span aria-hidden="true">{MODE_ICON[leg.mode]}</span>
      <span>
        {t(`modes.${leg.mode}`)} ≈ {formatMinutes(leg.minutes, locale)}
        {leg.costYen > 0 && ` · ≈ ${formatYen(leg.costYen, locale)}`}
      </span>
      {via && <span className="text-stone-600">{t("plan.via", { lines: via })}</span>}
      {leg.estimated && (
        <span className="rounded bg-stone-100 px-1 text-[10px] tracking-wide text-stone-700 uppercase">
          {t("common.estimate")}
        </span>
      )}
    </div>
  );
}

function Unscheduled({ document }: { document: PlanDocument }) {
  const { t, locale } = useI18n();
  const relevant = document.unscheduled.filter((entry) => !entry.suggested);
  if (relevant.length === 0) {
    return null;
  }
  return (
    <section className="rounded-lg border border-amber-200 bg-amber-50/60 p-4">
      <h3 className="font-semibold text-stone-900">{t("plan.unscheduledTitle")}</h3>
      <ul className="mt-2 flex flex-col gap-2 text-sm">
        {relevant.map((entry) => (
          <li key={entry.placeId}>
            <span className="font-medium text-stone-900">{entry.name[locale]}</span>
            {entry.mandatory && (
              <span className="ml-2">
                <Badge tone="shu">★ {t("plan.mustVisit")}</Badge>
              </span>
            )}
            <span className="block text-stone-600">{t(`reasons.${entry.reason}`)}</span>
          </li>
        ))}
      </ul>
      {document.hints.length > 0 && (
        <>
          <h4 className="mt-4 text-sm font-semibold text-stone-900">{t("plan.hintsTitle")}</h4>
          <ul className="mt-1 list-disc pl-5 text-sm text-stone-700">
            {document.hints.map((hint) => (
              <li key={hint}>{t(`hints.${hint}`)}</li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

function Exports({ itineraryId }: { itineraryId: string }) {
  const { t, locale } = useI18n();
  const linkClass =
    "rounded-lg bg-white px-3 py-1.5 text-sm font-medium text-ai-700 ring-1 ring-inset ring-ai-200 hover:bg-ai-50";
  return (
    <div className="no-print flex flex-wrap items-center gap-2 border-t border-stone-100 pt-4">
      <span className="text-sm font-medium text-stone-700">{t("plan.exportTitle")}:</span>
      <a className={linkClass} href={`/api/v1/itineraries/${itineraryId}/calendar.ics?lang=${locale}`} download>
        📅 {t("plan.exportIcs")}
      </a>
      <a className={linkClass} href={`/api/v1/itineraries/${itineraryId}/export.json`} download>
        {"{ }"} {t("plan.exportJson")}
      </a>
      <Link className={linkClass} href={`/${locale}/itineraries/${itineraryId}/print`}>
        🖨 {t("plan.print")}
      </Link>
    </div>
  );
}
