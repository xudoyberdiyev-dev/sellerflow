import React, { useEffect, useRef, useState, useId } from 'react';
import { Icon } from '../core/Icon.jsx';
import { Button, IconButton } from '../buttons/Button.jsx';
import { Field, Textarea } from '../inputs/Input.jsx';

function useDialog(open, onClose, ref) {
  useEffect(() => {
    if (!open) return;
    const prev = document.activeElement;
    const t = setTimeout(() => {
      const el = ref.current?.querySelector('[data-autofocus], textarea, input, button:not([aria-label="Yopish"])');
      el && el.focus();
    }, 0);
    const k = (e) => {
      if (e.key === 'Escape') { e.stopPropagation(); onClose && onClose(); }
      if (e.key === 'Tab' && ref.current) {
        const f = [...ref.current.querySelectorAll('button:not([disabled]), [href], input:not([disabled]), textarea:not([disabled]), select, [tabindex]:not([tabindex="-1"])')];
        if (!f.length) return;
        const first = f[0], last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener('keydown', k);
    return () => { clearTimeout(t); document.removeEventListener('keydown', k); prev && prev.focus && prev.focus(); };
  }, [open]);
}

/** Centered dialog. tone sets the header icon chip. */
export function Modal({ open, onClose, title, description, tone, icon, children, footer, width = 480, contained, role = 'dialog' }) {
  const ref = useRef(null);
  const tid = useId();
  useDialog(open, onClose, ref);
  if (!open) return null;
  const ic = icon || { danger: 'triangle-alert', warning: 'triangle-alert', privacy: 'lock', info: 'info' }[tone];
  return (
    <div className={`sf-overlay ${contained ? 'sf-overlay--contained' : ''}`} onMouseDown={(e) => e.target === e.currentTarget && onClose && onClose()}>
      <div ref={ref} className="sf-modal" role={role} aria-modal="true" aria-labelledby={tid} style={{ '--sf-modal-w': width + 'px' }}>
        <div className="sf-modal__head">
          {ic && <div className={`sf-modal__icon sf-modal__icon--${tone || 'info'}`}><Icon name={ic} size={18} /></div>}
          <div style={{ flex: 1, minWidth: 0 }}>
            <h2 className="sf-modal__title" id={tid}>{title}</h2>
            {description && <p className="sf-modal__desc">{description}</p>}
          </div>
          <IconButton size="sm" icon="x" label="Yopish" onClick={onClose} style={{ margin: '-4px -8px 0 0' }} />
        </div>
        {children && <div className="sf-modal__body">{children}</div>}
        {footer && <div className="sf-modal__foot" style={children ? undefined : { paddingTop: 20 }}>{footer}</div>}
      </div>
    </div>
  );
}

/**
 * Confirmation dialog. requireReason=true makes the reason textarea mandatory (dangerous actions).
 * onConfirm(reason) may return a Promise — the button shows loading until it settles.
 */
export function ConfirmDialog({
  open, onClose, onConfirm, title, description, tone = 'danger', confirmLabel = 'Tasdiqlash', cancelLabel = 'Bekor qilish',
  requireReason = false, reasonLabel = 'Sabab', reasonPlaceholder = 'Nima uchun bu amal bajarilayotganini yozing', minReasonLength = 10,
  auditNote = 'Amal auditga yoziladi', children, contained,
}) {
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [loading, setLoading] = useState(false);
  useEffect(() => { if (open) { setReason(''); setTouched(false); setLoading(false); } }, [open]);
  const tooShort = requireReason && reason.trim().length < minReasonLength;
  const submit = async () => {
    setTouched(true);
    if (tooShort) return;
    setLoading(true);
    try { await (onConfirm && onConfirm(reason.trim())); } finally { setLoading(false); }
  };
  return (
    <Modal open={open} onClose={loading ? undefined : onClose} title={title} description={description} tone={tone} contained={contained} role="alertdialog"
      footer={<>
        {auditNote && <span className="sf-modal__audit"><Icon name="scroll-text" size={14} />{auditNote}</span>}
        <Button variant="secondary" onClick={onClose} disabled={loading}>{cancelLabel}</Button>
        <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={submit} loading={loading}>{confirmLabel}</Button>
      </>}>
      {children}
      {requireReason && (
        <Field label={reasonLabel} hint={`Kamida ${minReasonLength} ta belgi. Sabab audit jurnalida saqlanadi.`}
          error={touched && tooShort ? `Sababni yozing — kamida ${minReasonLength} ta belgi kerak.` : null}>
          <Textarea value={reason} onChange={(e) => setReason(e.target.value)} onBlur={() => setTouched(true)} placeholder={reasonPlaceholder} maxLength={500} required aria-required="true" data-autofocus />
        </Field>
      )}
    </Modal>
  );
}
