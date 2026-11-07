Left navigation with grouped, collapsible sections; collapses to icon rail (60px) with tooltips.
```jsx
<Sidebar role="SUPPORT" active="sellers" onNavigate={go} collapsed={c} onToggleCollapsed={()=>setC(!c)}
  groups={[{label:'Asosiy',items:[{id:'dash',label:'Boshqaruv paneli',icon:'layout-dashboard'}]}]} />
```
- `roles` hides items the current role cannot open.
