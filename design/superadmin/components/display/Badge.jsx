import React from 'react';
import { Icon } from '../core/Icon.jsx';

const STATUS = {
  'Faol': 'success', 'Ro‘yxatdan o‘tmoqda': 'info', 'Bloklangan': 'warning', 'O‘chirish so‘ralgan': 'orange', 'Yopilgan': 'neutral',
  'Kutilmoqda': 'info', 'Ruxsat berilgan': 'success', 'Rad etilgan': 'danger', 'Bekor qilingan': 'danger', 'Muddati tugagan': 'neutral',
};
export const STATUS_TONES = STATUS;

/** Small tonal label. Always carries text — color is never the only signal. */
export function Badge({ tone = 'neutral', dot = true, icon, children }) {
  return (
    <span className={`sf-badge sf-badge--${tone}`}>
      {icon ? <Icon name={icon} size={12} /> : dot ? <span className="sf-badge__dot" aria-hidden="true" /> : null}
      {children}
    </span>
  );
}

/** Account / access-request status. Tone is derived from the Uzbek status label. */
export function StatusBadge({ status }) {
  return <Badge tone={STATUS[status] || 'neutral'}>{status}</Badge>;
}

/** Neutral metadata tag (sales channel, filter chip). Optional color swatch and remove button. */
export function Tag({ children, color, onRemove, icon }) {
  return (
    <span className="sf-tag">
      {color && <span className="sf-tag__swatch" style={{ background: color }} aria-hidden="true" />}
      {icon && <Icon name={icon} size={12} />}
      {children}
      {onRemove && <button type="button" className="sf-tag__x" aria-label={`${children} — olib tashlash`} onClick={onRemove}><Icon name="x" size={12} /></button>}
    </span>
  );
}

export const CHANNELS = [
  { id: 'uzum', label: 'Uzum Market', color: 'var(--chart-1)' },
  { id: 'wb', label: 'Wildberries', color: 'var(--chart-2)' },
  { id: 'ozon', label: 'Ozon', color: 'var(--chart-3)' },
  { id: 'instagram', label: 'Instagram', color: 'var(--chart-4)' },
  { id: 'telegram', label: 'Telegram savdo', color: 'var(--chart-5)' },
  { id: 'offline', label: 'Offline do‘kon', color: 'var(--chart-6)' },
  { id: 'other', label: 'Boshqa', color: 'var(--chart-7)' },
];

export function ChannelTag({ channel }) {
  const c = CHANNELS.find((x) => x.id === channel || x.label === channel) || CHANNELS[6];
  return <Tag color={c.color}>{c.label}</Tag>;
}
