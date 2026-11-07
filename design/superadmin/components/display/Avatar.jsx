import React from 'react';

const TONES = [
  ['var(--primary-subtle)', 'var(--primary-subtle-fg)'],
  ['var(--info-bg)', 'var(--info-text)'],
  ['var(--orange-bg)', 'var(--orange-text)'],
  ['var(--privacy-bg-strong)', 'var(--privacy-text)'],
  ['var(--neutral-bg)', 'var(--neutral-text)'],
  ['var(--warning-bg)', 'var(--warning-text)'],
];
const initials = (name = '') => name.trim().split(/\s+/).slice(0, 2).map((w) => w[0]).join('').toUpperCase() || '?';
const hash = (s) => [...s].reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 7);

/** Letter avatar. Tone is stable per name. size: 24 | 32 | 40 */
export function Avatar({ name, size = 32 }) {
  const [bg, fg] = TONES[hash(name || '') % TONES.length];
  return (
    <span className="sf-avatar" role="img" aria-label={name} style={{ width: size, height: size, background: bg, color: fg, fontSize: size <= 24 ? 10 : size <= 32 ? 12 : 14, boxShadow: 'inset 0 0 0 1px color-mix(in oklab, currentColor 14%, transparent)' }}>
      {initials(name)}
    </span>
  );
}
