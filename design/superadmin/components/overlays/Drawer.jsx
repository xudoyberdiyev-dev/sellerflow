import React, { useEffect, useRef, useId } from 'react';
import { IconButton } from '../buttons/Button.jsx';

/** Right-side panel for record details without leaving the list. */
export function Drawer({ open, onClose, title, subtitle, actions, children, footer, width, contained }) {
  const ref = useRef(null);
  const tid = useId();
  useEffect(() => {
    if (!open) return;
    const prev = document.activeElement;
    setTimeout(() => ref.current?.focus(), 0);
    const k = (e) => e.key === 'Escape' && onClose && onClose();
    document.addEventListener('keydown', k);
    return () => { document.removeEventListener('keydown', k); prev && prev.focus && prev.focus(); };
  }, [open]);
  if (!open) return null;
  return (
    <div className={`sf-drawer-overlay ${contained ? 'sf-drawer-overlay--contained' : ''}`} onMouseDown={(e) => e.target === e.currentTarget && onClose && onClose()}>
      <aside ref={ref} tabIndex={-1} className="sf-drawer" role="dialog" aria-modal="true" aria-labelledby={tid} style={width ? { width: `min(${width}px, 100%)` } : undefined}>
        <div className="sf-drawer__head">
          <div style={{ flex: 1, minWidth: 0 }}>
            <h2 className="sf-drawer__title" id={tid}>{title}</h2>
            {subtitle && <div className="sf-drawer__sub">{subtitle}</div>}
          </div>
          {actions}
          <IconButton icon="x" label="Yopish" onClick={onClose} />
        </div>
        <div className="sf-drawer__body">{children}</div>
        {footer && <div className="sf-drawer__foot">{footer}</div>}
      </aside>
    </div>
  );
}
