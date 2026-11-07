import React from 'react';
import { Icon } from '../core/Icon.jsx';
import { Button } from '../buttons/Button.jsx';
import { Badge } from '../display/Badge.jsx';

const STATES = {
  locked: { title: 'Tijoriy ma’lumot yashirin — sotuvchi roziligi kerak', text: 'Tannarx, foyda, narxlar va hisoblashlar sukut bo‘yicha ko‘rinmaydi. Ko‘rish uchun sotuvchiga Telegram bot orqali so‘rov yuboring. Ruxsat 30–60 daqiqaga, faqat o‘qish uchun beriladi.', action: 'Kirish so‘rash', icon: 'lock' },
  pending: { title: 'So‘rov yuborildi — sotuvchi javobi kutilmoqda', text: 'Sotuvchi Telegram botda «Ruxsat berish» tugmasini bossa, ma’lumot shu yerda faqat o‘qish rejimida ochiladi.', action: 'So‘rovni bekor qilish', icon: 'hourglass' },
  denied: { title: 'Sotuvchi so‘rovni rad etdi', text: 'Tijoriy ma’lumot yashirin qoladi. Zarurat bo‘lsa, sababni aniqroq yozib, yangi so‘rov yuboring.', action: 'Qayta so‘rash', icon: 'lock' },
  expired: { title: 'Kirish muddati tugadi', text: 'Ma’lumot yana yashirildi. Davom etish uchun sotuvchidan yangi rozilik oling.', action: 'Yangi so‘rov yuborish', icon: 'lock' },
};

/** Masked value placeholder for a single protected field. */
export function Redacted({ label = 'Yashirin' }) {
  return <span className="sf-redacted" aria-label={`${label}: sotuvchi roziligi kerak`}><Icon name="lock" size={12} />{label}</span>;
}

/**
 * Privacy guard block. Replaces any commercial data (tannarx, foyda, narx, hisoblashlar) until the seller consents.
 * state: locked | pending | denied | expired. Hide the action for roles that cannot request (canRequest=false).
 */
export function PrivacyGuard({ state = 'locked', title, description, fields, onAction, actionLabel, canRequest = true, inline, loading }) {
  const s = STATES[state] || STATES.locked;
  const action = canRequest ? (onAction && (
    <Button variant={state === 'pending' ? 'secondary' : 'privacy'} icon={state === 'pending' ? 'x' : 'send'} onClick={onAction} loading={loading} size={inline ? 'sm' : 'md'}>
      {actionLabel || s.action}
    </Button>
  )) : <span style={{ font: 'var(--type-small)', color: 'var(--text-tertiary)' }}>So‘rov yuborish huquqi yo‘q</span>;
  return (
    <div className={`sf-guard ${inline ? 'sf-guard--inline' : ''}`} role="region" aria-label="Himoyalangan ma’lumot">
      <div className="sf-guard__icon"><Icon name={s.icon} size={18} /></div>
      <div className="sf-guard__body">
        <p className="sf-guard__title">{title || s.title}</p>
        {!inline && <p className="sf-guard__text">{description || s.text}</p>}
        {fields && !inline && <div className="sf-guard__fields">{fields.map((f) => <Redacted key={f} label={f} />)}</div>}
        {state === 'pending' && !inline && <div><Badge tone="info">Kutilmoqda</Badge></div>}
        {!inline && action && <div style={{ marginTop: 8 }}>{action}</div>}
      </div>
      {inline && action}
    </div>
  );
}
