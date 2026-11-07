# SellerFlow Admin — Design System

Web superadmin panel for **SellerFlow**, a Telegram-bot SaaS that helps marketplace sellers in Uzbekistan (Uzum Market, Wildberries, Ozon, Instagram, Telegram savdo, offline do‘kon) calculate per-unit profit and the right selling price.

This system is for the **internal admin panel only** — used by the platform owner and staff, never by sellers.

- **Roles:** `SUPERADMIN` (everything) and `SUPPORT` (statistics, seller list, own access requests).
- **Devices:** desktop first (1280–1600px), must reflow to tablet and mobile.
- **Level:** corporate B2B SaaS. Calm, precise, bank-grade trust with startup speed.
- **Tariff:** only «Free (Beta)» for now.
- **Sources:** built from the written brief only. No codebase, Figma or brand files were provided; the logo was drawn from the brief's description (square, 6px radius, shield mark).

## The core principle: privacy by default

Admins **do not see** a seller's commercial data (tannarx, foyda, narxlar, hisoblashlar) by default. It only opens when the seller explicitly consents in the Telegram bot, for **30–60 minutes**, **read-only**, and **every view is written to the audit log**.

The UI makes that boundary visible everywhere:
1. A dedicated **privacy colour** (violet-grey, `--privacy-*`) is used for nothing else.
2. Commercial fields render as `Redacted` («Yashirin» + lock), never blurred real values. The data is not loaded.
3. `PrivacyGuard` replaces whole sections: «Tijoriy ma’lumot yashirin — sotuvchi roziligi kerak» + «Kirish so‘rash».
4. During consent, a sticky `ReadOnlyBanner` reads «Faqat ko‘rish · Auditga yozilmoqda · 23:41 qoldi»; edit controls are hidden.
5. `CountdownChip` turns amber in the last 5 minutes; on zero the data re-locks and a toast confirms it.
6. Dashboards and search show only platform aggregates, never one seller's numbers.

---

## CONTENT FUNDAMENTALS

**Language:** Uzbek, Latin script, with correct `o‘ g‘` (U+2018 ‘) and tutuq belgisi `’` (U+2019, «ma’lumot»). Never ASCII `'` or backtick. English only for technical terms: 2FA, ID, API, CSV, SUPERADMIN, SUPPORT.

**Tone:** formal-friendly, short, exact. Address the admin with the polite «siz» implicitly (imperative forms: «kiriting», «tanlang»). No exclamation marks, no jokes, no emoji.

**Buttons start with a verb**, in infinitive/noun form: «Saqlash», «Bloklash», «Kirish so‘rash», «Admin qo‘shish», «Qayta urinish», «Bekor qilish». The confirm button repeats the verb from the dialog title (title «Sotuvchini bloklash» → button «Bloklash»).

**Toasts are past tense results:** «Sotuvchi bloklandi», «So‘rov yuborildi», «2FA qayta o‘rnatildi». Logged actions add «Auditga yozildi».

**Errors = cause + fix**, one or two sentences:
- «Kod noto‘g‘ri. Autentifikator ilovasidagi yangi kodni kiriting.»
- «Server javob bermadi (504). Birozdan keyin qayta urinib ko‘ring.»
- «Sababni yozing — kamida 10 ta belgi kerak.»

**Empty states explain what will appear and how:** «Hali sotuvchi yo‘q» / «Sotuvchilar botda /start bosgach shu yerda paydo bo‘ladi.»

**Casing:** sentence case everywhere. Uppercase only for 12px sidebar group labels and role names (SUPERADMIN).

**Formats** (`components/core/format.js`):
- Date `DD.MM.YYYY`, time `HH:mm` 24h, timezone Asia/Tashkent: «05.11.2026 14:32».
- Money with a non-breaking space between groups: «120 000 so‘m». Negative: «−2 400 so‘m».
- Percent with a comma: «14,86%». Deltas: «+12,4%» / «−3,2%».
- Telegram ID, request IDs and tokens in JetBrains Mono: `482019377`, `req_7f3a9c21`.
- Phone masked: «+998 90 *** ** 17».

