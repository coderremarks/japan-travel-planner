"use client";

import clsx from "clsx";
import { Card, Field, describedBy, inputClass } from "@/components/ui/primitives";
import { useI18n } from "@/i18n/I18nProvider";
import { useAreas, useBaseLocations } from "@/lib/api/queries";
import { CATEGORIES, TRAVEL_MODES } from "@/lib/api/types";
import { toggleValue, type Draft } from "./draft";

type Props = { draft: Draft; onChange: (draft: Draft) => void; errors: Record<string, string> };

export function TripSettings({ draft, onChange, errors }: Props) {
  const { t, locale, dynamic } = useI18n();
  const areas = useAreas();
  const bases = useBaseLocations();
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => onChange({ ...draft, [key]: value });
  const error = (field: string) => (errors[field] ? dynamic(`fieldErrors.${errors[field]}`, errors[field]) : undefined);

  return (
    <Card title={t("editor.settings")}>
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <Field id="title" label={t("editor.title")} error={error("title")}>
            <input
              id="title"
              className={inputClass}
              maxLength={120}
              value={draft.title}
              aria-invalid={Boolean(error("title"))}
              aria-describedby={describedBy("title", false, error("title"))}
              onChange={(e) => set("title", e.target.value)}
            />
          </Field>
        </div>
        <Field id="startDate" label={t("editor.startDate")} error={error("startDate")}>
          <input
            id="startDate"
            type="date"
            className={inputClass}
            value={draft.startDate}
            onChange={(e) => set("startDate", e.target.value)}
          />
        </Field>
        <Field id="endDate" label={t("editor.endDate")} error={error("endDate")}>
          <input
            id="endDate"
            type="date"
            className={inputClass}
            min={draft.startDate}
            value={draft.endDate}
            aria-invalid={Boolean(error("endDate"))}
            aria-describedby={describedBy("endDate", false, error("endDate"))}
            onChange={(e) => set("endDate", e.target.value)}
          />
        </Field>
        <Field id="dayStart" label={t("editor.dayStart")}>
          <input
            id="dayStart"
            type="time"
            className={inputClass}
            value={draft.dayStart}
            onChange={(e) => set("dayStart", e.target.value)}
          />
        </Field>
        <Field id="dayEnd" label={t("editor.dayEnd")} error={error("dayEnd")}>
          <input
            id="dayEnd"
            type="time"
            className={inputClass}
            value={draft.dayEnd}
            aria-invalid={Boolean(error("dayEnd"))}
            aria-describedby={describedBy("dayEnd", false, error("dayEnd"))}
            onChange={(e) => set("dayEnd", e.target.value)}
          />
        </Field>
        <Field id="budget" label={t("editor.budget")} hint={t("editor.budgetHint")} error={error("budgetYen")}>
          <input
            id="budget"
            type="number"
            inputMode="numeric"
            min={0}
            max={10000000}
            step={1000}
            className={inputClass}
            value={draft.budgetYen}
            aria-describedby={describedBy("budget", true, error("budgetYen"))}
            onChange={(e) => set("budgetYen", Math.max(0, Number(e.target.value) || 0))}
          />
        </Field>
        <Field id="maxVisits" label={t("editor.maxVisits")}>
          <input
            id="maxVisits"
            type="number"
            min={1}
            max={12}
            className={inputClass}
            value={draft.maxVisitsPerDay}
            onChange={(e) => set("maxVisitsPerDay", Math.min(12, Math.max(1, Number(e.target.value) || 1)))}
          />
        </Field>
        <div className="sm:col-span-2">
          <Field id="base" label={t("editor.base")}>
            <select
              id="base"
              className={inputClass}
              value={draft.baseLocationId}
              onChange={(e) => set("baseLocationId", e.target.value)}
            >
              {areas.data?.map((area) => (
                <optgroup key={area.code} label={area.name[locale] ?? area.code}>
                  {bases.data
                    ?.filter((base) => base.areaCode === area.code)
                    .map((base) => (
                      <option key={base.id} value={base.id}>
                        {base.name[locale]}
                      </option>
                    ))}
                </optgroup>
              ))}
            </select>
          </Field>
        </div>
        <fieldset className="sm:col-span-2">
          <legend className="mb-2 text-sm font-medium text-stone-800">{t("editor.interests")}</legend>
          <div className="flex flex-wrap gap-2">
            {CATEGORIES.map((category) => {
              const active = draft.interests.includes(category);
              return (
                <button
                  key={category}
                  type="button"
                  aria-pressed={active}
                  onClick={() => set("interests", toggleValue(draft.interests, category))}
                  className={clsx(
                    "rounded-full px-3 py-1.5 text-sm font-medium ring-1 transition-colors ring-inset",
                    active
                      ? "bg-ai-600 ring-ai-600 text-white"
                      : "bg-white text-stone-700 ring-stone-300 hover:bg-stone-50",
                  )}
                >
                  {t(`categories.${category}`)}
                </button>
              );
            })}
          </div>
        </fieldset>
        <fieldset className="sm:col-span-2">
          <legend className="mb-2 text-sm font-medium text-stone-800">{t("editor.travelModes")}</legend>
          <div className="flex flex-wrap gap-4 text-sm">
            {TRAVEL_MODES.map((mode) => (
              <label key={mode} className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={draft.travelModes.includes(mode)}
                  onChange={() => {
                    const next = toggleValue(draft.travelModes, mode);
                    if (next.length > 0) {
                      set("travelModes", next);
                    }
                  }}
                />
                {t(`modes.${mode}`)}
              </label>
            ))}
          </div>
        </fieldset>
        <label className="flex items-start gap-3 text-sm sm:col-span-2">
          <input
            type="checkbox"
            className="mt-1"
            checked={draft.includeSuggestions}
            onChange={(e) => set("includeSuggestions", e.target.checked)}
          />
          <span>
            <span className="font-medium text-stone-800">{t("editor.suggestions")}</span>
            <span className="block text-xs text-stone-600">{t("editor.suggestionsHint")}</span>
          </span>
        </label>
      </div>
    </Card>
  );
}
