import React from 'react';
import { Icon } from '../core/Icon.jsx';
import { Button } from '../buttons/Button.jsx';
import { CountdownChip, useCountdown } from './CountdownChip.jsx';

/**
 * Sticky read-only session banner: «Faqat ko‘rish · auditga yozilmoqda · 23:41 qoldi».
 * Place at the top of the scroll container showing consented seller data.
 */
export function ReadOnlyBanner({ sellerName, expiresAt, seconds, onEnd, onExpire, warnAt = 300 }) {
  const live = useCountdown(expiresAt);
  const left = seconds != null ? seconds : live ?? 0;
  const urgent = left > 0 && left <= warnAt;
  return (
    <div className="sf-readonly" role="status" data-urgent={urgent || undefined}>
      <Icon name="eye" size={16} />
      <div className="sf-readonly__items">
        <strong>Faqat ko‘rish</strong>
        <span className="sf-readonly__dot" aria-hidden="true" />
        <span className="sf-readonly__rec">Auditga yozilmoqda</span>
        {sellerName && <><span className="sf-readonly__dot" aria-hidden="true" /><span>Sotuvchi roziligi: {sellerName}</span></>}
      </div>
      <CountdownChip expiresAt={expiresAt} seconds={seconds} warnAt={warnAt} onExpire={onExpire} />
      {onEnd && <Button size="sm" variant="ghost" icon="log-out" onClick={onEnd}>Ko‘rishni yakunlash</Button>}
    </div>
  );
}
