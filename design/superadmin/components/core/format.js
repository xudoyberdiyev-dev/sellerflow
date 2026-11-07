/** Uzbek-locale formatters used across SellerFlow Admin. Timezone: Asia/Tashkent. */
const TZ = 'Asia/Tashkent';
const group = (n) => String(Math.trunc(Math.abs(n))).replace(/\B(?=(\d{3})+(?!\d))/g, '\u00A0');

export function formatSom(n) { return (n < 0 ? '−' : '') + group(n) + '\u00A0so‘m'; }
export function formatNumber(n) { return (n < 0 ? '−' : '') + group(n); }
export function formatPercent(n, digits = 2) { return n.toFixed(digits).replace('.', ',') + '%'; }
function parts(d) {
  const p = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(new Date(d));
  const g = (t) => p.find((x) => x.type === t).value;
  return { dd: g('day'), mm: g('month'), yyyy: g('year'), HH: g('hour'), min: g('minute') };
}
export function formatDate(d) { const p = parts(d); return `${p.dd}.${p.mm}.${p.yyyy}`; }
export function formatTime(d) { const p = parts(d); return `${p.HH}:${p.min}`; }
export function formatDateTime(d) { return `${formatDate(d)} ${formatTime(d)}`; }
export function formatDuration(sec) {
  sec = Math.max(0, Math.floor(sec));
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  const pad = (x) => String(x).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}
