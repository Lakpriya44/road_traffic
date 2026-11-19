// Live end-to-end test: real Java server + 3 junction clients must already be running.
// Feeds the real SSE stream to the dashboard logic, submits requests through the dashboard's
// own submitReq() (real HTTP POST), and records what the dashboard shows.
const http = require('http'), fs = require('fs');
const { load } = require('./dashboard_harness.js');
const { sg, ctx, els } = load();
const BASE = 'http://127.0.0.1:8082/';
ctx.fetch = (u, o) => fetch(BASE + u, o);
const RUN_S = +process.argv[2] || 80, out = { samples: [], notes: [], reqs: [] };
http.get(BASE + 'events', res => { let buf = ''; res.setEncoding('utf8');
  res.on('data', c => { buf += c; let i; while ((i = buf.indexOf('\n\n')) >= 0) { const blk = buf.slice(0, i); buf = buf.slice(i + 2);
    const l = blk.split('\n').find(x => x.startsWith('data: ')); if (l) sg.handleEvent(JSON.parse(l.slice(6))); } }); });
const set = (id, v) => { els[id] = els[id] || {}; els[id].value = v; };
const t0 = Date.now(); let last = Date.now();
const timer = setInterval(() => { const n = Date.now(); sg.step(Math.min(0.1, (n - last) / 1000)); last = n;
  const t = n + (0); // dashboard uses its own offset-corrected clock
  const row = { t: n / 1000 }; for (const j of ['J01', 'J02', 'J03']) { const p = sg.phaseOf(j, Date.now() + 0); row[j] = [p.mode, p.dir || '', p.ped ? 1 : 0]; }
  out.samples.push(row); }, 100);
async function submit(kind, vals, label) {
  Object.entries(vals).forEach(([k, v]) => set(k, v));
  await ctx.submitReq(kind);
  const msg = els[kind === 'pedestrian' ? 'pedMsg' : 'emgMsg'];
  out.notes.push({ label, at: Date.now() / 1000, message: msg.textContent, cls: msg._a.class || msg.className });
}
(async () => {
  await new Promise(r => setTimeout(r, 12000));
  await submit('pedestrian', { pedJ: 'J01', pedD: 'NORTH', pedC: '3' }, 'valid ped J01 NORTH x3');
  await submit('emergency', { emgJ: 'J02', emgD: 'EAST', emgV: 'AMBULANCE' }, 'valid emergency J02 EAST AMBULANCE');
  await submit('pedestrian', { pedJ: 'J01', pedD: 'NORTH', pedC: '0' }, 'invalid count 0 (client-side validation)');
  await submit('pedestrian', { pedJ: 'J09', pedD: 'NORTH', pedC: '2' }, 'invalid junction J09 (server rejects)');
  await submit('emergency', { emgJ: 'J03', emgD: 'WEST', emgV: 'TANK' }, 'invalid vehicle (server rejects)');
  const stat = () => sg.myReqs.map(r => r.kind + ':' + r.junction + ':' + sg.reqStatus(r, Date.now() + 0));
  const seen = {}; const poll = setInterval(() => stat().forEach((s, i) => { seen[i] = seen[i] || new Set(); seen[i].add(s.split(':')[2]); }), 200);
  await new Promise(r => setTimeout(r, (RUN_S - 14) * 1000)); clearInterval(poll); clearInterval(timer);
  out.reqStatusSeen = Object.fromEntries(Object.entries(seen).map(([k, v]) => [sg.myReqs[k].kind + ':' + sg.myReqs[k].junction, [...v]]));
  out.counts = sg.counts; out.logs = sg.getLogs().filter(l => l.kind === 'ack').slice(0, 10).map(l => (l.err ? 'ERR ' : 'OK  ') + l.text);
  out.decisionHtmlSample = sg.decisionHtml(Date.now()).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').slice(0, 400);
  out.vehicles = sg.vehicles.length; fs.writeFileSync('/tmp/t/live_out.json', JSON.stringify(out)); process.exit(0);
})();
