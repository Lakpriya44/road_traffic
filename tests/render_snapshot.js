// Renders a snapshot of the dashboard map to an SVG file (rasterize with cairosvg to inspect visually).
const fs = require('fs'), path = require('path');
const { load, ser } = require('./dashboard_harness.js');
const { sg, els } = load(); let now = 1e6; sg.setNow(() => now);
const ev = (t, d) => sg.handleEvent({ t, d, ts: now });
['J01', 'J02', 'J03'].forEach(j => ev('join', j));
const mix = [[2, 5, 2, 8, 30], [1, 4, 2, 6, 20], [2, 3, 3, 7, 25], [1, 6, 1, 9, 40]];
const tr = (j, q) => ev('traffic', `TRAFFIC|${j}|${now}|` + ['NORTH', 'EAST', 'SOUTH', 'WEST'].map((d, i) => d + ',' + mix[i].slice(0, 3).join(',') + ',' + (q[i]) + ',' + mix[i][4]).join('|'));
tr('J01', [5, 3, 7, 9]); tr('J02', [6, 4, 3, 8]); tr('J03', [4, 5, 6, 2]);
const cmd = (j, ph, d, dur, m, v) => ev('command', `SIGNAL_COMMAND|${j}|${ph}|${d}|${dur}|${m || 'NORMAL'}|${v || 'NONE'}|${now}`);
cmd('J01', 'VEHICLE', 'NORTH', 14); cmd('J02', 'VEHICLE', 'WEST', 14); cmd('J03', 'PEDESTRIAN', 'SOUTH', 12);
ev('reqstate', `REQSTATE|J02|EAST*3*${now}|SOUTH*AMBULANCE*${now+1}`); ev('reqstate', `REQSTATE|J01|NORTH*4*${now+2}|`);
const mode = process.argv[2] || 'mid';
for (let i = 0; i < 20 * (mode === 'early' ? 4 : 11); i++) { now += 50; sg.step(0.05); }
sg.renderVehicles(); sg.renderSignals(now);
const css = fs.readFileSync(path.join(__dirname, '..', 'web', 'dashboard.html'), 'utf8').match(/<style>([\s\S]*?)<\/style>/)[1]
  .replace(/filter:drop-shadow\([^)]*\)/g, '').replace(/animation:[^;}]*/g, '');
const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 640" width="1600" height="640"><style>${css}</style>${els.map.children.map(ser).join('')}</svg>`;
fs.writeFileSync(process.argv[3] || '/tmp/snap.svg', svg);
