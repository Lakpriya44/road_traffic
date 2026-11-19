// Loads web/dashboard.html's scripts in Node with a minimal DOM stub so the real
// dashboard logic (event handling, phase model, vehicle simulation) can be tested headlessly.
const fs = require('fs'), vm = require('vm'), path = require('path');
function load() {
  const html = fs.readFileSync(path.join(__dirname, '..', 'web', 'dashboard.html'), 'utf8');
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]).join('\n');
  const els = {};
  function mk() {
    const e = { style: {}, children: [], _a: {}, textContent: '', value: '', _ih: '',
      
      setAttribute(k, v) { this._a[k] = v; }, getAttribute(k) { return this._a[k]; },
      appendChild(c) { this.children.push(c); c.parentNode = this; return c; },
      removeChild(c) { const i = this.children.indexOf(c); if (i >= 0) this.children.splice(i, 1); c.parentNode = null; },
      addEventListener() {}, querySelectorAll() { return []; }, closest() { return null; },
      get innerHTML() { return this._ih; }, set innerHTML(v) { this._ih = v; this.children = []; } };
    e.classList = { toggle(c, on) { const set = new Set((e._a.class || '').split(' ').filter(Boolean)); (on === undefined ? !set.has(c) : on) ? set.add(c) : set.delete(c); e._a.class = [...set].join(' '); },
      add(c) { this.toggle(c, true); }, remove(c) { this.toggle(c, false); } };
    e.querySelector = sel => { const id = sel.replace('#', ''); const f = n => { for (const c of n.children) { if (c._a && c._a.id === id) return c; const r = f(c); if (r) return r; } return null; }; return f(e) || mk(); };
    return e;
  }
  const document = { getElementById: id => els[id] || (els[id] = mk()), createElementNS: (ns, tag) => { const e = mk(); e.tag = tag; return e; },
    createElement: () => mk(), querySelectorAll: () => [] };
  const ctx = { document, console, Math, Date, JSON, Set, Object, Number, Array, String, Promise, setTimeout, fetch: () => { throw new Error('no fetch'); } };
  ctx.window = ctx; ctx.__SG_TEST = true;
  vm.createContext(ctx);
  vm.runInContext(scripts, ctx, { filename: 'dashboard.html' });
  return { sg: ctx.__sg, ctx, els };
}
function ser(e) {  // serialize a stub element tree to SVG text (used by tests/render_snapshot.js)
  if (e.tag === undefined) return '';
  const a = Object.entries(e._a).map(([k, v]) => ` ${k}="${String(v).replace(/&/g,'&amp;').replace(/"/g,'&quot;')}"`).join('');
  const st = Object.entries(e.style).filter(([k]) => k !== 'opacity' || true).map(([k, v]) => `${k.replace(/[A-Z]/g, m => '-' + m.toLowerCase())}:${v}`).join(';');
  const txt = String(e.textContent || '').replace(/&/g, '&amp;').replace(/</g, '&lt;');
  return `<${e.tag}${a}${st ? ` style="${st}"` : ''}>${txt}${e.children.map(ser).join('')}</${e.tag}>`;
}
module.exports = { load, ser };
