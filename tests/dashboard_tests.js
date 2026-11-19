// Headless tests of the dashboard logic. Run: node tests/dashboard_tests.js
const { load } = require('./dashboard_harness.js');
const fs = require('fs'), path = require('path');
let pass = 0, fail = 0;
function ok(name, cond, detail) { if (cond) { pass++; console.log('[PASS] ' + name); } else { fail++; console.log('[FAIL] ' + name + (detail ? '  -> ' + detail : '')); } }

function fresh() {
  const { sg } = load(); let now = 1_000_000;
  sg.setNow(() => now);
  const api = { sg, get now() { return now; }, set now(v) { now = v; },
    ev(t, d, extra) { sg.handleEvent(Object.assign({ t, d, ts: now }, extra || {})); },
    traffic(j, n, e, s, w) { // each arg: [l,s,r,q,wait]
      api.ev('traffic', `TRAFFIC|${j}|${now}|` + [['NORTH', n], ['EAST', e], ['SOUTH', s], ['WEST', w]].map(([d, a]) => d + ',' + a.join(',')).join('|')); },
    join(j) { api.ev('join', j); },
    cmd(j, phase, dir, dur, mode, veh) { api.ev('command', `SIGNAL_COMMAND|${j}|${phase}|${dir}|${dur}|${mode || 'NORMAL'}|${veh || 'NONE'}|${now}`); },
    run(seconds, hook) { const dt = 0.05, n = Math.round(seconds / dt); for (let i = 0; i < n; i++) { now += dt * 1000; sg.step(dt); if (hook) hook(); } } };
  return api;
}
const pos = (sg, v) => { if (v.phase === 'conn') { const q = sg.connPos(v.conn, v.cs); return [q[0], q[1]]; } const l = v.lane; return [l.a[0] + l.d[0] * v.s, l.a[1] + l.d[1] * v.s]; };

// ---------- 1. Geometry: left-hand traffic ----------
{
  const { sg } = fresh();
  let okE = true, okW = true, okS = true, okN = true;
  for (const j of ['J01', 'J02', 'J03']) {
    const jx = sg.XC[j];
    const w = sg.lanes[j + '-WEST'], e = sg.lanes[j + '-EAST'], n = sg.lanes[j + '-NORTH'], s = sg.lanes[j + '-SOUTH'];
    okE = okE && w.d[0] === 1 && w.a[1] === sg.CY - sg.LN && w.b[1] === sg.CY - sg.LN;     // eastbound = UPPER half
    okW = okW && e.d[0] === -1 && e.a[1] === sg.CY + sg.LN;                                // westbound = LOWER half
    okS = okS && n.d[1] === 1 && n.a[0] === jx + sg.LN;                                    // southbound = east half (driver's left)
    okN = okN && s.d[1] === -1 && s.a[0] === jx - sg.LN;                                   // northbound = west half (driver's left)
  }
  ok('Eastbound lanes are on the upper/northern half (y < centre line)', okE);
  ok('Westbound lanes are on the lower/southern half (y > centre line)', okW);
  ok('Southbound lanes use the east half (left of travel direction)', okS);
  ok('Northbound lanes use the west half (left of travel direction)', okN);
  const link = sg.lanes['J02-WEST'];
  ok('J01 east exit feeds J02 west approach (continuous main road)', sg.conn(sg.lanes['J01-WEST'], 'S').exit === link);
  ok('J03 west exit feeds J02 east approach (westbound continuous)', sg.conn(sg.lanes['J03-EAST'], 'S').exit === sg.lanes['J02-EAST']);
}