**Status vocabulary (fixed):**
- Akkaunt: Faol, Ro‘yxatdan o‘tmoqda, Bloklangan, O‘chirish so‘ralgan, Yopilgan.
- Kirish so‘rovi: Kutilmoqda, Ruxsat berilgan, Rad etilgan, Bekor qilingan, Muddati tugagan.

---

## VISUAL FOUNDATIONS

**Character.** Quiet, dense, trustworthy. No gradients, illustrations, photography, decorative patterns or textures. The only "texture" is the diagonal stripe inside `Redacted`, which marks hidden data.

**Colour.** Warm-neutral **stone** scale for surfaces, borders and text. **Brand green** `#18734B` (`--brand-600`, `--primary`) is reserved for the primary action, active navigation and chart series 1. Semantic tones (success, warning, danger, info, orange, neutral) each have bg / border / text / icon tokens. **Privacy** violet-grey for protected data only. Light on `:root`, dark on `[data-theme="dark"]`, same semantic names. The live contrast table is in `guidelines/contrast.html`.

**Chart palette.** Seven channel colours derived from Okabe–Ito (colour-blind safe): Uzum = brand green, Wildberries = blue, Ozon = amber, Instagram = pink, Telegram = sky, Offline = vermillion, Boshqa = stone. Always pair with a legend label; never colour alone.

**Type.** Inter 400/500/600, scale 12/13/14/16/20/24/30 (14 = body). Line heights 16/18/20/24/28/32/36. Headings 600 with −0.011em tracking at 20px and above. `font-variant-numeric: tabular-nums` is set globally. JetBrains Mono 400/500 for IDs, codes, tokens, OTP cells and countdown digits.

**Spacing.** 4px base: 4, 8, 12, 16, 20, 24, 32, 40, 48. Page padding 24px, card padding 16px, modal padding 20px.

**Radius.** 4 badge/tag/kbd · 6 inputs & buttons · 8 cards, tables, popovers · 12 modals & command palette. Avatars are round.

**Borders & elevation.** Cards, tables and the sidebar are **flat with a 1px `--border-default`**. Only two shadows exist: `--shadow-popover` (dropdowns, popovers, toasts, combobox) and `--shadow-modal` (modal, drawer, command palette). No inner shadows.

**Backgrounds.** App `--stone-50`, surfaces white; dark mode stone-950/900. The overlay is a 48% stone-950 scrim, no blur. Transparency is used only for scrims and on the READ-ONLY banner chip.

**States.**
- Hover: one step darker surface (`--bg-hover`) for neutral controls; primary goes 600 → 700, active 800. No opacity fades, no scaling.
- Press: one more step (`--bg-active` / brand-800). No shrink.
- Focus: `:focus-visible` ring = 2px surface gap + 2px brand-500 (`--focus-ring`); inputs use a 3px soft brand halo plus a brand border.
- Disabled: subtle background, `--text-disabled`, default border, `not-allowed` cursor.
- Loading: spinner replaces content inside the button without changing width; page content uses `Skeleton` blocks shaped like the final layout.
- Error: danger border + inline message with icon below the field.

**Motion.** Fast and functional: 120ms colour transitions, 180ms `cubic-bezier(0.2,0,0,1)` for overlays (fade + 8px slide/translate). No bounce. Skeleton shimmer 1.4s. `prefers-reduced-motion` disables all.

**Layout.** Fixed 248px sidebar (60px collapsed), 56px topbar, content max-width 1360px. The ReadOnly banner is sticky at the top of the scroll area. Drawer 480px from the right. Tables: sticky header, 44px rows (Comfortable) or 36px (Compact via `data-density="compact"`).

**Status badges** always contain text plus a dot, so colour is never the only carrier of meaning.

### When to use modal, drawer or page
- **Modal:** a single decision or a short form that blocks the flow — confirmations, «Kirish so‘rash», adding an admin. Max one modal at a time.
- **Drawer:** look at a record without losing the list — seller quick view from the table, request details. Read-mostly; actions in the footer.
- **Page:** anything with tabs, history or deep content, or anything worth a URL — seller profile, audit log, settings.

### Dangerous actions rule
Bloklash, admin o‘chirish, 2FA reset, blokdan chiqarish:
1. Red `danger` button (or menu item after a separator, at the bottom).
2. Always opens `ConfirmDialog` with `requireReason` (minimum 10 characters, stored in the audit log).
3. Description states the consequence and whether it is reversible.
4. Footer shows «Amal auditga yoziladi».
5. The result is confirmed with a toast with `audit`, including the reason.
6. SUPPORT never sees these actions (hidden, not disabled).

