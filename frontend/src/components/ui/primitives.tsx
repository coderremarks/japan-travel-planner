import clsx from "clsx";
import type { ButtonHTMLAttributes, ReactNode } from "react";

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  primary: "bg-ai-600 text-white hover:bg-ai-700 disabled:bg-ai-200",
  secondary: "bg-white text-ai-700 ring-1 ring-inset ring-ai-200 hover:bg-ai-50 disabled:text-stone-400",
  ghost: "text-stone-700 hover:bg-stone-100 disabled:text-stone-400",
  danger: "bg-white text-shu-600 ring-1 ring-inset ring-shu-500/40 hover:bg-shu-50",
};

export function Button({
  variant = "primary",
  size = "md",
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; size?: "sm" | "md" }) {
  return (
    <button
      type="button"
      {...props}
      className={clsx(
        "inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors disabled:cursor-not-allowed",
        size === "sm" ? "px-3 py-1.5 text-sm" : "px-4 py-2.5 text-sm",
        BUTTON_STYLES[variant],
        className,
      )}
    />
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <span role="status" className="inline-flex items-center gap-2 text-sm text-stone-600">
      <span aria-hidden="true" className="border-ai-200 border-t-ai-600 h-4 w-4 animate-spin rounded-full border-2" />
      {label}
    </span>
  );
}

type AlertTone = "info" | "success" | "warning" | "error";

const ALERT_STYLES: Record<AlertTone, string> = {
  info: "border-ai-200 bg-ai-50 text-ai-900",
  success: "border-emerald-200 bg-emerald-50 text-emerald-900",
  warning: "border-amber-200 bg-amber-50 text-amber-900",
  error: "border-shu-500/30 bg-shu-50 text-shu-600",
};

export function Alert({ tone = "info", children }: { tone?: AlertTone; children: ReactNode }) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={clsx("rounded-lg border px-4 py-3 text-sm", ALERT_STYLES[tone])}
    >
      {children}
    </div>
  );
}

export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: "neutral" | "ai" | "shu" | "amber";
}) {
  return (
    <span
      className={clsx(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        tone === "neutral" && "bg-stone-100 text-stone-700",
        tone === "ai" && "bg-ai-100 text-ai-700",
        tone === "shu" && "bg-shu-50 text-shu-600",
        tone === "amber" && "bg-amber-100 text-amber-800",
      )}
    >
      {children}
    </span>
  );
}

export function Card({
  title,
  actions,
  children,
  className,
}: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={clsx("rounded-xl border border-stone-200 bg-white p-5 shadow-sm", className)}>
      {(title || actions) && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          {title && <h2 className="text-base font-semibold text-stone-900">{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

/** Label, control, hint and error wired together with ids for screen readers. */
export function Field({
  id,
  label,
  hint,
  error,
  children,
}: {
  id: string;
  label: ReactNode;
  hint?: ReactNode;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-stone-800">
        {label}
      </label>
      {children}
      {hint && (
        <p id={`${id}-hint`} className="text-xs text-stone-600">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-shu-600 text-xs font-medium">
          {error}
        </p>
      )}
    </div>
  );
}

export const inputClass =
  "w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm text-stone-900 shadow-sm placeholder:text-stone-400 focus:border-ai-500 aria-[invalid=true]:border-shu-500";

export function describedBy(id: string, hint?: unknown, error?: unknown): string | undefined {
  const ids = [hint ? `${id}-hint` : null, error ? `${id}-error` : null].filter(Boolean);
  return ids.length ? ids.join(" ") : undefined;
}
