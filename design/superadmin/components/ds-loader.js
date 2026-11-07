/* Dev fallback: if the compiled _ds_bundle.js is not present, fetch component sources, transpile with Babel and expose window.SellerFlowAdmin. */
(function () {
  var base = (document.currentScript && document.currentScript.src || '').replace(/[^/]*$/, '');
  var ORDER = ['core/format.js', 'core/Icon.jsx', 'core/Logo.jsx', 'buttons/Button.jsx', 'feedback/Skeleton.jsx', 'feedback/EmptyState.jsx',
    'choices/Choice.jsx', 'choices/SegmentedControl.jsx', 'display/Tooltip.jsx', 'overlays/Menu.jsx', 'inputs/Input.jsx', 'inputs/Select.jsx',
    'inputs/Combobox.jsx', 'inputs/OtpInput.jsx', 'choices/DateRangePicker.jsx', 'display/Badge.jsx', 'display/Avatar.jsx', 'cards/Card.jsx',
    'cards/StatCard.jsx', 'feedback/Alert.jsx', 'feedback/Toast.jsx', 'navigation/Tabs.jsx', 'navigation/Sidebar.jsx', 'navigation/Topbar.jsx',
    'navigation/CommandPalette.jsx', 'table/DataTable.jsx', 'overlays/Modal.jsx', 'overlays/Drawer.jsx', 'privacy/CountdownChip.jsx',
    'privacy/PrivacyGuard.jsx', 'privacy/ReadOnlyBanner.jsx', 'privacy/TelegramPreview.jsx'];

  function findBundle() {
    if (window.SellerFlowAdmin && window.SellerFlowAdmin.Button) return window.SellerFlowAdmin;
    for (var k of Object.keys(window)) { try { var v = window[k]; if (v && typeof v === 'object' && v.Button && v.DataTable) return v; } catch (e) {} }
    return null;
  }

  function transform(src) {
    var names = [];
    src = src.replace(/^import\s+React(?:\s*,\s*\{([^}]*)\})?\s+from\s+['"]react['"];?/m, function (_, b) { return b ? 'const {' + b + '} = React;' : ''; });
    src = src.replace(/^import[^\n]*\n/gm, '');
    src = src.replace(/^export\s*\{([^}]*)\};?/gm, function (_, b) {
      return b.split(',').map(function (p) { var m = p.trim().split(/\s+as\s+/); names.push([m[0], m[1] || m[0]]); return ''; }).join('');
    });
    src = src.replace(/^export\s+(function|const|let|class)\s+([A-Za-z_$][\w$]*)/gm, function (_, kw, n) { names.push([n, n]); return kw + ' ' + n; });
    return { src: src, names: names };
  }

  function evalInto(NS, texts, labels) {
    texts.forEach(function (t, i) {
      var tr = transform(t);
      var known = Object.keys(NS).filter(function (k) { return /^[A-Za-z_$][\w$]*$/.test(k); });
      var code = '(function(NS, React){ const {' + known.join(',') + '} = NS;\n' + tr.src + '\n' +
        'Object.assign(NS, {' + tr.names.map(function (n) { return n[1] + ':' + n[0]; }).join(',') + '}); })';
      var out = Babel.transform(code, { presets: ['react'], filename: labels[i] }).code;
      (0, eval)(out)(NS, window.React);
    });
    return NS;
  }

  /** Load extra JSX modules (e.g. UI-kit screens) that use `export` and reference DS components by bare name. */
  window.loadSellerFlowModules = function (urls) {
    return window.loadSellerFlowDS().then(function (DS) {
      var NS = Object.assign({}, DS);
      return Promise.all(urls.map(function (u) { return fetch(u).then(function (r) { return r.text(); }); }))
        .then(function (texts) { return evalInto(NS, texts, urls); });
    });
  };

  var promise;
  window.loadSellerFlowDS = function () {
    if (promise) return promise;
    var existing = findBundle();
    if (existing) return (promise = Promise.resolve(existing));
    promise = new Promise(function (res) {
      var s = document.createElement('script');
      s.src = base + '../_ds' + '_bundle.js';
      s.onload = function () { res(findBundle()); };
      s.onerror = function () { res(null); };
      document.head.appendChild(s);
    }).then(function (b) { return b || fromSource(); });
    return promise;
  };
  function fromSource() {
    return Promise.all(ORDER.map(function (p) { return fetch(base + p).then(function (r) { return r.text(); }); })).then(function (texts) {
      var NS = evalInto({}, texts, ORDER);
      window.SellerFlowAdmin = NS;
      return NS;
    });
  }
})();
