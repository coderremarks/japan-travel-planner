import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    rules: {
      // XSS guard rails: React escapes everything except these escape hatches.
      "react/no-danger": "error",
      "no-restricted-syntax": [
        "error",
        {
          selector: "MemberExpression[property.name='innerHTML']",
          message: "Do not write innerHTML; render with React so content is escaped.",
        },
      ],
      "no-console": ["error", { allow: ["warn", "error"] }],
    },
  },
  {
    // Playwright fixtures call a parameter named `use`; it is not a React hook.
    files: ["e2e/**"],
    rules: { "react-hooks/rules-of-hooks": "off" },
  },
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "coverage/**",
    "playwright-report/**",
    "test-results/**",
    "next-env.d.ts",
    "src/lib/api/schema.d.ts",
  ]),
]);

export default eslintConfig;
