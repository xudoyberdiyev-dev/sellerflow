import * as React from "react";
export interface DateRangeValue { preset: "today" | "7d" | "30d" | "custom"; from: Date; to: Date; }
export interface DateRangePickerProps { value?: DateRangeValue; onChange?: (v: DateRangeValue) => void; today?: Date; }
export declare function DateRangePicker(props: DateRangePickerProps): JSX.Element;
export declare function rangeForPreset(preset: string, today?: Date): { from: Date; to: Date } | null;
