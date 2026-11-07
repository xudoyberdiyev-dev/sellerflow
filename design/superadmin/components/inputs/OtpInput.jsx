import React, { useRef } from 'react';

/** 6-cell one-time code input. Auto-advances, Backspace steps back, paste fills all cells. */
export function OtpInput({ length = 6, value = '', onChange, onComplete, invalid, disabled, autoFocus, label = 'Tasdiqlash kodi' }) {
  const refs = useRef([]);
  const chars = Array.from({ length }, (_, i) => value[i] || '');

  const set = (next) => {
    const v = next.slice(0, length);
    onChange && onChange(v);
    if (v.length === length && !v.includes(' ')) onComplete && onComplete(v);
  };
  const focus = (i) => refs.current[Math.max(0, Math.min(length - 1, i))]?.focus();

  const onInput = (i, e) => {
    const d = e.target.value.replace(/\D/g, '');
    if (!d) return;
    if (d.length > 1) { set((value.slice(0, i) + d).slice(0, length)); focus(i + d.length); return; }
    const arr = chars.slice(); arr[i] = d; set(arr.join('').trimEnd()); focus(i + 1);
  };
  const onKey = (i, e) => {
    if (e.key === 'Backspace') {
      e.preventDefault();
      const arr = chars.slice();
      if (arr[i]) { arr[i] = ''; } else if (i > 0) { arr[i - 1] = ''; focus(i - 1); }
      set(arr.join(''));
    } else if (e.key === 'ArrowLeft') focus(i - 1);
    else if (e.key === 'ArrowRight') focus(i + 1);
  };
  const onPaste = (e) => {
    e.preventDefault();
    const d = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length);
    if (d) { set(d); focus(d.length); }
  };

  const half = Math.floor(length / 2);
  return (
    <div className="sf-otp" role="group" aria-label={label} data-invalid={invalid || undefined} onPaste={onPaste}>
      {chars.map((c, i) => (
        <React.Fragment key={i}>
          {length === 6 && i === half && <span className="sf-otp__sep" aria-hidden="true" />}
          <input ref={(el) => (refs.current[i] = el)} className="sf-otp__cell" inputMode="numeric" autoComplete={i === 0 ? 'one-time-code' : 'off'}
            maxLength={length} value={c} disabled={disabled} autoFocus={autoFocus && i === 0}
            aria-label={`${label}, ${i + 1}-raqam`} aria-invalid={invalid || undefined}
            onChange={(e) => onInput(i, e)} onKeyDown={(e) => onKey(i, e)} onFocus={(e) => e.target.select()} />
        </React.Fragment>
      ))}
    </div>
  );
}
