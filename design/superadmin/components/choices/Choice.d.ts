import * as React from "react";
export interface ChoiceProps extends React.InputHTMLAttributes<HTMLInputElement> { label?: React.ReactNode; description?: React.ReactNode; invalid?: boolean; indeterminate?: boolean; }
export declare function Checkbox(props: ChoiceProps): JSX.Element;
export declare function Radio(props: ChoiceProps): JSX.Element;
export declare function Switch(props: ChoiceProps): JSX.Element;
