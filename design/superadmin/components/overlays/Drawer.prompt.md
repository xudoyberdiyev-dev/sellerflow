Right-side 480px panel for record details while keeping the list in context.
```jsx
<Drawer open={!!seller} onClose={close} title={seller.name} subtitle={<StatusBadge status="Faol" />}>…</Drawer>
```
- Use `.sf-dl` for label/value lists inside.