### Privacy pattern
Commercial data is **never** visible by default, in any role. Request → seller consents in Telegram (`TelegramPreview` shows exactly what they will see) → read-only session with banner and countdown → automatic re-lock on expiry. Tables show «Yashirin» cells. Search, export and dashboards never include commercial values.

### Required states per screen
Every screen draws: **loading** (skeleton), **empty**, **error** (with «Qayta urinish») and **403** (`EmptyState kind="forbidden"`). The UI kit has a «Prototip holati» switcher to preview them.

---

## ICONOGRAPHY

- **Set:** [Lucide](https://lucide.dev), outline, 2px stroke, round caps, loaded from the CDN as `lucide-static@0.460.0` and inlined as SVG by `<Icon name="…">` (stroke = `currentColor`). No icon font, no PNGs.
- **Sizes:** 12 (badges, chips), 14 (small buttons, meta), 16 (default), 18–20 (modal and empty-state chips).
- **Colour:** inherits text colour. Secondary text colour in menus, brand on the active nav item, `--privacy-icon` for lock/eye.
- **Key glyphs:** `lock` = hidden data, `eye` = read-only viewing, `timer` / `alarm-clock` = access window, `scroll-text` = audit, `shield-check` = 2FA on, `ban` = block, `key-round` = access requests, `send` = Telegram request.
- **No emoji** in the panel. Unicode is only used for keyboard glyphs in `Kbd` (⌘ ↵ ↑ ↓).
- **Icon-only buttons** must have `label` (aria-label + title).
- **Logo:** `assets/logo-full.svg` and `assets/logo-mark.svg`; the `Logo` component renders the same mark inline. The shield path is Lucide's `shield-check`. Replace with official artwork when it exists.

---

## Index

- `styles.css` — entry point (imports only).
- `tokens/` — `colors.css` (light + dark), `typography.css`, `spacing.css` (spacing, radius, shadows, density, z-index, motion), `fonts.css` (Google Fonts), `base.css`.
- `components/components.css` — all component styles (`sf-` prefix).
- `components/` — React primitives, each with `.d.ts` and `.prompt.md`, plus one preview card per folder:
  - `core/` Icon, Logo, LogoMark, format helpers (`formatSom`, `formatPercent`, `formatDate`, `formatDateTime`, `formatDuration`)
  - `buttons/` Button, IconButton
  - `inputs/` Field, Input, Textarea, Select, Combobox, OtpInput
  - `choices/` Checkbox, Radio, Switch, SegmentedControl, DateRangePicker
  - `display/` Badge, StatusBadge, Tag, ChannelTag, Avatar, Tooltip, Kbd
  - `cards/` Card, StatCard, Sparkline
  - `feedback/` Skeleton, SkeletonText, EmptyState, InlineAlert, Banner, Toast, ToastStack, useToasts
  - `table/` DataTable
  - `navigation/` Tabs, Breadcrumbs, Sidebar, Topbar, CommandPalette
  - `overlays/` Modal, ConfirmDialog, Drawer, Popover, DropdownMenu
  - `privacy/` CountdownChip, useCountdown, PrivacyGuard, Redacted, ReadOnlyBanner, TelegramPreview
  - `ds-loader.js` — dev fallback that loads the sources when the compiled bundle is absent.
- `guidelines/` — foundation specimen cards (colours, contrast, type, spacing, radius, elevation, density, focus, logo, icons).
- `ui_kits/admin/` — click-through admin panel: login + 2FA, dashboard, sellers (table + drawer), seller profile with the full consent flow, access requests, audit log, admins (403 for SUPPORT), ⌘K palette, dangerous-action dialogs, dark mode.
- `assets/` — logo SVGs.
- `SKILL.md` — agent skill manifest.

### Intentional additions
- `Icon` — wrapper for the Lucide set.
- `Field` — label/hint/error wrapper so every input gets correct a11y wiring.
- `Redacted` — single-cell version of the privacy guard for tables.
- `ChannelTag`, `StatusBadge` — fixed vocabularies mapped to the right colours.
