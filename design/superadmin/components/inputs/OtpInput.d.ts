import * as React from "react";
/** @startingPoint section="Components" subtitle="6-cell OTP for 2FA login" viewport="700x200" */
export interface OtpInputProps { length?: number; value?: string; onChange?: (v: string) => void; onComplete?: (v: string) => void; invalid?: boolean; disabled?: boolean; autoFocus?: boolean; label?: string; }
export declare function OtpInput(props: OtpInputProps): JSX.Element;
