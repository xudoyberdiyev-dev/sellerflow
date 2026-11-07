import React from 'react';
import { Icon } from '../core/Icon.jsx';

/** Tiny inline trend line. data: number[] */
export function Sparkline({ data = [], width = 96, height = 32, color = 'var(--chart-1)', fill = true, label }) {
  if (data.length < 2) return null;
  const min = Math.min(...data), max = Math.max(...data), r = max - min || 1;
  const pts = data.map((v, i) => [(i / (data.length - 1)) * width, height - 2 - ((v - min) / r) * (height - 4)]);
  const d = pts.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true} style={{ flex: 'none', overflow: 'visible' }}>
      {fill && <path d={`${d} L${width} ${height} L0 ${height} Z`} fill={color} opacity="0.1" />}
      <path d={d} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}

/** KPI card: label, value, delta % (vs previous period) and sparkline. */
export function StatCard({ label, value, delta, deltaLabel = 'oldingi davrga nisbatan', trend, icon, loading, invertDelta }) {
  if (loading) {
    return (
      <div className="sf-card sf-stat" aria-busy="true">
        <span className="sf-skel" style={{ width: 110, height: 14 }} />
        <span className="sf-skel" style={{ width: 140, height: 28 }} />
        <span className="sf-skel" style={{ width: 180, height: 12 }} />
      </div>
    );
  }
  const dir = delta == null ? null : delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat';
  const good = invertDelta ? dir === 'down' : dir === 'up';
  const tone = dir === 'flat' ? 'flat' : good ? 'up' : 'down';
  const pct = delta == null ? '' : (delta > 0 ? '+' : delta < 0 ? '−' : '') + Math.abs(delta).toFixed(1).replace('.', ',') + '%';
  return (
    <div className="sf-card sf-stat">
      <div className="sf-stat__label">{icon && <Icon name={icon} size={14} />}{label}</div>
      <div className="sf-stat__row">
        <div className="sf-stat__value">{value}</div>
        {trend && <Sparkline data={trend} color={tone === 'down' ? 'var(--danger-icon)' : 'var(--chart-1)'} />}
      </div>
      {dir && (
        <div className="sf-stat__meta">
          <span className={`sf-stat__delta sf-stat__delta--${tone}`}>
            <Icon name={dir === 'up' ? 'arrow-up-right' : dir === 'down' ? 'arrow-down-right' : 'minus'} size={12} />{pct}
          </span>
          <span>{deltaLabel}</span>
        </div>
      )}
    </div>
  );
}
