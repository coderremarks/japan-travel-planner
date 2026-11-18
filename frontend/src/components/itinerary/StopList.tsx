"use client";

import clsx from "clsx";
import { Badge, Card } from "@/components/ui/primitives";
import { formatMinutes, formatYen } from "@/i18n/format";
import { useI18n } from "@/i18n/I18nProvider";
import { moveStop, removeStop, toggleMandatory, type Draft } from "./draft";

type Props = { draft: Draft; onChange: (draft: Draft) => void; errors: Record<string, string> };

/** Reordering uses buttons rather than drag-and-drop only, so it works with keyboard and screen readers. */
export function StopList({ draft, onChange, errors }: Props) {
  const { t, locale, dynamic } = useI18n();
  const stops = draft.stops;
  const setStops = (next: Draft["stops"]) => onChange({ ...draft, stops: next });

  return (
    <Card title={`${t("stops.title")} (${stops.length})`}>
      {stops.length === 0 ? (
        <p className="rounded-lg border border-dashed border-stone-300 px-4 py-6 text-center text-sm text-stone-600">
          {t("stops.empty")}
        </p>
      ) : (
        <ol className="flex flex-col gap-2" aria-label={t("stops.title")}>
          {stops.map((stop, index) => {
            const name = stop.place.name[locale];
            const error = errors[`stops[${index}].placeId`];
            return (
              <li
                key={stop.place.id}
                className={clsx(
                  "flex items-center gap-3 rounded-lg border px-3 py-2",
                  stop.mandatory ? "border-shu-500/40 bg-shu-50/60" : "border-stone-200 bg-white",
                )}
              >
                <span className="w-6 text-center text-sm font-semibold text-stone-600">{index + 1}</span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-stone-900">{name}</p>
                  <p className="text-xs text-stone-600">
                    {t(`categories.${stop.place.category}`)} · {formatMinutes(stop.place.visitMinutes, locale)}
                    {stop.place.admissionYen > 0 && ` · ≈ ${formatYen(stop.place.admissionYen, locale)}`}
                  </p>
                  {error && <p className="text-shu-600 text-xs">{dynamic(`fieldErrors.${error}`, error)}</p>}
                </div>
                <label
                  className="flex items-center gap-1.5 text-xs font-medium text-stone-700"
                  title={t("stops.mustVisitHint")}
                >
                  <input
                    type="checkbox"
                    checked={stop.mandatory}
                    onChange={() => setStops(toggleMandatory(stops, stop.place.id))}
                    aria-label={`${t("stops.mustVisit")}: ${name}`}
                  />
                  <span className="hidden sm:inline">{t("stops.mustVisit")}</span>
                  {stop.mandatory && (
                    <span className="sm:hidden">
                      <Badge tone="shu">★</Badge>
                    </span>
                  )}
                </label>
                <div className="flex items-center">
                  <IconButton
                    label={`${t("common.moveUp")}: ${name}`}
                    disabled={index === 0}
                    onClick={() => setStops(moveStop(stops, index, -1))}
                  >
                    ↑
                  </IconButton>
                  <IconButton
                    label={`${t("common.moveDown")}: ${name}`}
                    disabled={index === stops.length - 1}
                    onClick={() => setStops(moveStop(stops, index, 1))}
                  >
                    ↓
                  </IconButton>
                  <IconButton
                    label={`${t("common.remove")}: ${name}`}
                    onClick={() => setStops(removeStop(stops, stop.place.id))}
                  >
                    ✕
                  </IconButton>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </Card>
  );
}

function IconButton({
  label,
  children,
  ...props
}: { label: string; children: React.ReactNode } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      {...props}
      className="grid h-8 w-8 place-items-center rounded-md text-stone-600 hover:bg-stone-100 disabled:text-stone-300"
    >
      <span aria-hidden="true">{children}</span>
    </button>
  );
}
