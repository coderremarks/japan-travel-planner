import type { Metadata } from "next";
import { headers } from "next/headers";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import "../globals.css";
import { AppProviders } from "@/components/providers/AppProviders";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { isLocale } from "@/i18n/config";
import { createTranslator } from "@/i18n/dictionary";

export async function generateMetadata({ params }: { params: Promise<{ lang: string }> }): Promise<Metadata> {
  const { lang } = await params;
  const { t } = createTranslator(isLocale(lang) ? lang : "en");
  return {
    title: { default: `${t("app.name")} ${t("app.nameJa")} — ${t("app.tagline")}`, template: `%s | ${t("app.name")}` },
    description: t("home.subtitle"),
    robots: { index: false, follow: false },
  };
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ lang: string }>;
}) {
  const { lang } = await params;
  if (!isLocale(lang)) {
    notFound();
  }
  // Reading the request headers makes every page dynamic, which per-request CSP nonces require.
  await headers();
  const { t } = createTranslator(lang);

  return (
    <html lang={lang}>
      <body className="flex min-h-screen flex-col">
        <AppProviders locale={lang}>
          <SiteHeader />
          <main id="main" className="flex-1">
            {children}
          </main>
          <footer className="no-print border-t border-stone-200 bg-white">
            <div className="mx-auto max-w-7xl space-y-1 px-4 py-6 text-xs text-stone-600 sm:px-6">
              <p>{t("footer.disclaimer")}</p>
              <p>{t("footer.mapData")}</p>
            </div>
          </footer>
        </AppProviders>
      </body>
    </html>
  );
}
