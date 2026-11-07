Placeholder that replaces seller commercial data until consent: «Tijoriy ma’lumot yashirin — sotuvchi roziligi kerak» + request action.
```jsx
<PrivacyGuard fields={['Tannarx','Foyda','Narxlar','Hisoblashlar']} onAction={openRequestDialog} />
<PrivacyGuard state="pending" onAction={cancel} />
<Redacted label="Foyda" />   // single-cell mask in tables
```
- Default for every commercial field. Never blur real values — the data is not loaded at all.
