# Yutori — Android app design brief (for Figma / AI design tools)

This is a designer brief, not an engineering spec. Pair with
[`ui-spec.md`](./ui-spec.md), which is the behavioral contract
(states, data queries, nav graph). This file owns visual language.

## Context

Yutori is a personal, single-user, SMS-driven spending tracker for
Indian users. All data on-device; the app reads bank SMSes to
auto-extract transactions. I am the only user, so the UI can afford
to be opinionated and data-dense rather than mass-market polite.

## Platform

- Android phones, portrait only, 360dp–411dp width
- Material 3, Jetpack Compose
- Both light and dark modes, **dark mode is the primary target**
  (most daily use)
- **No Android 12+ dynamic color.** We own the palette; user
  wallpaper should not leak into the app.
- Currency: INR default (₹), occasional USD/EUR/GBP/AED for foreign CC
  spend. Use en-IN number formatting (₹1,23,456.78).

## Visual personality — Copilot.money-inspired, modern fintech-minimal

Reference: https://copilot.money

Principles this translates to:

- **Near-black, not true black, in dark mode.** Surface like #0F0F10,
  elevated surface like #17171A. True #000 reads as a void on OLED
  and kills subtle elevation.
- **Grayscale dominates.** ~90% of the UI is neutrals. Color earns
  its place; it doesn't decorate.
