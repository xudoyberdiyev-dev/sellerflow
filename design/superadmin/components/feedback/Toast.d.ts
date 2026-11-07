import * as React from "react";
export interface ToastProps { tone?: "success" | "danger" | "info" | "warning"; title: React.ReactNode; description?: React.ReactNode; audit?: boolean | string; onClose?: () => void; }
export declare function Toast(props: ToastProps): JSX.Element;
export declare function ToastStack(props: { toasts: (ToastProps & { id: string })[]; onDismiss?: (id: string) => void }): JSX.Element;
export declare function useToasts(ttl?: number): { toasts: (ToastProps & { id: string })[]; push: (t: ToastProps) => void; dismiss: (id: string) => void };
