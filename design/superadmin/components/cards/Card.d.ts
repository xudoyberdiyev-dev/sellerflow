import * as React from "react";
export interface CardProps { title?: React.ReactNode; description?: React.ReactNode; actions?: React.ReactNode; footer?: React.ReactNode; children?: React.ReactNode; padded?: boolean; interactive?: boolean; onClick?: () => void; style?: React.CSSProperties; as?: string; }
export declare function Card(props: CardProps): JSX.Element;
