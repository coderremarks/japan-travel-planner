"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { LOCALE_COOKIE, type Locale } from "@/i18n/config";
import { useI18n } from "@/i18n/I18nProvider";
import { useLogout, useMe } from "@/lib/api/queries";
import { switchLocalePath } from "@/lib/navigation";
import { Button } from "@/components/ui/primitives";

export function SiteHeader() {
  const { t, locale } = useI18n();
  const me = useMe();
  const logout = useLogout();
  const router = useRouter();

  return (
    <header className="no-print border-b border-stone-200 bg-white/90 backdrop-blur">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2"
      >
        {t("nav.skipToContent")}
      </a>
      <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <Link href={`/${locale}`} className="text-ai-700 flex items-baseline gap-2">
          <span className="text-xl font-bold tracking-wide">{t("app.nameJa")}</span>
          <span className="text-sm font-semibold tracking-[0.2em] text-stone-600 uppercase">{t("app.name")}</span>
        </Link>
        <nav aria-label="Main" className="flex flex-wrap items-center gap-2 text-sm">
          {me.data && (
            <Link
              className="rounded-lg px-3 py-2 font-medium text-stone-700 hover:bg-stone-100"
              href={`/${locale}/itineraries`}
            >
              {t("nav.myTrips")}
            </Link>
          )}
          <LanguageSwitcher />
          {me.data ? (
            <>
              <span className="hidden px-2 text-stone-600 md:inline">
                {t("nav.signedInAs", { name: me.data.displayName })}
              </span>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => logout.mutate(undefined, { onSuccess: () => router.push(`/${locale}`) })}
              >
                {t("nav.signOut")}
              </Button>
            </>
          ) : (
            me.data === null && (
              <>
                <Link
                  className="rounded-lg px-3 py-2 font-medium text-stone-700 hover:bg-stone-100"
                  href={`/${locale}/login`}
                >
                  {t("nav.signIn")}
                </Link>
                <Link
                  className="bg-ai-600 hover:bg-ai-700 rounded-lg px-3 py-2 font-medium text-white"
                  href={`/${locale}/register`}
                >
                  {t("nav.signUp")}
                </Link>
              </>
            )
          )}
        </nav>
      </div>
    </header>
  );
}

function LanguageSwitcher() {
  const { t, locale } = useI18n();
  const pathname = usePathname();
  const other: Locale = locale === "en" ? "ja" : "en";
  return (
    <a
      href={switchLocalePath(pathname, other)}
      hrefLang={other}
      lang={other}
      onClick={() => {
        document.cookie = `${LOCALE_COOKIE}=${other}; path=/; max-age=31536000; SameSite=Lax`;
      }}
      className="rounded-lg px-3 py-2 font-medium text-stone-700 ring-1 ring-stone-200 ring-inset hover:bg-stone-100"
      aria-label={`${t("nav.language")}: ${other === "ja" ? "日本語" : "English"}`}
    >
      {other === "ja" ? "日本語" : "English"}
    </a>
  );
}
