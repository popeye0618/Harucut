// 합성 파이프라인 부하 측정 도구
//
//   node run.mjs seed                          계정·프레임·원본 사진을 한 번 만든다
//   node run.mjs load --n 30 --label before    동시 n건을 던지고 끝까지 추적한다
//
// 측정하는 것은 두 가지다.
//   1) 접수 응답 시간  — POST /compose 가 돌아오기까지
//   2) 최종 상태 분포  — 끝까지 따라가서 DONE / FAILED / 미완 을 센다
// 포트폴리오에 쓰는 유실 건수는 2번에서 나온다. 이건 애플리케이션 메트릭이 아니라
// 작업의 최종 상태라서 Grafana 로는 볼 수 없다.

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.BASE_URL ?? 'http://localhost:18080';
const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8026';
const SEED_DIR = process.env.SEED_DIR ?? join(HERE, '..', '..', '..', 'seed');
const STATE = join(HERE, 'seed.json');
const OUT_DIR = join(HERE, 'results');

const args = process.argv.slice(2);
const cmd = args[0];
const opt = (name, fallback) => {
  const i = args.indexOf('--' + name);
  return i >= 0 ? args[i + 1] : fallback;
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ── HTTP ────────────────────────────────────────────────

let cookie = '';

async function call(method, path, body, extra = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(cookie ? { Cookie: cookie } : {}),
      ...(extra.headers ?? {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const set = res.headers.getSetCookie?.() ?? [];
  if (set.length) {
    // httpOnly 쿠키로 인증한다. 이름=값 부분만 모아 두면 된다.
    const jar = new Map(cookie ? cookie.split('; ').map((c) => c.split('=').map((s, i) => (i ? c.slice(c.indexOf('=') + 1) : s))) : []);
    for (const c of set) {
      const [pair] = c.split(';');
      const eq = pair.indexOf('=');
      jar.set(pair.slice(0, eq), pair.slice(eq + 1));
    }
    cookie = [...jar].map(([k, v]) => `${k}=${v}`).join('; ');
  }
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* 본문이 JSON 이 아닌 경우 */ }
  return { status: res.status, ok: res.ok, json, text };
}

function must(res, what) {
  if (!res.ok) {
    throw new Error(`${what} 실패 (HTTP ${res.status}): ${res.text.slice(0, 300)}`);
  }
  return res;
}

// ── 준비 ────────────────────────────────────────────────

async function waitForApp() {
  process.stdout.write('앱 기동 대기 ');
  for (let i = 0; i < 120; i++) {
    try {
      const r = await fetch(BASE + '/api/auth/status', { method: 'GET' });
      if (r.status < 500) { console.log('완료'); return; }
    } catch { /* 아직 안 떴다 */ }
    process.stdout.write('.');
    await sleep(2000);
  }
  throw new Error('앱이 240초 안에 뜨지 않았다');
}

async function fetchCode(email) {
  // Mailpit 에 도착한 메일에서 6자리 코드를 꺼낸다.
  for (let i = 0; i < 30; i++) {
    const r = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + email)}`);
    if (r.ok) {
      const { messages } = await r.json();
      if (messages?.length) {
        const detail = await (await fetch(`${MAILPIT}/api/v1/message/${messages[0].ID}`)).json();
        // 코드는 숫자가 아니라 대문자+숫자 6자다(예: UD29CC).
        // HTML 에는 CSS 색상값(#222222 등)이 섞여 있어 텍스트 본문에서만 찾는다.
        // 본문에서 코드는 자기 줄에 혼자 있다.
        const line = (detail.Text ?? '')
          .split(/\r?\n/)
          .map((s) => s.trim())
          .find((s) => /^[A-Z0-9]{6}$/.test(s) && /[A-Z]/.test(s + 'X'));
        if (line) return line;
      }
    }
    await sleep(1000);
  }
  throw new Error('인증 메일에서 코드를 찾지 못했다');
}

async function uploadOne(type, filename, bytes) {
  const res = must(await call('POST', '/api/auth/user/files/presigned-upload', {
    type,
    filename,
    contentType: 'PNG',
    fileSize: bytes.length,
  }), `presigned 발급(${filename})`);
  const { key, uploadUrl, contentType } = res.json.data ?? res.json;
  const put = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': contentType, 'Content-Length': String(bytes.length) },
    body: bytes,
  });
  if (!put.ok) throw new Error(`S3 업로드 실패(${filename}): HTTP ${put.status}`);
  return key;
}

async function seed() {
  await waitForApp();

  const password = opt('password', 'LoadTest1234!');
  const reuse = opt('email', null);
  const email = reuse ?? `load${Date.now()}@harucut.test`;

  if (reuse) {
    // 새 계정은 BASIC 플랜이라 프레임 보관 한도가 0 이다.
    // 계정을 만든 뒤 플랜을 올리고, 이 옵션으로 그 계정을 다시 써서 프레임까지 만든다.
    console.log(`기존 계정 사용: ${email}`);
  } else {
    console.log(`계정 생성: ${email}`);
    must(await call('POST', '/api/email-auth/code', { email }), '인증 코드 발송');
    const code = await fetchCode(email);
    must(await call('POST', '/api/email-auth/verification', { email, code }), '인증 코드 검증');
    must(await call('POST', '/api/harucut/register', { email, username: 'loadtester', password }), '회원가입');
  }
  must(await call('POST', '/api/harucut/login', { email, password }), '로그인');
  console.log('로그인 완료');

  console.log('원본 사진 4장 + 프레임 미리보기 업로드');
  const sourceKeys = [];
  for (const name of ['cut1', 'cut2', 'cut3', 'cut4']) {
    const bytes = readFileSync(join(SEED_DIR, `${name}.png`));
    sourceKeys.push(await uploadOne('FOURCUT_SOURCE', `${name}.png`, bytes));
  }
  const previewKey = await uploadOne('FRAME', 'preview.png', readFileSync(join(SEED_DIR, 'preview.png')));

  console.log('프레임 생성');
  const frame = must(await call('POST', '/api/auth/user/frame', {
    title: '부하 측정용 프레임',
    description: '컴포넌트 없이 배경만 있는 최소 프레임',
    previewKey,
    frameType: 'CLASSIC',
    background: { type: 'COLOR', value: '#1E1E23' },
    cellCutouts: [true, true, true, true],
    components: [],
  }), '프레임 생성');
  const frameId = (frame.json.data ?? frame.json).frameId ?? (frame.json.data ?? frame.json).id;

  writeFileSync(STATE, JSON.stringify({ email, password, cookie, frameId, sourceKeys }, null, 2));
  console.log(`\n준비 완료. frameId=${frameId}`);
  console.log(`상태 파일: ${STATE}`);
}

// ── 측정 ────────────────────────────────────────────────

function percentile(sorted, p) {
  if (!sorted.length) return null;
  const i = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[i];
}

async function load() {
  const n = Number(opt('n', 30));
  const label = opt('label', 'run');
  const timeoutSec = Number(opt('timeout', 300));

  if (!existsSync(STATE)) throw new Error('seed.json 이 없다. 먼저 node run.mjs seed 를 실행한다');
  const state = JSON.parse(readFileSync(STATE, 'utf8'));
  cookie = state.cookie;

  // 쿠키가 만료됐을 수 있으니 다시 로그인한다.
  const relogin = await call('POST', '/api/harucut/login', { email: state.email, password: state.password });
  if (relogin.ok) state.cookie = cookie;

  // 합성에 성공하면 서버가 원본 4장을 S3 에서 지운다(결과만 보관함에 남기는 정책).
  // 그래서 여러 요청이 같은 key 를 쓰면 첫 성공 이후 나머지가 404 로 죽는다.
  // 요청마다 원본 한 벌씩 따로 올린다.
  console.log(`원본 준비: ${n}건 x 4장 = ${n * 4}장 업로드`);
  const pics = ['cut1', 'cut2', 'cut3', 'cut4'].map((nm) => readFileSync(join(SEED_DIR, `${nm}.png`)));
  const keySets = new Array(n);
  const CONCURRENT_UPLOADS = 8;
  let next = 0;
  await Promise.all(
    Array.from({ length: CONCURRENT_UPLOADS }, async () => {
      while (true) {
        const idx = next++;
        if (idx >= n) return;
        const keys = [];
        for (let k = 0; k < 4; k++) keys.push(await uploadOne('FOURCUT_SOURCE', `cut${k + 1}.png`, pics[k]));
        keySets[idx] = keys;
        process.stdout.write(`\r  업로드 ${keySets.filter(Boolean).length}/${n}세트  `);
      }
    })
  );
  console.log('');

  console.log(`\n[${label}] 동시 ${n}건 발사`);
  const t0 = Date.now();

  const accepted = [];
  const rejected = [];
  await Promise.all(
    Array.from({ length: n }, async (_, i) => {
      const started = Date.now();
      try {
        const res = await call('POST', '/api/auth/user/media/compose', {
          frameId: state.frameId,
          sourceKeys: keySets[i],
          idempotencyKey: `${label}-${t0}-${i}`,
        });
        const ms = Date.now() - started;
        if (res.ok) {
          const d = res.json.data ?? res.json;
          accepted.push({ i, ms, jobId: d.jobId ?? d.id });
        } else {
          rejected.push({ i, ms, status: res.status, body: res.text.slice(0, 200) });
        }
      } catch (e) {
        rejected.push({ i, ms: Date.now() - started, status: 0, body: String(e) });
      }
    })
  );

  const acceptMs = accepted.map((a) => a.ms).sort((a, b) => a - b);
  console.log(`접수 완료  성공 ${accepted.length} / 실패 ${rejected.length}   경과 ${((Date.now() - t0) / 1000).toFixed(1)}초`);
  if (acceptMs.length) {
    console.log(`접수 응답  p50 ${percentile(acceptMs, 50)}ms  p95 ${percentile(acceptMs, 95)}ms  최대 ${acceptMs.at(-1)}ms`);
  }

  // 최종 상태를 끝까지 추적한다.
  console.log(`최종 상태 추적 (최대 ${timeoutSec}초)`);
  const settled = new Map();
  const deadline = Date.now() + timeoutSec * 1000;
  while (settled.size < accepted.length && Date.now() < deadline) {
    for (const a of accepted) {
      if (settled.has(a.jobId)) continue;
      const r = await call('GET', `/api/auth/user/media/compose/${a.jobId}`);
      const d = r.json?.data ?? r.json;
      if (d && d.status && d.status !== 'PENDING') {
        settled.set(a.jobId, { status: d.status, ms: Date.now() - t0, failureReason: d.failureReason ?? null });
      }
    }
    const remain = accepted.length - settled.size;
    process.stdout.write(`\r  확정 ${settled.size}/${accepted.length}  남음 ${remain}   `);
    if (settled.size < accepted.length) await sleep(3000);
  }
  console.log('');

  const done = [...settled.values()].filter((s) => s.status === 'DONE');
  const failed = [...settled.values()].filter((s) => s.status === 'FAILED');
  const stuck = accepted.length - settled.size;
  const doneMs = done.map((d) => d.ms).sort((a, b) => a - b);

  const summary = {
    label,
    requested: n,
    accepted: accepted.length,
    rejectedAtAccept: rejected.length,
    done: done.length,
    failed: failed.length,
    stillPending: stuck,
    lossRate: Number(((rejected.length + failed.length) / n * 100).toFixed(1)),
    acceptMs: {
      p50: percentile(acceptMs, 50),
      p95: percentile(acceptMs, 95),
      max: acceptMs.at(-1) ?? null,
    },
    endToEndSec: {
      p50: doneMs.length ? Math.round(percentile(doneMs, 50) / 1000) : null,
      max: doneMs.length ? Math.round(doneMs.at(-1) / 1000) : null,
    },
    failureReasons: [...new Set(failed.map((f) => f.failureReason).filter(Boolean))].slice(0, 5),
    rejectSamples: rejected.slice(0, 3),
    startedAt: new Date(t0).toISOString(),
    finishedAt: new Date().toISOString(),
  };

  mkdirSync(OUT_DIR, { recursive: true });
  writeFileSync(join(OUT_DIR, `${label}.json`), JSON.stringify(summary, null, 2));

  console.log('\n────────── 결과 ──────────');
  console.log(`요청            ${summary.requested}건`);
  console.log(`접수 성공       ${summary.accepted}건`);
  console.log(`접수 거부       ${summary.rejectedAtAccept}건`);
  console.log(`최종 DONE       ${summary.done}건`);
  console.log(`최종 FAILED     ${summary.failed}건   (영구 실패)`);
  console.log(`미완 PENDING    ${summary.stillPending}건`);
  console.log(`유실률          ${summary.lossRate}%`);
  console.log(`접수 응답 p95   ${summary.acceptMs.p95}ms`);
  console.log(`끝까지 걸린시간 p50 ${summary.endToEndSec.p50}초 / 최대 ${summary.endToEndSec.max}초`);
  if (summary.failureReasons.length) console.log(`실패 사유       ${summary.failureReasons.join(' | ')}`);
  console.log(`저장            ${join(OUT_DIR, label + '.json')}`);
}

// ── 진입점 ──────────────────────────────────────────────

try {
  if (cmd === 'seed') await seed();
  else if (cmd === 'load') await load();
  else {
    console.log('사용법:');
    console.log('  node run.mjs seed');
    console.log('  node run.mjs load --n 30 --label before-pool2 [--timeout 300]');
    process.exit(1);
  }
} catch (e) {
  console.error('\n오류:', e.message);
  process.exit(1);
}
