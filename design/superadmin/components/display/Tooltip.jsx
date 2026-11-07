import React, { useState, useId } from 'react';

/** Keyboard key glyph. */
export function Kbd({ children }) { return <kbd className="sf-kbd">{children}</kbd>; }

/** Hover/focus tooltip. Short text only; never hide essential info here. */
export function Tooltip({ content, shortcut, placement = 'top', children, defaultOpen = false }) {
  const [open, setOpen] = useState(defaultOpen);
  const id = useId();
  const child = React.isValidElement(children) ? React.cloneElement(children, { 'aria-describedby': open ? id : undefined }) : children;
  return (
    <span className="sf-tooltip-wrap" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(defaultOpen)}
      onFocus={() => setOpen(true)} onBlur={() => setOpen(defaultOpen)} onKeyDown={(e) => e.key === 'Escape' && setOpen(false)}>
      {child}
      {open && <span role="tooltip" id={id} className={`sf-tooltip ${placement === 'bottom' ? 'sf-tooltip--bottom' : ''}`}>
        {content}{shortcut && <Kbd>{shortcut}</Kbd>}
      </span>}
    </span>
  );
}
