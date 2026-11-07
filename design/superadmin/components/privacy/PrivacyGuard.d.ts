import * as React from "react";
/** @startingPoint section="Privacy" subtitle="Hidden commercial data guard" viewport="700x260" */
export interface PrivacyGuardProps { state?: "locked" | "pending" | "denied" | "expired"; title?: React.ReactNode; description?: React.ReactNode; fields?: string[]; onAction?: () => void; actionLabel?: string; canRequest?: boolean; inline?: boolean; loading?: boolean; }
export declare function PrivacyGuard(props: PrivacyGuardProps): JSX.Element;
export declare function Redacted(props: { label?: string }): JSX.Element;
