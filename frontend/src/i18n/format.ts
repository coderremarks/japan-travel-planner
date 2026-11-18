import type { Locale } from "./config";

const BCP47: Record<Locale, string> = { en: "en-JP", ja: "ja-JP" };

/** Yen without decimals: "¥12,300" / "￥12,300". */
export function formatYen(amount: number, locale: Locale): string {
  return new Intl.NumberFormat(BCP47[locale], {
    style: "currency",
    currency: "JPY",
    maximumFractionDigits: 0,
  }).format(amount);
}

/** "1 h 25 min" / "1時間25分". */
export function formatMinutes(total: number, locale: Locale): string {
  const hours = Math.floor(total / 60);
  const minutes = total % 60;
  if (locale === "ja") {
    return hours > 0 ? `${hours}時間${minutes > 0 ? `${minutes}分` : ""}` : `${minutes}分`;
  }
  if (hours === 0) {
    return `${minutes} min`;
  }
  return minutes === 0 ? `${hours} h` : `${hours} h ${minutes} min`;
}

/** ISO date (YYYY-MM-DD) as "Tue, Dec 8" / "12月8日(火)" — parsed as a calendar date, never shifted by time zone. */
export function formatDate(isoDate: string, locale: Locale): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return new Intl.DateTimeFormat(BCP47[locale], {
    month: "short",
    day: "numeric",
    weekday: "short",
    timeZone: "UTC",
  }).format(date);
}

export function formatDateRange(start: string, end: string, locale: Locale): string {
  return start === end ? formatDate(start, locale) : `${formatDate(start, locale)} – ${formatDate(end, locale)}`;
}

/** Instant (ISO with zone) in Japan time, e.g. for "updated" labels. */
export function formatDateTime(instant: string, locale: Locale): string {
  return new Intl.DateTimeFormat(BCP47[locale], {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Asia/Tokyo",
  }).format(new Date(instant));
}

export function formatDistance(meters: number, locale: Locale): string {
  return meters < 1000
    ? `${Math.round(meters)} m`
    : `${new Intl.NumberFormat(BCP47[locale], { maximumFractionDigits: 1 }).format(meters / 1000)} km`;
}
