import * as React from "react";
export interface FieldProps { label?: React.ReactNode; optional?: boolean; hint?: React.ReactNode; error?: React.ReactNode; id?: string;
  children: React.ReactElement | ((a: { id: string; describedBy?: string; invalid: boolean }) => React.ReactNode); }
export declare function Field(props: FieldProps): JSX.Element;
export interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "size"> { icon?: string; suffix?: React.ReactNode; size?: "sm" | "md"; invalid?: boolean; }
export declare function Input(props: InputProps): JSX.Element;
export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> { invalid?: boolean; }
export declare function Textarea(props: TextareaProps): JSX.Element;
