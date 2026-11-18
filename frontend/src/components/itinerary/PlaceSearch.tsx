"use client";

import { useDeferredValue, useState } from "react";
import { Badge, Button, Card, Field, Spinner, inputClass } from "@/components/ui/primitives";
import { formatMinutes, formatYen } from "@/i18n/format";
import { useI18n } from "@/i18n/I18nProvider";
import { useAreas, usePlaces } from "@/lib/api/queries";
import { CATEGORIES, type Category } from "@/lib/api/types";
import { addStop, type Draft } from "./draft";

export function PlaceSearch({ draft, onChange }: { draft: Draft; onChange: (draft: Draft) => void }) {
  const { t, locale } = useI18n();
  const areas = useAreas();
  const [area, setArea] = useState("");
  const [category, setCategory] = useState<Category | "">("");
  const [query, setQuery] = useState("");
  // Deferred value keeps typing responsive and avoids a request per keystroke.
  const deferredQuery = useDeferredValue(query);
  const places = usePlaces({ area, category: category || undefined, q: deferredQuery });
  const chosen = new Set(draft.stops.map((stop) => stop.place.id));

  return (
    <Card title={t("search.title")}>
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="sm:col-span-3">
          <Field id="place-query" label={t("search.query")}>
            <input
              id="place-query"
              type="search"
              className={inputClass}
              placeholder={t("search.placeholder")}
              maxLength={100}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </Field>
        </div>
        <Field id="place-area" label={t("search.area")}>
          <select id="place-area" className={inputClass} value={area} onChange={(e) => setArea(e.target.value)}>
            <option value="">{t("search.anyArea")}</option>
            {areas.data?.map((a) => (
              <option key={a.code} value={a.code}>
                {a.name[locale]}
              </option>
            ))}
          </select>
        </Field>
        <div className="sm:col-span-2">
          <Field id="place-category" label={t("search.category")}>
            <select
              id="place-category"
              className={inputClass}
              value={category}
              onChange={(e) => setCategory(e.target.value as Category | "")}
            >
              <option value="">{t("search.anyCategory")}</option>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {t(`categories.${c}`)}
                </option>
              ))}
            </select>
          </Field>
        </div>
      </div>
      <div className="mt-4" aria-live="polite">
        {places.isPending ? (
          <Spinner label={t("common.loading")} />
        ) : places.data && places.data.content.length === 0 ? (
          <p className="text-sm text-stone-600">{t("search.noResults")}</p>
        ) : (
          <ul className="flex max-h-96 flex-col divide-y divide-stone-100 overflow-y-auto">
            {places.data?.content.map((place) => {
              const added = chosen.has(place.id);
              return (
                <li key={place.id} className="flex items-start gap-3 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-stone-900">
                      {place.name[locale]}{" "}
                      <span className="text-xs font-normal text-stone-600">
                        {locale === "ja" ? place.name.en : place.name.ja}
                      </span>
                    </p>
                    <p className="mt-0.5 text-xs text-stone-600">{place.summary[locale]}</p>
                    <div className="mt-1.5 flex flex-wrap gap-1.5">
                      <Badge tone="ai">{t(`categories.${place.category}`)}</Badge>
                      <Badge>{t("search.visit", { minutes: formatMinutes(place.visitMinutes, locale) })}</Badge>
                      <Badge>
                        {place.admissionYen > 0
                          ? t("search.admission", { price: formatYen(place.admissionYen, locale) })
                          : t("search.free")}
                      </Badge>
                      {place.dataSource === "SAMPLE_UNVERIFIED" && <Badge tone="amber">{t("search.sample")}</Badge>}
                    </div>
                  </div>
                  <Button
                    variant={added ? "ghost" : "secondary"}
                    size="sm"
                    disabled={added}
                    aria-label={`${added ? t("common.added") : t("common.add")}: ${place.name[locale]}`}
                    onClick={() => onChange({ ...draft, stops: addStop(draft.stops, place) })}
                  >
                    {added ? `✓ ${t("common.added")}` : `＋ ${t("common.add")}`}
                  </Button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Card>
  );
}
