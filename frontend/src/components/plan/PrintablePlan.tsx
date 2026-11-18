"use client";

import Link from "next/link";
import { Alert, Button, Spinner } from "@/components/ui/primitives";
import { formatDateRange } from "@/i18n/format";
import { useI18n } from "@/i18n/I18nProvider";
import { useItinerary, usePlan } from "@/lib/api/queries";
import { PlanView } from "./PlanPanel";

export function PrintablePlan({ id }: { id: string }) {
  const { t, locale } = useI18n();
  const itinerary = useItinerary(id);
  const plan = usePlan(id);

  if (itinerary.isPending || plan.isPending) {
    return (
      <div className="flex justify-center py-24">
        <Spinner label={t("common.loading")} />
      </div>
    );
  }
  if (!itinerary.data || !plan.data?.plan) {
    return (
      <div className="mx-auto max-w-xl px-4 py-16">
        <Alert tone="error">{t("errors.PLAN_NOT_FOUND")}</Alert>
      </div>
    );
  }
  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <div className="no-print mb-6 flex items-center justify-between">
        <Link href={`/${locale}/itineraries/${id}`} className="text-ai-700 text-sm font-medium underline">
          ← {t("common.back")}
        </Link>
        <Button onClick={() => window.print()}>🖨 {t("plan.print")}</Button>
      </div>
      <h1 className="text-2xl font-bold text-stone-900">{itinerary.data.title}</h1>
      <p className="mb-6 text-stone-600">
        {formatDateRange(itinerary.data.startDate, itinerary.data.endDate, locale)} ·{" "}
        {itinerary.data.baseLocation.name[locale]}
      </p>
      <PlanView
        document={plan.data.plan}
        stale={Boolean(plan.data.stale)}
        itineraryId={id}
        baseName={itinerary.data.baseLocation.name}
        printable
      />
    </div>
  );
}