- **One calm accent.** Pick a single accent (candidates: warm amber
  #F5B547-style, or muted sage/teal). Use it for: active state,
  primary CTA, a single emphasized metric. Not for section headers,
  not for icons, not for dividers.
- **Typography carries hierarchy, not color.** Oversized restrained
  numerals for amounts (think 32–48sp), small tight labels in
  uppercase or all-caps small for meta rows.
- **Subtle separators, not cards.** Hairline 1dp dividers at ~12%
  opacity instead of elevated cards everywhere. A card appears only
  when content genuinely needs containment (e.g., alert banners,
  source-SMS blocks).
- **Generous row height, tight horizontal padding.** List rows
  breathe vertically (56–72dp) but don't waste horizontal space.
- **Semantic colors are muted, not saturated.** Budget over 100% uses
  a desaturated red (not #FF0000). Refunds/income use a muted green.
  REFUND = green. INCOME = blue-teal. DROP = full gray (equivalent
  to "not counted"). SPEND = default text color (uncolored).
- **Numbers are the hero.** The dashboard is read-first; a user
  glancing at the screen for 1 second should know "how much have I
  spent, am I on track." All chrome defers to that.

### Palette (seed — the designer can refine)

Light mode:
- Surface: `#FAFAF7` (warm off-white, not clinical white)
- Surface elevated: `#FFFFFF`
- On-surface: `#15161A`
- On-surface muted: `#6B6F76`
- Divider: `rgba(21, 22, 26, 0.08)`
- Accent (pick one during design): warm amber `#C48919` or
  sage-teal `#2C7A6E`
- Semantic — over-budget: `#B7433A`, refund/positive: `#3E7E4F`,
  income: `#3B6E9A`

Dark mode:
- Surface: `#0F0F10`
- Surface elevated: `#17171A`
- On-surface: `#EDEEF0`
- On-surface muted: `#8A8E96`
- Divider: `rgba(237, 238, 240, 0.08)`
- Accent: warmer/brighter variant of light-mode accent
- Semantic — over-budget: `#E06C61`, refund/positive: `#6FB07E`,
  income: `#7BA9D1`

## First-pass scope

Not all 13 screens. The 5 screens I look at every day:

1. **Onboarding** (welcome + permission explainer + budget prompt,
   collapsed to the essential flow — skip the import step for v1
   mockup since it has lots of states)
2. **Dashboard (home)** — the most important screen in the app.
   Include states: no budget set, normal (with budget), over budget,
   pending-FX banner visible.
3. **Category drill-down** — list of transactions in one category.
4. **Transaction detail** — read-only.
5. **Settings hub** — list-of-rows page.

Everything else (card drill-down, budget setup, accounts editor,
recipient rules, CSV export, notifications, etc.) is scope for a
later pass once we agree on the language.

## What each screen should show (unchanged from before, scoped to the 5)

1. **Onboarding** — 3 steps: welcome, permission explainer
   (RECEIVE_SMS, READ_SMS, POST_NOTIFICATIONS), budget prompt.
2. **Dashboard (home)** — month header with Import / Edit budget /
   ⚙ Settings actions; budget gauge showing spent vs. effective
   budget; stat row (carry-over ±₹, days left, daily burn);
   pending-FX banner (conditional); alert banner (conditional);
   breakdown of spend by category (ring chart or stacked bar —
   designer's call); horizontal card/account chip strip. Handles
   "no budget set," "no transactions," "over budget" states.
3. **Category drill-down** — transactions list for one category,
   month. Infobox at top for CASH / UNCATEGORIZED / OTHER
   (explaining what's in the bucket).
4. **Transaction detail** — hero amount, merchant, date/time,
   classification pill with budget-effect color (SPEND neutral,
   REFUND green, INCOME blue, DROP muted), forex "original + rate"
   block if applicable, expandable source SMSes (monospace raw
   body, sender, role tag, PRIMARY flag), metadata rows.
5. **Settings hub** — list of: My accounts, Recipient rules, Alert
   thresholds, Import past SMS, Rerun parser, Purge non-financial
   SMSes, Export CSV, Permissions, About.

## Classifications and enum glossary

Classification (13): CC_TRANSACTION, UPI_PAYMENT, DEBIT_CARD,
ATM_WITHDRAWAL (SPEND); REFUND; INCOMING_CREDIT (INCOME);
CC_BILL_PAYMENT, CASHBACK, SELF_TRANSFER, OTP, BALANCE_ALERT,
NON_FINANCIAL, UNMATCHED (DROP).

Category (11): Food & Dining, Groceries, Travel & Transport,
Shopping, Bills & Utilities, Health, Entertainment, UPI Transfer,
Cash, Uncategorized, Other.

## Design system deliverables

- Color tokens: light + dark. No dynamic-color variants.
- Typography scale: Material 3 defaults adapted — numerals at
  display/headline sizes, JetBrains Mono (or similar) for raw SMS
  bodies
- Icon set: 11 category icons, bank/issuer logos placeholder
  treatment, system icons. Prefer outlined over filled for
  secondary chrome; filled only for active state.
- Component library: budget gauge (with 3 threshold colorings),
  progress strip, category breakdown component, card chip,
  transaction row, classification pill, section header, infobox,
  input fields, filter chip, source-SMS block
- 8dp spacing grid
- Empty / error / loading templates

## Hard constraints

- No animations for communicating state — color alone shouldn't
  carry meaning (accessibility). Pair color with a text label or
  icon wherever it means something.
- Dynamic type must not break layouts (show at 100%, 130%, 200%
  text scale).
- Touch targets ≥ 48dp.
- Raw SMS bodies displayed verbatim in monospace — never
  paraphrased.
- Currency symbol position: ₹1,234 not 1,234 ₹.
- Masked account numbers shown as ••0000 (two bullets + digits).

## Design principles

- Budget-neutral events (CC bill payments, self-transfers, bill
  generation notices) must be visually distinguishable from spend
  in any list view.
- Pending forex is a first-class visible state, not an error.
- The dashboard is read-first: user glances and knows spend vs.
  budget. Actions (import, budget, settings) are secondary.

## Reference data (for realistic mockups)

Dataset shape: ~220 SMSes/month from Indian banks (Kotak, Axis,
ICICI, HDFC, SBI). Amounts typically ₹100–₹50,000, a handful of
₹1 lakh+ transactions. Senders are DLT headers like VK-KOTAKB-S,
JD-ICICIT-S. Typical merchants: Zomato, Swiggy, Uber, Ola, Amazon,
Flipkart, Blinkit, Vijay Sales, Apollo, Netflix, GitHub, Claude,
Airtel, Jio.

## Deliver

A Figma file with:
- 1 frame per screen × state × theme (light/dark) for the 5 scoped
  screens — ~30 frames total, not 80
- A components page (the component library above)
- A design-tokens page (the palette + typography above)

Label every state clearly. Keep it shippable — no aspirational
decoration that contradicts the "data-dense, read-first, dark-
primary, Copilot-inspired" brief.
