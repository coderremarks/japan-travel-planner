"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { Spinner } from "@/components/ui/primitives";
import { useI18n } from "@/i18n/I18nProvider";
import { useMe } from "@/lib/api/queries";

/**
 * Client-side gate for signed-in pages. It only improves UX — the API enforces authentication and
 * ownership on every request regardless of what the browser renders.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { t, locale } = useI18n();
  const me = useMe();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (me.data === null) {
      router.replace(`/${locale}/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [me.data, locale, pathname, router]);

  if (!me.data) {
    return (
      <div className="flex justify-center py-24">
        <Spinner label={t("common.loading")} />
      </div>
    );
  }
  return <>{children}</>;
}
