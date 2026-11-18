import Link from "next/link";
import { isLocale } from "@/i18n/config";
import { createTranslator, type MessageKey } from "@/i18n/dictionary";

const FEATURES: { title: MessageKey; body: MessageKey; icon: string }[] = [
  { title: "home.featureOptimizerTitle", body: "home.featureOptimizerBody", icon: "🧭" },
  { title: "home.featureHoursTitle", body: "home.featureHoursBody", icon: "🎌" },
  { title: "home.featureHonestTitle", body: "home.featureHonestBody", icon: "≈" },
  { title: "home.featureBilingualTitle", body: "home.featureBilingualBody", icon: "あ" },
];

export default async function HomePage({ params }: { params: Promise<{ lang: string }> }) {
  const { lang } = await params;
  const locale = isLocale(lang) ? lang : "en";
  const { t } = createTranslator(locale);

  return (
    <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 sm:py-20">
      <section className="max-w-3xl">
        <p className="text-shu-600 mb-3 text-sm font-semibold tracking-[0.25em] uppercase">{t("app.tagline")}</p>
        <h1 className="text-4xl leading-tight font-bold text-stone-900 sm:text-5xl">{t("home.title")}</h1>
        <p className="mt-6 text-lg leading-8 text-stone-600">{t("home.subtitle")}</p>
        <div className="mt-8 flex flex-wrap gap-3">
          <Link
            href={`/${locale}/register`}
            className="bg-ai-600 hover:bg-ai-700 rounded-lg px-5 py-3 text-sm font-semibold text-white shadow-sm"
          >
            {t("home.cta")}
          </Link>
          <Link
            href={`/${locale}/login`}
            className="text-ai-700 ring-ai-200 hover:bg-ai-50 rounded-lg bg-white px-5 py-3 text-sm font-semibold ring-1 ring-inset"
          >
            {t("home.secondary")}
          </Link>
        </div>
      </section>
      <section aria-label={t("app.tagline")} className="mt-16 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {FEATURES.map((feature) => (
          <article key={feature.title} className="rounded-xl border border-stone-200 bg-white p-5 shadow-sm">
            <span
              aria-hidden="true"
              className="bg-ai-50 text-ai-700 mb-3 grid h-10 w-10 place-items-center rounded-lg text-lg font-bold"
            >
              {feature.icon}
            </span>
            <h2 className="font-semibold text-stone-900">{t(feature.title)}</h2>
            <p className="mt-2 text-sm leading-6 text-stone-600">{t(feature.body)}</p>
          </article>
        ))}
      </section>
    </div>
  );
}
