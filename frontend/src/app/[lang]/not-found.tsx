"use client";

import Link from "next/link";
import { useI18n } from "@/i18n/I18nProvider";

export default function NotFound() {
  const { t, locale } = useI18n();
  return (
    <div className="mx-auto max-w-xl px-4 py-24 text-center">
      <h1 className="text-2xl font-bold text-stone-900">{t("notFound.title")}</h1>
      <p className="mt-3 text-stone-600">{t("notFound.body")}</p>
      <Link href={`/${locale}`} className="text-ai-700 mt-6 inline-block font-medium underline">
        {t("notFound.home")}
      </Link>
    </div>
  );
}
