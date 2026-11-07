import React, { useState, useRef, useEffect } from 'react';
import { Icon } from '../core/Icon.jsx';
import { SegmentedControl } from './SegmentedControl.jsx';
import { Button, IconButton } from '../buttons/Button.jsx';

const MONTHS = ['Yanvar', 'Fevral', 'Mart', 'Aprel', 'May', 'Iyun', 'Iyul', 'Avgust', 'Sentabr', 'Oktabr', 'Noyabr', 'Dekabr'];
const DOW = ['Du', 'Se', 'Ch', 'Pa', 'Ju', 'Sh', 'Ya'];
const day0 = (d) => { const x = new Date(d); x.setHours(0, 0, 0, 0); return x; };
const fmt = (d) => `${String(d.getDate()).padStart(2, '0')}.${String(d.getMonth() + 1).padStart(2, '0')}.${d.getFullYear()}`;
const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };

export function rangeForPreset(preset, today = new Date()) {
  const t = day0(today);
  if (preset === 'today') return { from: t, to: t };
  if (preset === '7d') return { from: addDays(t, -6), to: t };
  if (preset === '30d') return { from: addDays(t, -29), to: t };
  return null;
}

/** Presets (Bugun / 7 kun / 30 kun) + custom range calendar. value: {preset, from, to} */
export function DateRangePicker({ value, onChange, today = new Date() }) {
  const v = value || { preset: '7d', ...rangeForPreset('7d', today) };
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState({ from: v.from, to: v.to });
  const [month, setMonth] = useState(() => { const d = day0(v.to || today); d.setDate(1); return d; });
  const ref = useRef(null);
  const t0 = day0(today);

  useEffect(() => {
    if (!open) return;
    const h = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const k = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', h); document.addEventListener('keydown', k);
    return () => { document.removeEventListener('mousedown', h); document.removeEventListener('keydown', k); };
  }, [open]);

  const onPreset = (p) => {
    if (p === 'custom') { setDraft({ from: v.from, to: v.to }); setOpen(true); return; }
    onChange && onChange({ preset: p, ...rangeForPreset(p, today) });
  };
  const clickDay = (d) => {
    if (!draft.from || (draft.from && draft.to)) setDraft({ from: d, to: null });
    else if (d < draft.from) setDraft({ from: d, to: draft.from });
    else setDraft({ from: draft.from, to: d });
  };

  const first = new Date(month); const offset = (first.getDay() + 6) % 7;
  const daysIn = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const cells = [...Array(offset).fill(null), ...Array.from({ length: daysIn }, (_, i) => new Date(month.getFullYear(), month.getMonth(), i + 1))];

  return (
    <div ref={ref} style={{ position: 'relative', display: 'inline-flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
      <SegmentedControl label="Davr" value={v.preset} onChange={onPreset}
        options={[{ value: 'today', label: 'Bugun' }, { value: '7d', label: '7 kun' }, { value: '30d', label: '30 kun' }, { value: 'custom', label: 'Oraliq', icon: 'calendar' }]} />
      {v.from && v.to && <span className="sf-num" style={{ font: 'var(--type-small)', color: 'var(--text-secondary)' }}>{fmt(v.from)} – {fmt(v.to)}</span>}
      {open && (
        <div className="sf-pop" role="dialog" aria-label="Sana oralig‘ini tanlash" style={{ top: 'calc(100% + 6px)', left: 0, padding: 12, width: 260 }}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
            <IconButton size="sm" icon="chevron-left" label="Oldingi oy" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))} />
            <div style={{ flex: 1, textAlign: 'center', font: 'var(--type-body-strong)' }}>{MONTHS[month.getMonth()]} {month.getFullYear()}</div>
            <IconButton size="sm" icon="chevron-right" label="Keyingi oy" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))} />
          </div>
          <div className="sf-cal" role="grid">
            {DOW.map((d) => <div key={d} className="sf-cal__dow">{d}</div>)}
            {cells.map((d, i) => {
              if (!d) return <span key={'e' + i} />;
              const future = d > t0;
              const edge = (draft.from && +d === +draft.from) || (draft.to && +d === +draft.to);
              const inR = draft.from && draft.to && d > draft.from && d < draft.to;
              return <button key={i} type="button" className="sf-cal__day" disabled={future} data-muted={future || undefined}
                data-edge={edge || undefined} data-in={inR || undefined} data-today={+d === +t0 || undefined}
                aria-label={fmt(d)} aria-pressed={!!edge} onClick={() => clickDay(d)}>{d.getDate()}</button>;
            })}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border-default)' }}>
            <span className="sf-num" style={{ font: 'var(--type-small)', color: 'var(--text-secondary)', flex: 1 }}>
              {draft.from ? fmt(draft.from) : '—'} – {draft.to ? fmt(draft.to) : '—'}
            </span>
            <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>Bekor qilish</Button>
            <Button size="sm" variant="primary" disabled={!draft.from || !draft.to} onClick={() => { onChange && onChange({ preset: 'custom', ...draft }); setOpen(false); }}>Qo‘llash</Button>
          </div>
        </div>
      )}
    </div>
  );
}
