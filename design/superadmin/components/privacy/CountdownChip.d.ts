import * as React from "react";
export interface CountdownChipProps { expiresAt?: Date | number; seconds?: number; warnAt?: number; onExpire?: () => void; }
export declare function CountdownChip(props: CountdownChipProps): JSX.Element;
export declare function useCountdown(expiresAt?: Date | number): number | null;
export declare function formatCountdown(sec: number): string;
