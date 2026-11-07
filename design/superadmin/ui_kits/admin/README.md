# SellerFlow Admin — UI kit

Click-through recreation of the internal admin panel, built only from `components/`.

Screens: Login → 2FA (OTP; `000000` shows the error) · Boshqaruv paneli · Sotuvchilar (table, quick-view drawer) · Sotuvchi profili (tabs, PrivacyGuard → request modal with Telegram preview → simulated consent → ReadOnlyBanner + countdown → re-lock) · Kirish so‘rovlari · Audit jurnali · Adminlar (2FA reset / block / delete with required reason; 403 for SUPPORT).

Profile menu (top right) switches role and theme. ⌘K / Ctrl+K opens the command palette. The bottom «Prototip holati» switch previews loading, empty and error states.

Files: `data.js` (mock data), `Shell.jsx` (AppShell, PageHeader, NAV), `Login.jsx`, `Dashboard.jsx`, `Sellers.jsx`, `SellerDetail.jsx`, `AccessRequests.jsx`, `Admins.jsx` (Admins + Audit), `App.jsx` (state & routing).