// ---------- 2. Turn paths: direction of each turn ----------
{
  const { sg } = fresh(); let allOk = true, notes = [];
  const exp = { NORTH: { L: 'E', S: 'S', R: 'W' }, EAST: { L: 'S', S: 'W', R: 'N' }, SOUTH: { L: 'W', S: 'N', R: 'E' }, WEST: { L: 'N', S: 'E', R: 'S' } };
  const outVec = { N: [0, -1], E: [1, 0], S: [0, 1], W: [-1, 0] };
  for (const j of ['J01', 'J02', 'J03']) for (const dir of ['NORTH', 'EAST', 'SOUTH', 'WEST']) for (const r of ['L', 'S', 'R']) {
    const l = sg.lanes[j + '-' + dir], c = sg.conn(l, r), ex = c.exit;
    const start = c.pts[0], end = c.pts[c.pts.length - 1];
    const cont = Math.hypot(start[0] - l.b[0], start[1] - l.b[1]) < 1e-6 && Math.hypot(end[0] - ex.a[0], end[1] - ex.a[1]) < 1e-6;
    const ov = outVec[exp[dir][r]];
    const headOk = ex.d[0] === ov[0] && ex.d[1] === ov[1];
    // turn sense by cross product of in/out headings (screen coords, y down): left turn => cross < 0
    const cross = l.d[0] * ex.d[1] - l.d[1] * ex.d[0];
    const senseOk = r === 'S' ? cross === 0 : (r === 'L' ? cross < 0 : cross > 0);
    // smoothness: max heading change between consecutive samples must be small
    let maxTurn = 0; for (let i = 2; i < c.pts.length; i++) { const a1 = Math.atan2(c.pts[i - 1][1] - c.pts[i - 2][1], c.pts[i - 1][0] - c.pts[i - 2][0]), a2 = Math.atan2(c.pts[i][1] - c.pts[i - 1][1], c.pts[i][0] - c.pts[i - 1][0]); let d = Math.abs(a2 - a1); if (d > Math.PI) d = 2 * Math.PI - d; maxTurn = Math.max(maxTurn, d); }
    if (!(cont && headOk && senseOk && maxTurn < 0.35)) { allOk = false; notes.push(`${j}-${dir}-${r} cont=${cont} head=${headOk} sense=${senseOk} turn=${maxTurn.toFixed(2)}`); }
  }
  ok('All 36 movements (3 junctions x 4 approaches x L/S/R): continuous, correct exit road, correct turn sense, smooth curve', allOk, notes.slice(0, 3).join('; '));
}

// ---------- 3. Phase model (mirrors JunctionClient timing) ----------
{
  const t = fresh(); const sg = t.sg; t.join('J01');
  t.cmd('J01', 'VEHICLE', 'NORTH', 10); const t0 = t.now;
  t.now = t0 + 500; let p = sg.phaseOf('J01', t.now);
  ok('first command with no previous green: all red during the 1 s clearance', p.mode === 'ALLRED');
  t.now = t0 + 2500; p = sg.phaseOf('J01', t.now);
  ok('green starts after 2 s and reports remaining time', p.mode === 'GREEN' && p.dir === 'NORTH' && Math.abs(p.left - 9.5) < 0.01, JSON.stringify([p.mode, p.left]));
  t.now = t0 + 2500; t.now = t0 + 12000; t.cmd('J01', 'VEHICLE', 'EAST', 8);
  const t1 = t.now; t.now = t1 + 400; p = sg.phaseOf('J01', t.now);
  ok('new command: previous green approach (NORTH) turns YELLOW', p.mode === 'YELLOW' && p.dir === 'NORTH');
  t.now = t1 + 1500; p = sg.phaseOf('J01', t.now);
  ok('then all red', p.mode === 'ALLRED');
  t.now = t1 + 2200; p = sg.phaseOf('J01', t.now);
  ok('then EAST green and others red', p.mode === 'GREEN' && sg.sigOf(p, 'EAST', true) === 'G' && sg.sigOf(p, 'NORTH', true) === 'R' && sg.sigOf(p, 'SOUTH', true) === 'R' && sg.sigOf(p, 'WEST', true) === 'R');
  t.now = t1 + 20000; ok('idle (all red) when no new command arrives', sg.phaseOf('J01', t.now).mode === 'IDLE');
  t.cmd('J01', 'PEDESTRIAN', 'WEST', 9); t.now += 2500; p = sg.phaseOf('J01', t.now);
  ok('pedestrian phase: WALK on commanded crossing and every vehicle approach red', p.mode === 'WALK' && p.dir === 'WEST' && ['NORTH', 'EAST', 'SOUTH', 'WEST'].every(d => sg.sigOf(p, d, true) === 'R'));
  t.cmd('J01', 'VEHICLE', 'SOUTH', 15, 'EMERGENCY', 'AMBULANCE'); t.now += 2500; p = sg.phaseOf('J01', t.now);
  ok('emergency phase: green on arrival direction, flagged emergency', p.mode === 'GREEN' && p.dir === 'SOUTH' && p.emg === true);
  t.ev('leave', 'J01'); ok('offline junction: no phase, lamps dark', sg.phaseOf('J01', t.now).mode === 'OFFLINE' && sg.sigOf({ mode: 'OFFLINE' }, 'NORTH', false) === 'X');
}

