import * as React from "react";
/** @startingPoint section="Components" subtitle="Confirm dialog with required reason" viewport="700x460" */
export interface ModalProps { open: boolean; onClose?: () => void; title: React.ReactNode; description?: React.ReactNode; tone?: "danger" | "warning" | "privacy" | "info"; icon?: string; children?: React.ReactNode; footer?: React.ReactNode; width?: number; contained?: boolean; role?: "dialog" | "alertdialog"; }
export declare function Modal(props: ModalProps): JSX.Element | null;
export interface ConfirmDialogProps { open: boolean; onClose: () => void; onConfirm: (reason: string) => void | Promise<void>; title: React.ReactNode; description?: React.ReactNode; tone?: "danger" | "warning" | "privacy" | "info"; confirmLabel?: string; cancelLabel?: string; requireReason?: boolean; reasonLabel?: string; reasonPlaceholder?: string; minReasonLength?: number; auditNote?: string | null; children?: React.ReactNode; contained?: boolean; }
export declare function ConfirmDialog(props: ConfirmDialogProps): JSX.Element | null;
