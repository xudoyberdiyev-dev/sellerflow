Checkbox (multi), Radio (one-of) and Switch (instant on/off setting).
```jsx
<Checkbox checked={a} onChange={e=>setA(e.target.checked)} label="Faqat faol sotuvchilar" />
<Radio name="d" value="30" label="30 daqiqa" />
<Switch checked={on} onChange={...} label="2FA majburiy" description="Barcha adminlar uchun" />
```
- Switch applies immediately; use Checkbox inside forms that need «Saqlash».