// ---------- 4. Simulation invariants (long run, all junctions, cycling signals) ----------
{
  const t = fresh(), sg = t.sg; ['J01', 'J02', 'J03'].forEach(j => t.join(j));
  const heavy = [[2, 6, 2, 18, 40], [3, 5, 1, 9, 20], [1, 4, 2, 26, 60], [1, 5, 3, 14, 30]];
  ['J01', 'J02', 'J03'].forEach(j => t.traffic(j, ...heavy));
  const dirs = ['NORTH', 'EAST', 'SOUTH', 'WEST'];
  let violations = { redrun: 0, overlap: 0, teleport: 0, reverse: 0, centre: 0, vanish: 0, wronglane: 0 }, sample = {};
  const seenIds = new Set(); let prev = new Map(), frontPrev = new Map(), cmdIdx = 0, nextCmd = 0, routesSeen = {};
  const dt = 0.05;
  const checkTick = () => {
    for (const j of ['J01', 'J02', 'J03']) { sg.phases[j] = sg.phaseOf(j, t.now); }
    const cur = new Map();
    for (const v of sg.vehicles) {
      const p = pos(sg, v); cur.set(v, { p, s: v.s, cs: v.cs, phase: v.phase, lane: v.lane });
      const pr = prev.get(v);
      if (pr) {
        const d = Math.hypot(p[0] - pr.p[0], p[1] - pr.p[1]);
        if (d > 125 * dt + 1.5) { violations.teleport++; sample.teleport = sample.teleport || `v${v.id} d=${d.toFixed(1)} ${pr.phase}->${v.phase}`; }
        if (v.phase === 'lane' && pr.phase === 'lane' && pr.lane === v.lane && v.s < pr.s - 1e-9) violations.reverse++;
        if (v.phase === 'conn' && pr.phase === 'conn' && v.cs < pr.cs - 1e-9) violations.reverse++;
        // stop line: front crossing while its approach signal is RED (use the gate evaluated in this tick)
        if (v.phase === 'lane' && v.lane.type === 'app' && pr.phase === 'lane' && pr.lane === v.lane) {
          const f0 = pr.s + v.len / 2, f1 = v.s + v.len / 2, g = sg.sigOf(sg.phases[v.lane.J], v.lane.dir, sg.state[v.lane.J].online);
          if (f0 <= v.lane.stopS + 0.01 && f1 > v.lane.stopS + 0.6 && g === 'R') { violations.redrun++; sample.redrun = `v${v.id} ${v.lane.key} f0=${f0.toFixed(1)} f1=${f1.toFixed(1)}`; }
        }
      }
      // never on the centre line / wrong half of a straight lane (cars only, not diverting)
      if (v.phase === 'lane' && !v.emg && !v.div && v.alpha > 0.99) {
        const l = v.lane, nrm = [l.d[1], -l.d[0]], px = p[0] + nrm[0] * v.lat, py = p[1] + nrm[1] * v.lat;
        const off = (l.d[0] !== 0) ? Math.abs(py - sg.CY) : Math.abs(px - sg.XC[l.J]);
        if (off < sg.LN - 3 || off > sg.LN + 3) { violations.centre++; sample.centre = `v${v.id} ${l.key} off=${off.toFixed(1)}`; }
      }
    }
    // vanish: only at a stub end or while diverting into a driveway
    for (const [v, pr] of prev) if (!cur.has(v)) {
      const okEnd = pr.lane.type === 'stub' && pr.s > pr.lane.len - 12; const okDiv = v.div === true;
      if (!(okEnd || okDiv)) { violations.vanish++; sample.vanish = `v${v.id} in ${pr.lane.key} s=${pr.s.toFixed(1)}/${pr.lane.len}`; }
    }
    // overlap in same lane (same class, both normal)
    for (const l of Object.values(sg.lanes).concat(Object.values(sg.stubs))) {
      const vs = l.vs.filter(v => !v.div && v.alpha > 0.99 && !v.emg).sort((a, b) => b.s - a.s);
      for (let i = 1; i < vs.length; i++) { const gap = vs[i - 1].s - vs[i].s - (vs[i - 1].len + vs[i].len) / 2; if (gap < GAPMIN) { violations.overlap++; sample.overlap = `${l.key} gap=${gap.toFixed(1)}`; } }
    }
    prev = cur;
  };
  const GAPMIN = -1.5;
  t.cmd('J01', 'VEHICLE', 'WEST', 12); t.cmd('J02', 'VEHICLE', 'NORTH', 12); t.cmd('J03', 'VEHICLE', 'EAST', 12);
  const seq = { J01: 0, J02: 1, J03: 2 }; let lastCmdT = t.now;
  for (let i = 0; i < Math.round(240 / dt); i++) {
    t.now += dt * 1000;
    if (t.now - lastCmdT > 14000) { lastCmdT = t.now; ['J01', 'J02', 'J03'].forEach((j, k) => { seq[j]++; t.cmd(j, 'VEHICLE', dirs[(seq[j] + k) % 4], 12); }); }
    if (i % 100 === 0) ['J01', 'J02', 'J03'].forEach(j => t.traffic(j, ...heavy));
    sg.step(dt); checkTick();
    // record how each vehicle leaves each approach (route -> exit lane)
    for (const j of ['J01', 'J02', 'J03']) for (const v of sg.box[j]) if (!seenIds.has(v.id)) { seenIds.add(v.id); routesSeen[v.conn.lane.key + ':' + v.route + '->' + v.conn.exit.key] = true; }
  }
  ok('no vehicle crossed a stop line on RED over 240 s x 3 junctions', violations.redrun === 0, sample.redrun);
  ok('no vehicle teleported (continuous motion incl. junction transitions)', violations.teleport === 0, sample.teleport);
  ok('no vehicle reversed', violations.reverse === 0);
  ok('vehicles never on the centre line / wrong half of the road', violations.centre === 0, sample.centre);
  ok('vehicles vanish only at the road end or by pulling into a roadside driveway', violations.vanish === 0, sample.vanish);
  ok('no excessive overlap between vehicles in a lane', violations.overlap === 0, sample.overlap);
  const keys = Object.keys(routesSeen);
  const byRoute = r => keys.filter(k => k.includes(':' + r + '->')).length;
  ok(`vehicles completed LEFT (${byRoute('L')}), STRAIGHT (${byRoute('S')}) and RIGHT (${byRoute('R')}) movements`, byRoute('L') > 0 && byRoute('S') > 0 && byRoute('R') > 0);
  const westStraight = keys.some(k => k.startsWith('J01-WEST:S->J02-WEST'));
  ok('vehicles continue from J01 straight east into the J02 approach road', westStraight);
  ok('vehicle count stays bounded (no leak)', sg.vehicles.length < 260, 'count=' + sg.vehicles.length);
}

