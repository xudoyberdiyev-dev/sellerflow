import React, { useEffect, useState } from 'react';

const ICON_BASE = 'https://cdn.jsdelivr.net/npm/lucide-static@0.460.0/icons/';
const cache = {};
function load(name) {
  if (!cache[name]) {
    cache[name] = fetch(ICON_BASE + name + '.svg').then((r) => (r.ok ? r.text() : '')).then((t) =>
      t.replace(/<!--[\s\S]*?-->/g, '').replace(/<svg[^>]*>/, (m) => m.replace(/\s(width|height)="[^"]*"/g, '').replace('<svg', '<svg aria-hidden="true" focusable="false"'))
    ).catch(() => '');
  }
  return cache[name];
}

/** Lucide icon (lucide-static, CDN) inlined as SVG, stroke = currentColor. `name` is the kebab-case Lucide name. */
export function Icon({ name, size = 16, label, className = '', style }) {
  const [svg, setSvg] = useState(() => (cache[name] && cache[name].value) || '');
  useEffect(() => {
    let live = true;
    load(name).then((t) => { cache[name].value = t; if (live) setSvg(t); });
    return () => { live = false; };
  }, [name]);
  return (
    <span
      className={'sf-icon ' + className}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      style={{ width: size, height: size, ...style }}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
