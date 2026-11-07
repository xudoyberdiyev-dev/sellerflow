Text inputs wrapped by Field (label, hint, error with cause + fix).
```jsx
<Field label="Telegram ID" error="ID faqat raqamlardan iborat bo‘lishi kerak. Masalan: 482019377">
  <Input icon="at-sign" placeholder="482019377" />
</Field>
<Field label="Izoh" optional><Textarea maxLength={500} /></Field>
```
- Field injects id/aria-describedby/invalid into its child. Error text replaces hint.
