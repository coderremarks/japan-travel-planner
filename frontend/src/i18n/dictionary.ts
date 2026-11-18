import en from "./messages/en.json";
import ja from "./messages/ja.json";
import type { Locale } from "./config";

export type Messages = typeof en;

/**
 * The Japanese catalogue must have exactly the English shape: a missing or misspelt key is a
 * TypeScript compile error, not a blank label in production.
 */
const japanese: Messages = ja;

const dictionaries: Record<Locale, Messages> = { en, ja: japanese };

export function getMessages(locale: Locale): Messages {
  return dictionaries[locale];
}

/** Dot-separated paths to every string leaf, e.g. `"plan.optimize"`. */
type Leaves<T, Prefix extends string = ""> = {
  [K in keyof T & string]: T[K] extends string ? `${Prefix}${K}` : Leaves<T[K], `${Prefix}${K}.`>;
}[keyof T & string];

export type MessageKey = Leaves<Messages>;

/** Keys that exist with `_one`/`_other` suffixes, usable with plural translation. */
export type PluralKey = MessageKey extends infer K ? (K extends `${infer Base}_other` ? Base : never) : never;

export type Values = Record<string, string | number>;

export function lookup(messages: Messages, key: string): string {
  let node: unknown = messages;
  for (const part of key.split(".")) {
    node = (node as Record<string, unknown> | undefined)?.[part];
  }
  return typeof node === "string" ? node : key;
}

export function interpolate(template: string, values?: Values): string {
  if (!values) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (match, name: string) => (name in values ? String(values[name]) : match));
}

/** Builds a translate function; React escapes the result, so values cannot inject markup. */
export function createTranslator(locale: Locale) {
  const messages = getMessages(locale);
  const pluralRules = new Intl.PluralRules(locale);
  const t = (key: MessageKey, values?: Values) => interpolate(lookup(messages, key), values);
  const plural = (key: PluralKey, count: number, values?: Values) => {
    const category = pluralRules.select(count) === "one" ? "one" : "other";
    return interpolate(lookup(messages, `${key}_${category}`), { count, ...values });
  };
  /** For codes coming from the API (error codes, reasons) that may not have a translation. */
  const dynamic = (key: string, fallback: string) => {
    const text = lookup(messages, key);
    return text === key ? fallback : text;
  };
  return { t, plural, dynamic };
}

export type Translator = ReturnType<typeof createTranslator>;