// ---------- 5. Red holds, green releases, queue consistency ----------
{
  const t = fresh(), sg = t.sg; t.join('J02');
  t.traffic('J02', [1, 2, 1, 6, 10], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0]);   // NORTH queue 6
  t.run(0.1);
  const lane = sg.lanes['J02-NORTH'];
  ok('initial queue drawn to match reported queue (6 cars on the NORTH approach)', lane.vs.length === 6, 'cars=' + lane.vs.length);
  ok('no cars on approaches that report queue 0', ['EAST', 'SOUTH', 'WEST'].every(d => sg.lanes['J02-' + d].vs.length === 0));
  t.run(20);
  const stopped = lane.vs.every(v => v.v < 0.5);
  const head = lane.vs.slice().sort((a, b) => b.s - a.s)[0];
  ok('with no green, queued vehicles wait stationary behind the stop line', stopped && head.s + head.len / 2 <= lane.stopS + 0.3, `front=${(head.s + head.len / 2).toFixed(1)} stop=${lane.stopS}`);
  ok('queue does not grow beyond the reported queue (6)', lane.vs.length <= 6, 'cars=' + lane.vs.length);
  t.traffic('J02', [1, 2, 1, 3, 10], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0]);   // queue drops to 3: no new arrivals
  t.run(5); ok('when reported queue is lower than drawn, no extra cars are added', lane.vs.length <= 6);
  t.cmd('J02', 'VEHICLE', 'NORTH', 10); let released = 0; const before = lane.vs.length;
  t.run(4.5); ok('after the 2 s clearance NORTH green releases the queue (cars move forward)', lane.vs.length < before || sg.box['J02'].length > 0, `before=${before} now=${lane.vs.length}`);
  t.run(10); ok('queued cars have crossed the junction and left the NORTH approach', lane.vs.filter(v => v.s < lane.stopS - 5 && v.v < 0.5).length === 0);
  t.traffic('J02', [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0]);
  t.run(30); ok('with all queues 0 the road empties (no new cars spawn)', sg.vehicles.filter(v => !v.div).every(v => v.lane.type === 'stub' || true) && ['NORTH', 'EAST', 'SOUTH', 'WEST'].every(d => sg.lanes['J02-' + d].vs.length === 0));
}

