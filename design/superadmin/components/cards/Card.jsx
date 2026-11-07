import React from 'react';

/** Flat surface with 1px border (no shadow). Optional header with title/description/actions and footer. */
export function Card({ title, description, actions, footer, children, padded = true, interactive, onClick, style, as = 'section' }) {
  const Tag = as;
  return (
    <Tag className={`sf-card ${interactive ? 'sf-card--interactive' : ''}`} onClick={onClick} style={style}>
      {(title || actions) && (
        <div className="sf-card__head">
          <div style={{ minWidth: 0 }}>
            {title && <h3 className="sf-card__title">{title}</h3>}
            {description && <p className="sf-card__desc">{description}</p>}
          </div>
          {actions && <div className="sf-card__actions">{actions}</div>}
        </div>
      )}
      {padded ? <div className="sf-card__body">{children}</div> : children}
      {footer && <div className="sf-card__foot">{footer}</div>}
    </Tag>
  );
}
