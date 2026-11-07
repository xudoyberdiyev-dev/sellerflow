Modal for focused decisions; ConfirmDialog for confirmations — `requireReason` for dangerous actions (bloklash, admin o‘chirish, 2FA reset).
```jsx
<ConfirmDialog open={o} onClose={close} tone="danger" requireReason title="Sotuvchini bloklash"
  description="Dilnoza Karimova botdan foydalana olmaydi." confirmLabel="Bloklash" onConfirm={async (reason)=>{...}} />
```
- Focus trap, Esc closes, returns focus. Confirm label repeats the verb from the title.