// ---------- 6. Pedestrian + emergency requests: state tracking and visuals ----------
{
  const t = fresh(), sg = t.sg; ['J01', 'J02', 'J03'].forEach(j => t.join(j));
  ['J01', 'J02', 'J03'].forEach(j => t.traffic(j, [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0], [0, 0, 0, 0, 0]));
  t.run(1);
  // request accepted via HTTP: add to "my requests" as the form would, then the server's reqstate event confirms it
  const id = t.now; sg.myReqs.unshift({ id, kind: 'pedestrian', junction: 'J01', dir: 'NORTH', detail: '×4', ts: t.now });
  t.ev('reqstate', `REQSTATE|J01|NORTH*4*${id}|`);
  ok('pedestrian request shows as pending on J01 with direction and count', sg.state.J01.pendPed.length === 1 && sg.state.J01.pendPed[0].dir === 'NORTH' && sg.state.J01.pendPed[0].val === 4);
  ok('request status = QUEUED while it is in the server queue', sg.reqStatus(sg.myReqs[0], t.now) === 'QUEUED');
  t.ev('reqstate', `REQSTATE|J01||`); t.ev('decision', `DECISION|J01|PEDESTRIAN|NORTH|0|9|0||Pedestrian request (4 waiting) served`);
  t.cmd('J01', 'PEDESTRIAN', 'NORTH', 9);
  t.now += 500; ok('status = SERVED when the server takes it for the next phase', sg.reqStatus(sg.myReqs[0], t.now) === 'SERVED');
  t.now += 2500; ok('status = ACTIVE during the WALK phase', sg.reqStatus(sg.myReqs[0], t.now) === 'ACTIVE');
  const ph = sg.phaseOf('J01', t.now); ok('WALK indicated on the NORTH crossing; all vehicle signals red', ph.mode === 'WALK' && ph.dir === 'NORTH' && ['NORTH', 'EAST', 'SOUTH', 'WEST'].every(d => sg.sigOf(ph, d, true) === 'R'));
  t.now += 12000; ok('status = COMPLETED after the phase', sg.reqStatus(sg.myReqs[0], t.now) === 'COMPLETED');
  // emergency
  const eid = t.now + 7; t.ev('reqstate', `REQSTATE|J03||EAST*FIRE_TRUCK*${eid}`);
  t.run(3);
  const l = sg.lanes['J03-EAST']; const emg = l.vs.filter(v => v.emg);
  ok('emergency vehicle appears on the requested approach (J03 EAST) with the right type', emg.length === 1 && emg[0].etype === 'FIRE_TRUCK');
  ok('emergency vehicle is not duplicated by repeated reqstate events', (t.ev('reqstate', `REQSTATE|J03||EAST*FIRE_TRUCK*${eid}`), t.run(2), sg.vehicles.filter(v => v.emg).length === 1));
  t.run(12); const e0 = sg.vehicles.find(v => v.emg);
  ok('emergency vehicle waits at the stop line while its signal is red (does not run the red)', e0 && e0.phase === 'lane' && e0.s + e0.len / 2 <= l.stopS + 0.3, e0 ? `s=${e0.s}` : 'gone');
  t.ev('reqstate', `REQSTATE|J03||`); t.cmd('J03', 'VEHICLE', 'EAST', 15, 'EMERGENCY', 'FIRE_TRUCK');
  t.run(5); ok('emergency green releases the emergency vehicle through the junction', !sg.vehicles.some(v => v.emg && v.lane === l && v.phase === 'lane' && v.s < l.stopS));
  t.run(10); ok('emergency vehicle leaves the visible road at its exit', !sg.vehicles.some(v => v.emg));
}

// ---------- 7. Real captured SSE feed from the Java server ----------
{
  const f = '/tmp/t/sse.txt';
  if (fs.existsSync(f)) {
    const t = fresh(), sg = t.sg; let n = 0, err = 0;
    for (const line of fs.readFileSync(f, 'utf8').split('\n')) if (line.startsWith('data: ')) { try { sg.handleEvent(JSON.parse(line.slice(6))); n++; } catch (e) { err++; } }
    ok(`real SSE capture parsed without errors (${n} events)`, n > 20 && err === 0);
    ok('real feed populated traffic / command / decision / request state', ['J01', 'J02', 'J03'].every(j => sg.state[j].traffic && sg.state[j].command && sg.state[j].decision));
    t.now = Date.now(); sg.setNow(() => Date.now());
    let renderErr = null; try { sg.renderPanels(); sg.cardsHtml(Date.now()); sg.decisionHtml(Date.now()); sg.renderVehicles(); } catch (e) { renderErr = e.message; }
    ok('panels render from real data without exceptions', renderErr === null, renderErr);
  } else console.log('[SKIP] no /tmp/t/sse.txt capture');
}
console.log(`\n${pass} passed, ${fail} failed`); process.exit(fail ? 1 : 0);
