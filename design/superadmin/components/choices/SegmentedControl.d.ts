import * as React from "react";
export interface SegmentOption { value: string; label: React.ReactNode; icon?: string; disabled?: boolean; }
export interface SegmentedControlProps { options: SegmentOption[]; value: string; onChange?: (v: string) => void; label?: string; size?: "sm" | "md"; }
export declare function SegmentedControl(props: SegmentedControlProps): JSX.Element;
