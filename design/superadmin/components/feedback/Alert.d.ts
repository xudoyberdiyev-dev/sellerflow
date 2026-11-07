import * as React from "react";
export type AlertTone = "info" | "success" | "warning" | "danger" | "privacy";
export interface InlineAlertProps { tone?: AlertTone; title?: React.ReactNode; children?: React.ReactNode; actions?: React.ReactNode; onClose?: () => void; }
export declare function InlineAlert(props: InlineAlertProps): JSX.Element;
export interface BannerProps { tone?: Exclude<AlertTone, "privacy">; children: React.ReactNode; action?: React.ReactNode; onClose?: () => void; }
export declare function Banner(props: BannerProps): JSX.Element;
