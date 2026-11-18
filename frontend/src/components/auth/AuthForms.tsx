"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Alert, Button, Field, describedBy, inputClass } from "@/components/ui/primitives";
import { useI18n } from "@/i18n/I18nProvider";
import { ApiError } from "@/lib/api/client";
import { useLogin, useRegister } from "@/lib/api/queries";
import { safeRedirectPath } from "@/lib/navigation";

function useErrorText() {
  const { t, dynamic } = useI18n();
  return {
    message(error: unknown): string {
      if (error instanceof ApiError) {
        return dynamic(`errors.${error.code}`, t("errors.generic"));
      }
      return t("errors.network");
    },
    field(error: unknown, field: string): string | undefined {
      if (!(error instanceof ApiError)) {
        return undefined;
      }
      const codes = error.violations
        .filter((v) => v.field === field)
        .map((v) => dynamic(`fieldErrors.${v.code}`, v.code));
      return codes.length ? codes.join(" ") : undefined;
    },
  };
}

function AuthCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-md px-4 py-12 sm:py-16">
      <div className="rounded-2xl border border-stone-200 bg-white p-6 shadow-sm sm:p-8">
        <h1 className="mb-6 text-2xl font-bold text-stone-900">{title}</h1>
        {children}
      </div>
    </div>
  );
}

export function LoginForm() {
  const { t, locale } = useI18n();
  const router = useRouter();
  const next = useSearchParams().get("next");
  const login = useLogin();
  const errors = useErrorText();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    login.mutate({ email, password }, { onSuccess: () => router.push(safeRedirectPath(next, locale)) });
  }

  return (
    <AuthCard title={t("auth.loginTitle")}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        {next && !login.error && <Alert>{t("auth.signInRequired")}</Alert>}
        {login.error && <Alert tone="error">{errors.message(login.error)}</Alert>}
        <Field id="email" label={t("auth.email")}>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={inputClass}
          />
        </Field>
        <Field id="password" label={t("auth.password")}>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={inputClass}
          />
        </Field>
        <Button type="submit" disabled={login.isPending}>
          {login.isPending ? t("common.loading") : t("auth.loginSubmit")}
        </Button>
        <p className="text-center text-sm text-stone-600">
          {t("auth.noAccount")}{" "}
          <Link className="text-ai-700 font-medium underline" href={`/${locale}/register`}>
            {t("nav.signUp")}
          </Link>
        </p>
      </form>
    </AuthCard>
  );
}

export function RegisterForm() {
  const { t, locale } = useI18n();
  const router = useRouter();
  const register = useRegister();
  const errors = useErrorText();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [preferredLocale, setPreferredLocale] = useState<"en" | "ja">(locale);

  function submit(event: FormEvent) {
    event.preventDefault();
    register.mutate(
      { displayName, email, password, preferredLocale },
      { onSuccess: () => router.push(`/${preferredLocale}/itineraries`) },
    );
  }

  const passwordError = errors.field(register.error, "password");
  const emailError = errors.field(register.error, "email");
  const nameError = errors.field(register.error, "displayName");

  return (
    <AuthCard title={t("auth.registerTitle")}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-4">
        {register.error && <Alert tone="error">{errors.message(register.error)}</Alert>}
        <Field id="displayName" label={t("auth.displayName")} error={nameError}>
          <input
            id="displayName"
            autoComplete="nickname"
            required
            maxLength={60}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            aria-invalid={Boolean(nameError)}
            aria-describedby={describedBy("displayName", false, nameError)}
            className={inputClass}
          />
        </Field>
        <Field id="email" label={t("auth.email")} error={emailError}>
          <input
            id="email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={Boolean(emailError)}
            aria-describedby={describedBy("email", false, emailError)}
            className={inputClass}
          />
        </Field>
        <Field id="password" label={t("auth.password")} hint={t("auth.passwordHint")} error={passwordError}>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={12}
            maxLength={128}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={Boolean(passwordError)}
            aria-describedby={describedBy("password", true, passwordError)}
            className={inputClass}
          />
        </Field>
        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium text-stone-800">{t("auth.language")}</legend>
          <div className="flex gap-4 text-sm">
            {(["ja", "en"] as const).map((option) => (
              <label key={option} className="flex items-center gap-2">
                <input
                  type="radio"
                  name="preferredLocale"
                  value={option}
                  checked={preferredLocale === option}
                  onChange={() => setPreferredLocale(option)}
                />
                {option === "ja" ? "日本語" : "English"}
              </label>
            ))}
          </div>
        </fieldset>
        <Button type="submit" disabled={register.isPending}>
          {register.isPending ? t("common.loading") : t("auth.registerSubmit")}
        </Button>
        <p className="text-center text-sm text-stone-600">
          {t("auth.haveAccount")}{" "}
          <Link className="text-ai-700 font-medium underline" href={`/${locale}/login`}>
            {t("nav.signIn")}
          </Link>
        </p>
      </form>
    </AuthCard>
  );
}
