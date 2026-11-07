import React, { useState, useMemo } from 'react';
import { Icon } from '../core/Icon.jsx';
import { Button, IconButton } from '../buttons/Button.jsx';
import { Checkbox } from '../choices/Choice.jsx';
import { SegmentedControl } from '../choices/SegmentedControl.jsx';
import { DropdownMenu } from '../overlays/Menu.jsx';
import { Skeleton } from '../feedback/Skeleton.jsx';
import { EmptyState } from '../feedback/EmptyState.jsx';

/**
 * Data table: sort, column picker, row selection, sticky header, pagination, density, row action menu.
 * columns: [{key, header, sortable?, align?, render?(row), width?, hideable? (default true), defaultHidden?}]
 */
export function DataTable({
  columns = [], rows = [], rowKey = 'id', selectable = true, pageSize = 10, maxHeight = 520,
  density: densityProp, onDensityChange, rowActions, onRowClick, loading, error, onRetry, emptyTitle = 'Ma’lumot yo‘q', emptyDescription,
  toolbar, bulkActions, caption,
}) {
  const [sort, setSort] = useState({ key: null, dir: 'asc' });
  const [hidden, setHidden] = useState(() => new Set(columns.filter((c) => c.defaultHidden).map((c) => c.key)));
  const [selected, setSelected] = useState(new Set());
  const [page, setPage] = useState(0);
  const [densityState, setDensityState] = useState('comfortable');
  const density = densityProp || densityState;
  const setDensity = (d) => { setDensityState(d); onDensityChange && onDensityChange(d); };

  const visible = columns.filter((c) => !hidden.has(c.key));
  const sorted = useMemo(() => {
    if (!sort.key) return rows;
    const s = [...rows].sort((a, b) => {
      const x = a[sort.key], y = b[sort.key];
      return typeof x === 'number' ? x - y : String(x).localeCompare(String(y), 'uz');
    });
    return sort.dir === 'desc' ? s.reverse() : s;
  }, [rows, sort]);
  const pages = Math.max(1, Math.ceil(sorted.length / pageSize));
  const pageRows = sorted.slice(page * pageSize, page * pageSize + pageSize);
  const allOnPage = pageRows.length > 0 && pageRows.every((r) => selected.has(r[rowKey]));
  const someOnPage = pageRows.some((r) => selected.has(r[rowKey]));

  const toggleSort = (k) => setSort((s) => (s.key !== k ? { key: k, dir: 'asc' } : s.dir === 'asc' ? { key: k, dir: 'desc' } : { key: null, dir: 'asc' }));
  const toggleRow = (k) => setSelected((s) => { const n = new Set(s); n.has(k) ? n.delete(k) : n.add(k); return n; });
  const togglePage = () => setSelected((s) => { const n = new Set(s); pageRows.forEach((r) => (allOnPage ? n.delete(r[rowKey]) : n.add(r[rowKey]))); return n; });

  const colItems = columns.filter((c) => c.hideable !== false).map((c) => ({
    label: c.header, icon: hidden.has(c.key) ? 'square' : 'square-check', keepOpen: true,
    onSelect: () => setHidden((h) => { const n = new Set(h); n.has(c.key) ? n.delete(c.key) : n.add(c.key); return n; }),
  }));

  const colCount = visible.length + (selectable ? 1 : 0) + (rowActions ? 1 : 0);
  let body;
  if (loading) {
    body = Array.from({ length: Math.min(pageSize, 6) }, (_, i) => (
      <tr key={i}>{selectable && <td className="sf-table__check"><Skeleton width={16} height={16} /></td>}
        {visible.map((c, j) => <td key={c.key}><Skeleton width={j === 0 ? 160 : 80} /></td>)}{rowActions && <td />}</tr>
    ));
  } else if (error) {
    body = <tr><td colSpan={colCount} style={{ height: 'auto' }}><EmptyState kind="error" title="Ma’lumotni yuklab bo‘lmadi" description={error} actions={onRetry && <Button icon="rotate-cw" onClick={onRetry}>Qayta urinish</Button>} /></td></tr>;
  } else if (rows.length === 0) {
    body = <tr><td colSpan={colCount} style={{ height: 'auto' }}><EmptyState title={emptyTitle} description={emptyDescription} /></td></tr>;
  } else {
    body = pageRows.map((r) => {
      const k = r[rowKey]; const sel = selected.has(k);
      return (
        <tr key={k} aria-selected={selectable ? sel : undefined} data-clickable={!!onRowClick || undefined}
          onClick={onRowClick ? (e) => { if (!e.target.closest('button,input,label,a')) onRowClick(r); } : undefined}>
          {selectable && <td className="sf-table__check"><Checkbox checked={sel} onChange={() => toggleRow(k)} aria-label="Qatorni tanlash" /></td>}
          {visible.map((c) => <td key={c.key} data-align={c.align}>{c.render ? c.render(r) : r[c.key]}</td>)}
          {rowActions && <td className="sf-table__actions"><DropdownMenu align="end" items={rowActions(r)} trigger={(p) => <IconButton size="sm" icon="ellipsis" label="Qator amallari" {...p} />} /></td>}
        </tr>
      );
    });
  }

  return (
    <div className="sf-table-wrap" data-density={density === 'compact' ? 'compact' : undefined}>
      {selected.size > 0 ? (
        <div className="sf-table-toolbar sf-table-toolbar--selected">
          <span>{selected.size} ta tanlandi</span>
          <Button size="sm" variant="ghost" onClick={() => setSelected(new Set())}>Tanlovni bekor qilish</Button>
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>{bulkActions && bulkActions([...selected])}</div>
        </div>
      ) : (
        <div className="sf-table-toolbar">
          {toolbar}
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 8, alignItems: 'center' }}>
            <SegmentedControl size="sm" label="Zichlik" value={density} onChange={setDensity}
              options={[{ value: 'comfortable', label: 'Keng', icon: 'rows-3' }, { value: 'compact', label: 'Zich', icon: 'rows-4' }]} />
            <DropdownMenu align="end" label="Ustunlar" items={colItems} trigger={(p) => <Button size="sm" icon="columns-3" {...p}>Ustunlar</Button>} />
          </div>
        </div>
      )}
      <div className="sf-table-scroll" style={{ maxHeight }}>
        <table className="sf-table">
          {caption && <caption className="sf-sr-only">{caption}</caption>}
          <thead>
            <tr>
              {selectable && <th className="sf-table__check"><Checkbox checked={allOnPage} indeterminate={!allOnPage && someOnPage} onChange={togglePage} aria-label="Sahifadagi barcha qatorlarni tanlash" /></th>}
              {visible.map((c) => (
                <th key={c.key} data-align={c.align} style={{ width: c.width }} aria-sort={sort.key === c.key ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined}>
                  {c.sortable ? (
                    <button type="button" className="sf-table__sort" onClick={() => toggleSort(c.key)}>
                      {c.header}<Icon name={sort.key !== c.key ? 'chevrons-up-down' : sort.dir === 'asc' ? 'arrow-up' : 'arrow-down'} size={12} />
                    </button>
                  ) : c.header}
                </th>
              ))}
              {rowActions && <th className="sf-table__actions"><span className="sf-sr-only">Amallar</span></th>}
            </tr>
          </thead>
          <tbody>{body}</tbody>
        </table>
      </div>
      {!loading && !error && rows.length > 0 && (
        <div className="sf-table-foot">
          <span className="sf-num">{page * pageSize + 1}–{Math.min(sorted.length, (page + 1) * pageSize)} / {sorted.length}</span>
          <div className="sf-table-foot__pages">
            <IconButton size="sm" icon="chevron-left" label="Oldingi sahifa" disabled={page === 0} onClick={() => setPage(page - 1)} />
            <span className="sf-num" style={{ padding: '0 6px' }}>{page + 1} / {pages}</span>
            <IconButton size="sm" icon="chevron-right" label="Keyingi sahifa" disabled={page >= pages - 1} onClick={() => setPage(page + 1)} />
          </div>
        </div>
      )}
    </div>
  );
}
