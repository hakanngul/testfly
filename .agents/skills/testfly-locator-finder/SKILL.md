---
name: testfly-locator-finder
description: Scan a web page URL and extract TestFly Locator declarations (getByTestId, getByLabel, getByRole, getByPlaceholder, findById, find CSS) verified against the real io.testfly.locator.Locator. Use when asked to go to / open / scan / crawl a link or page and find, list, extract, or generate locators, selectors, or Page Object fields for TestFly; not for writing full test flows (use testfly-web-write) or SDK changes.
---

# TestFly locator finder

Turns "go to this link, scan the page, give me locators" into ready-to-paste `Locator` fields. The scanner proposes the most resilient expression per element; the driver then re-resolves each one through the real `io.testfly.locator.Locator` and keeps only those that hit exactly the scanned node. Paths below are relative to the repository root.

## Run (agent path)

```bash
.agents/skills/testfly-locator-finder/scripts/find-locators.sh https://www.saucedemo.com --class LoginPage
```

- First run compiles TestFly (`mvn -q compile`) and caches the runtime classpath in `target/locator-finder/`; later runs take seconds. Chrome must be installed; Selenium Manager fetches the driver.
- Output (stdout): a Markdown table (`field | locator | strategy | status | alternatives`) followed by a `BasePage` subclass containing only `VERIFIED` fields.
- Options: `--out f.md`, `--json f.json` (full report incl. `spec`), `--screenshot f.png`, `--wait ms` (default 1500 for SPA hydration), `--test-id-attr data-test`, `--include-hidden`, `--headed`.
- Offline smoke check: `find-locators.sh "file://$PWD/.agents/skills/testfly-locator-finder/scripts/fixture.html"` must report 13 elements, 13 verified.

## Run (browser-tool path)

Use this when the page needs a logged-in session or several clicks to reach, and the agent has a browser tool with JS evaluation (in-app browser or Claude in Chrome). Navigate there, then evaluate the scanner's file content followed by a call:

```js
// <contents of scripts/locator-scan.js>(null)   -> JSON string
// or, same-origin: (0, eval)(await (await fetch('/locator-scan.js')).text())(null)
```

This path uses the scanner's embedded copy of the matching rules and is **not** verified by the Java `Locator`. Say so in the handoff, or re-run the driver on a reachable URL.

## Reading the result

Priority order: `getByTestId` → `getByLabel` (form controls) → `getByRole(Role.X, name)` → `getByPlaceholder` / `getByAltText` / `getByTitle` → `findById` / `findByName` (only stable-looking values) → `getByText` → `find(css)` → `.nth(i)`. Every row escalates to `.exact()` before falling to the next strategy.

- `VERIFIED_NTH` and any `:nth-of-type(` CSS are positional and get a comment in the generated class. Suggest a test id to the app team, or scope with `within(By)` by hand.
- A `Test-id attribute in use` note means the page uses another convention (`data-test`, `data-qa`, `data-cy`). Re-run with `--test-id-attr <attr>`, and tell the user to call `Locator.setTestIdAttribute("<attr>")` once before tests start. It is process-wide, so never call it inside a test.
- Field names are derived from accessible names and transliterated to ASCII (`Şifre` becomes `sifreInput`). Rename them for the domain before handing off.
- Hand the class to `testfly-web-write` when the user also wants actions or tests.

## Gotchas

- `Locator` does **not** skip hidden elements when resolving. A hidden mobile-menu `<button>Giriş</button>` makes `getByRole(BUTTON, "Giriş")` ambiguous even though the user sees one button. Uniqueness therefore counts hidden nodes, but only visible nodes are emitted unless you pass `--include-hidden`.
- Name matching is a case-insensitive **substring**: `getByRole(BUTTON, "Giriş")` also matches "Giriş yardımı". This is why `.exact()` is often the chosen variant.
- `getByLabel` matches the accessible name, which tries `aria-label` first and falls back to placeholder/value. On saucedemo `getByLabel("Username")` works through `aria-label`, not a `<label>`.
- A bare `<select>` gets the concatenated option text ("TürkçeEnglish") as its accessible name, so role candidates are suppressed for it and `findByName` wins.
- The driver reads `Locator`'s private `ACCESSIBLE_NAME_JS`, `FORM_CONTROL_CSS`, and `testIdAttribute`, plus `Role.values()`, through reflection, so it follows SDK changes automatically. The JS defaults only serve the browser-tool path. Update them if `Locator`/`Role` change.
- Generated ids (`:r3:`, `ember123`, `mat-input-0`, hashes, 4+ digit runs) are rejected as `findById`/class candidates.
- Only the landing URL is scanned. For dialogs, tabs, or post-login screens, use the browser-tool path after navigating there.

## Troubleshooting

- `WARNING: Unable to find an exact match for CDP version …`: harmless. Selenium is older than the local Chrome, and the scan does not use CDP.
- Progress lines (`compiling TestFly`, `resolving runtime classpath`) go to stderr; stdout is only the report.
- Very few elements on a SPA: increase `--wait 5000`. The scan runs once after the wait.
