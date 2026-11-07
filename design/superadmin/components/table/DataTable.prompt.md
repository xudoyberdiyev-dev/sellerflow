Primary list view: sortable columns, column picker, row selection with bulk bar, sticky header, pagination, Keng/Zich density (44/36px rows), row «…» menu.
```jsx
<DataTable caption="Sotuvchilar" columns={[{key:'name',header:'Sotuvchi',sortable:true},{key:'tg',header:'Telegram ID',render:r=><span className="sf-id">{r.tg}</span>}]}
  rows={rows} rowActions={r=>[{label:'Ochish',icon:'panel-right-open'},{separator:true},{label:'Bloklash',icon:'ban',danger:true}]}
  onRowClick={openDrawer} toolbar={<Input icon="search" size="sm" placeholder="Qidirish" />} />
```
- Built-in loading (skeleton rows), empty and error states. Numbers right-aligned.
- Never add columns for commercial data (tannarx, foyda, narx).
