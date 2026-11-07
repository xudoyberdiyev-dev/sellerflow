KPI card: label, tabular value, ±% delta vs previous period, sparkline.
```jsx
<StatCard label="Faol sotuvchilar" value="1 284" delta={12.4} trend={[3,5,4,6,8,7,9]} />
<StatCard loading />
```
- Delta uses comma decimals «+12,4%». `invertDelta` when a decrease is good (e.g. blocked accounts).
- Platform-level aggregates only — never a single seller's commercial numbers.
