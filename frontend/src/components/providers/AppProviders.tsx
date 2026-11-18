"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { ApiError } from "@/lib/api/client";
import type { Locale } from "@/i18n/config";
import { I18nProvider } from "@/i18n/I18nProvider";

export function AppProviders({ locale, children }: { locale: Locale; children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            // Client errors (4xx) will not fix themselves by retrying.
            retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
          },
        },
      }),
  );
  return (
    <QueryClientProvider client={queryClient}>
      <I18nProvider locale={locale}>{children}</I18nProvider>
    </QueryClientProvider>
  );
}
