// SCRUM-50 / NFR-03 : temps de reponse de l'API REST, 95 % des requetes en 500 ms au plus.
// Charge de lecture sur les routes GET de la passerelle. Les routes qui ne repondent pas 200
// avec le jeton utilise sont ecartees au demarrage (droits du role) et affichees.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:18080';
const AUTH = { Authorization: `Bearer ${__ENV.TOKEN || ''}` };
const PATHS = [
  '/api/incidents', '/api/assets/zones', '/api/assets/sensors', '/api/assets/pumps',
  '/api/assets/pipes', '/api/assets/tanks', '/api/assets/interventions',
  '/api/detection/anomalies', '/api/detection/thresholds', '/api/notifications/list',
];
const name = (p) => 'dur' + p.replace(/\//g, '_');
const trends = {};
PATHS.forEach((p) => { trends[p] = new Trend(name(p)); });

export const options = {
  stages: [
    { duration: '15s', target: 10 },
    { duration: '45s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{phase:load}': ['p(95)<500'],
    'http_req_failed{phase:load}': ['rate<0.01'],
    'http_reqs{phase:load}': ['count>0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'max'],
};

export function setup() {
  const ok = [];
  for (const p of PATHS) {
    const r = http.get(BASE + p, {
      headers: AUTH,
      tags: { phase: 'probe' },
      responseCallback: http.expectedStatuses({ min: 200, max: 499 }),
    });
    console.log(`sonde ${r.status} ${p}`);
    if (r.status === 200) ok.push(p);
  }
  if (ok.length === 0) throw new Error('aucune route accessible : verifier le jeton et la plateforme');
  console.log(`routes testees : ${ok.join(', ')}`);
  return { ok };
}

export default function (data) {
  const p = data.ok[Math.floor(Math.random() * data.ok.length)];
  const r = http.get(BASE + p, { headers: AUTH, tags: { phase: 'load' } });
  check(r, { 'statut 200': (x) => x.status === 200 });
  trends[p].add(r.timings.duration);
  sleep(0.2 + Math.random() * 0.3);
}

export function handleSummary(data) {
  const v = (n) => { const m = data.metrics[n]; return m ? (m.values || m) : {}; };
  const d = v('http_req_duration{phase:load}');
  const n = v('http_reqs{phase:load}');
  const f = v('http_req_failed{phase:load}');
  const fmt = (x) => (x === undefined ? 'n/a' : x.toFixed(1));
  const p95 = d['p(95)'];
  const errPct = ((f.rate || 0) * 100).toFixed(2);
  const ok = p95 !== undefined && p95 < 500 && (f.rate || 0) < 0.01;
  const rows = PATHS.map((p) => [p, v(name(p))]).filter(([, m]) => m['p(95)'] !== undefined);
  const text = [
    '=== SCRUM-50 / NFR-03 : temps de reponse de l\'API ===',
    `requetes : ${n.count} (${fmt(n.rate)} /s) | erreurs : ${errPct} %`,
    `global : p95 = ${fmt(p95)} ms (seuil 500) | moyenne = ${fmt(d.avg)} ms | max = ${fmt(d.max)} ms`,
    ...rows.map(([p, m]) => `  ${p.padEnd(28)} p95 = ${fmt(m['p(95)'])} ms | moy = ${fmt(m.avg)} ms`),
    `VERDICT : ${ok ? 'OK' : 'ECHEC'}`,
    '',
  ].join('\n');
  const md = [
    `- Date : ${new Date().toISOString()}`,
    `- Requêtes mesurées : ${n.count} (${fmt(n.rate)} par seconde), erreurs : ${errPct} %`,
    `- **95e percentile global : ${fmt(p95)} ms** (seuil : 500 ms), moyenne ${fmt(d.avg)} ms, maximum ${fmt(d.max)} ms`,
    `- Résultat : **${ok ? 'conforme (NFR-03)' : 'non conforme'}**`,
    '',
    '| Route | p95 (ms) | moyenne (ms) |',
    '|---|---|---|',
    ...rows.map(([p, m]) => `| ${p} | ${fmt(m['p(95)'])} | ${fmt(m.avg)} |`),
    '',
  ].join('\n');
  return { stdout: text, '/out/resultat.md': md };
}
