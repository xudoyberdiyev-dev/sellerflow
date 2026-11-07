import * as React from "react";
/** @startingPoint section="Privacy" subtitle="Sticky read-only consent session banner" viewport="900x80" */
export interface ReadOnlyBannerProps { sellerName?: string; expiresAt?: Date | number; seconds?: number; onEnd?: () => void; onExpire?: () => void; warnAt?: number; }
export declare function ReadOnlyBanner(props: ReadOnlyBannerProps): JSX.Element;
