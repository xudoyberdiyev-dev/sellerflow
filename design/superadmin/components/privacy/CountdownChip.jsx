import React, { useEffect, useState } from 'react';
import { Icon } from '../core/Icon.jsx';

const fmt = (sec) => {
  sec = Math.max(0, Math.floor(sec));
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  const p = (x) => String(x).padStart(2, '0');
  return h > 0 ? `${h}:${p(m)}:${p(s)}` : `${p(m)}:${p(s)}`;
};

/** Live remaining-time hook. expiresAt: Date | ms timestamp. */
export function useCountdown(expiresAt) {
  const target = expiresAt == null ? null : +new Date(expiresAt);
  const [now, setNow] = useState(Date.now());
  useEffect(() => { if (target == null) return; const t = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(t); }, [target]);
  return target == null ? null : Math.max(0, (target - now) / 1000);
}

/**
 * Access-window countdown: «23:41 qoldi». Turns warning in the last `warnAt` seconds (default 5 min).
 * Pass expiresAt for a live timer, or `seconds` for a static render.
 */
export function CountdownChip({ expiresAt, seconds, warnAt = 300, onExpire }) {
  const live = useCountdown(expiresAt);
  const left = seconds != null ? seconds : live ?? 0;
  const expired = left <= 0;
  const urgent = !expired && left <= warnAt;
  useEffect(() => { if (expired && expiresAt != null && onExpire) onExpire(); }, [expired]);
  return (
    <span className="sf-countdown" data-urgent={urgent || undefined} data-expired={expired || undefined} role="timer" aria-live={urgent ? 'polite' : 'off'}
      aria-label={expired ? 'Kirish muddati tugadi' : `Kirish muddati: ${fmt(left)} qoldi`}>
      <Icon name={expired ? 'timer-off' : urgent ? 'alarm-clock' : 'timer'} size={12} />
      {expired ? 'Muddati tugadi' : <><b>{fmt(left)}</b> qoldi</>}
    </span>
  );
}
export { fmt as formatCountdown };
