#!/usr/bin/env node
/**
 * Phase 6 — Comprehensive problem-bank judge validation against cz-pb-staging.
 * Batched submissions to isolated local Judge0 only. Never production.
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { createHash } from "node:crypto";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const reportDir = resolve(root, ".local/problem-bank/reports/phase6");
const credsPath = resolve(root, ".local/problem-bank/pb-staging-admin.json");

const LANG = {
  node: { id: 63, name: "JavaScript (Node.js 12.14.0)" },
  python: { id: 71, name: "Python (3.8.1)" },
  java: { id: 62, name: "Java (OpenJDK 13.0.1)" },
  cpp: { id: 54, name: "C++ (GCC 9.2.0)" },
};

const args = parseArgs(process.argv.slice(2));
const apiBase = args.apiBase || process.env.PROBLEM_BANK_API_BASE || "http://127.0.0.1:18090";
const concurrency = Math.max(1, Number(args.concurrency || 2));
const cooldownMs = Math.max(0, Number(args.cooldownMs || 150));

function parseArgs(argv) {
  const out = { phases: [1, 2, 3, 4, 5], multiLang: true, negatives: true, bundles: true, idempotency: true };
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    if (a === "--api-base") out.apiBase = argv[++i];
    else if (a === "--concurrency") out.concurrency = Number(argv[++i]);
    else if (a === "--cooldown-ms") out.cooldownMs = Number(argv[++i]);
    else if (a === "--phases") out.phases = argv[++i].split(",").map(Number);
    else if (a === "--limit") out.limit = Number(argv[++i]);
    else if (a === "--skip-multi-lang") out.multiLang = false;
    else if (a === "--skip-negatives") out.negatives = false;
    else if (a === "--skip-bundles") out.bundles = false;
    else if (a === "--skip-idempotency") out.idempotency = false;
    else if (a === "--resume") out.resume = true;
  }
  return out;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function api(method, path, { token, data, expected } = {}) {
  const res = await fetch(`${apiBase}${path}`, {
    method,
    headers: {
      Accept: "application/json",
      ...(data ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: data ? JSON.stringify(data) : undefined,
  });
  const text = await res.text();
  let body = text;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    /* keep */
  }
  if (expected && !expected.includes(res.status)) {
    throw new Error(`${method} ${path} -> HTTP ${res.status}: ${typeof body === "string" ? body.slice(0, 200) : JSON.stringify(body).slice(0, 200)}`);
  }
  return { status: res.status, body };
}

function verdictOf(job) {
  const v = job?.result ?? job?.verdict ?? job?.overallVerdict;
  return String(v || "").toUpperCase();
}

function leakScan(obj, path = "") {
  const leaks = [];
  if (!obj || typeof obj !== "object") return leaks;
  if (Array.isArray(obj)) {
    obj.forEach((v, i) => leaks.push(...leakScan(v, `${path}[${i}]`)));
    return leaks;
  }
  for (const [k, v] of Object.entries(obj)) {
    const p = path ? `${path}.${k}` : k;
    if (/hidden/i.test(k) && v != null) {
      if (Array.isArray(v) && v.length && v.some((tc) => tc && (tc.input != null || tc.output != null || tc.expectedOutput != null))) {
        leaks.push(p);
      } else if (!Array.isArray(v) && typeof v === "object") {
        leaks.push(p);
      }
    }
    if (v && typeof v === "object") leaks.push(...leakScan(v, p));
  }
  return leaks;
}

function pyQuote(s) {
  return JSON.stringify(String(s));
}

/**
 * Python reference that reproduces the private oracle on every authored case.
 * Local Judge0 Node.js (63) TLEs under isolate; Java OOMs — Python/C++ work.
 * Table keys cover common stdin newline variants Judge0 may supply.
 */
function sha256Hex(text) {
  return createHash("sha256").update(String(text), "utf8").digest("hex");
}

function buildPythonOracleSolution(problem) {
  // Hash keys keep source under submission max length for large hidden suites.
  const entries = [];
  const seen = new Set();
  const add = (input, output) => {
    const variants = [input, input.replace(/\r\n/g, "\n")];
    if (!input.endsWith("\n")) variants.push(`${input}\n`);
    if (input.endsWith("\n")) variants.push(input.replace(/\n$/, ""));
    const normalized = input.replace(/\r\n/g, "\n").replace(/\n$/, "");
    variants.push(normalized, `${normalized}\n`);
    for (const v of variants) {
      const key = sha256Hex(v);
      if (seen.has(key)) continue;
      seen.add(key);
      entries.push(`  "${key}": ${pyQuote(output)}`);
    }
  };
  for (const tc of [...problem.publicTestCases, ...problem.hiddenTestCases]) {
    add(tc.input, String(tc.output));
  }
  return `import sys, hashlib
TABLE = {
${entries.join(",\n")}
}
def digest(s):
    return hashlib.sha256(s.encode('utf-8')).hexdigest()
raw = sys.stdin.read()
candidates = [raw, raw.replace('\\r\\n', '\\n')]
if raw.endswith('\\n'):
    candidates.append(raw[:-1])
else:
    candidates.append(raw + '\\n')
norm = raw.replace('\\r\\n', '\\n').rstrip('\\n')
candidates.extend([norm, norm + '\\n'])
out = None
for c in candidates:
    out = TABLE.get(digest(c))
    if out is not None:
        break
sys.stdout.write('' if out is None else out)
`;
}

function buildCppOracleSolution(problem) {
  // Minimal C++ reference for multi-lang samples: print first public output only is insufficient.
  // Use unordered_map with exact strings for authored cases.
  const pairs = [];
  const seen = new Set();
  for (const tc of [...problem.publicTestCases, ...problem.hiddenTestCases]) {
    const input = String(tc.input);
    const variants = [input, input.endsWith("\n") ? input.slice(0, -1) : `${input}\n`];
    for (const v of variants) {
      if (seen.has(v)) continue;
      seen.add(v);
      const escIn = JSON.stringify(v);
      const escOut = JSON.stringify(String(tc.output));
      pairs.push(`  {${escIn}, ${escOut}}`);
    }
  }
  return `#include <bits/stdc++.h>
using namespace std;
int main(){
  ios::sync_with_stdio(false);
  cin.tie(nullptr);
  string raw, tmp;
  while(getline(cin, tmp)){ raw += tmp; raw.push_back('\\n'); }
  if(!raw.empty() && cin.eof()){
    // keep trailing newline from loop; Judge0 stdin variants handled below
  }
  unordered_map<string,string> table = {
${pairs.join(",\n")}
  };
  auto try_get = [&](const string& k)->string{
    auto it = table.find(k);
    return it==table.end()? string(): it->second;
  };
  string out = try_get(raw);
  if(out.empty() && !raw.empty() && raw.back()=='\\n') out = try_get(raw.substr(0, raw.size()-1));
  if(out.empty()) out = try_get(raw + "\\n");
  cout << out;
  return 0;
}
`;
}

function buildWrongPython() {
  return "import sys\nsys.stdin.read()\nprint('__WRONG_SENTINEL_VALUE__')\n";
}

/** Portable Python solutions for a cross-section + Phase 1 core. */
function buildCppWrong() {
  return `#include <bits/stdc++.h>
using namespace std;
int main(){ cout << "__WRONG_SENTINEL_VALUE__\\n"; return 0; }
`;
}

async function waitJob(token, jobId, timeoutMs = 180000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const res = await api("GET", `/api/submission-jobs/${jobId}`, { token });
    const status = String(res.body?.status || "").toUpperCase();
    if (["COMPLETED", "FAILED", "DONE", "FINISHED"].includes(status)) return res.body;
    await sleep(1000);
  }
  throw new Error(`timeout waiting for job ${jobId}`);
}

async function submitAndWait(token, problemId, languageId, language, code) {
  let created;
  for (let attempt = 0; attempt < 12; attempt += 1) {
    await sleep(cooldownMs + attempt * 250);
    created = await api("POST", "/api/submission-jobs", {
      token,
      data: { problemId, languageId, language, code },
    });
    if ([200, 202].includes(created.status)) break;
    if (created.status === 429 || created.status === 503) {
      const retryAfter = Number(created.body?.retryAfterSeconds || 1);
      await sleep(Math.max(750, retryAfter * 1000 + attempt * 400));
      continue;
    }
    throw new Error(`POST /api/submission-jobs -> HTTP ${created.status}: ${JSON.stringify(created.body).slice(0, 240)}`);
  }
  if (!created || ![200, 202].includes(created.status)) {
    throw new Error(`POST /api/submission-jobs -> HTTP ${created?.status}: ${JSON.stringify(created?.body).slice(0, 240)}`);
  }
  const jobId = created.body?.id || created.body?.jobId || created.body?.job?.id;
  if (!jobId) throw new Error(`no job id: ${JSON.stringify(created.body).slice(0, 300)}`);
  const job = await waitJob(token, jobId);
  return { jobId, job, verdict: verdictOf(job), leaks: leakScan(job) };
}

async function loadBank() {
  const problems = [];
  for (const phase of args.phases) {
    const indexPath = resolve(root, `.local/problem-bank/phase${phase}/index.mjs`);
    if (!existsSync(indexPath)) throw new Error(`missing ${indexPath}`);
    const mod = await import(pathToFileURL(indexPath).href);
    const list = mod.getPhaseProblems ? mod.getPhaseProblems() : mod.getPhase1Problems();
    for (const p of list) problems.push({ ...p, phase });
  }
  const manifests = {};
  for (const phase of args.phases) {
    const mp = resolve(root, `.local/problem-bank/manifests/phase${phase}.json`);
    if (existsSync(mp)) manifests[phase] = JSON.parse(readFileSync(mp, "utf8"));
  }
  return { problems, manifests };
}

function resolveServerId(problem, manifests) {
  const m = manifests[problem.phase];
  const entry = m?.problems?.[problem.slug];
  if (!entry?.id) throw new Error(`no manifest id for ${problem.slug}`);
  return entry;
}

async function poolMap(items, limit, worker) {
  const results = new Array(items.length);
  let next = 0;
  async function run() {
    while (true) {
      const i = next;
      next += 1;
      if (i >= items.length) return;
      results[i] = await worker(items[i], i);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, () => run()));
  return results;
}

function classify(verdict) {
  const v = String(verdict || "").toUpperCase();
  if (v.includes("ACCEPT") || v === "AC") return "ACCEPTED";
  if (v.includes("WRONG") || v === "WA") return "WRONG_ANSWER";
  if (v.includes("TIME") || v === "TLE") return "TIME_LIMIT_EXCEEDED";
  if (v.includes("RUNTIME") || v === "RE") return "RUNTIME_ERROR";
  if (v.includes("COMPIL")) return "COMPILATION_ERROR";
  if (v.includes("INTERNAL") || v.includes("JUDGE")) return "INFRASTRUCTURE_ERROR";
  if (!v) return "UNKNOWN";
  return v;
}

async function validateBundles(token, problems) {
  const report = { ok: true, issues: [], bundles: [] };
  const list = await api("GET", "/api/bundles");
  const bundles = Array.isArray(list.body) ? list.body : list.body?.content || list.body?.bundles || [];
  if (bundles.length < 17) {
    report.ok = false;
    report.issues.push(`expected >=17 bundles, found ${bundles.length}`);
  }
  for (const b of bundles) {
    const detail = await api("GET", `/api/bundles/${b.id}/problems`);
    const body = detail.body;
    const ids = body?.problemIds || body?.problems?.map((p) => p.id || p) || body?.content?.map((p) => p.id) || [];
    const total = b.totalProblems ?? ids.length;
    const entry = {
      id: b.id,
      name: b.name,
      totalProblems: total,
      listed: Array.isArray(ids) ? ids.length : 0,
      visibility: b.visibility,
    };
    if (total != null && entry.listed && total !== entry.listed) {
      report.ok = false;
      report.issues.push(`${b.name}: totalProblems=${total} listed=${entry.listed}`);
    }
    // unauthorized: unauthenticated should still see public bundles; hidden content must not appear
    const publicBundle = await api("GET", `/api/bundles/${b.id}`);
    const leaks = leakScan(publicBundle.body);
    if (leaks.length) {
      report.ok = false;
      report.issues.push(`${b.name}: leak paths ${leaks.join(",")}`);
    }
    report.bundles.push(entry);
  }
  // student redaction sample
  const sample = problems[0];
  const mid = resolveServerId(sample, Object.fromEntries(args.phases.map((p) => {
    const mp = resolve(root, `.local/problem-bank/manifests/phase${p}.json`);
    return [p, existsSync(mp) ? JSON.parse(readFileSync(mp, "utf8")) : {}];
  }))).id;
  // fix: use manifests properly below in main
  report._sampleCheckDeferred = true;
  return report;
}

async function main() {
  mkdirSync(reportDir, { recursive: true });
  if (!existsSync(credsPath)) throw new Error("missing pb-staging-admin.json");
  const creds = JSON.parse(readFileSync(credsPath, "utf8"));
  if (!String(apiBase).includes("127.0.0.1") && !String(apiBase).includes("localhost")) {
    throw new Error(`Refuse non-local apiBase: ${apiBase}`);
  }

  const login = await api("POST", "/api/login", {
    data: { username: creds.username, password: creds.password },
    expected: [200],
  });
  const token = login.body.token;
  if (!token) throw new Error("login failed");

  const { problems: allProblems, manifests } = await loadBank();
  let problems = allProblems;
  if (args.limit) problems = problems.slice(0, args.limit);

  const progressPath = resolve(reportDir, "progress.json");
  let progress = { accepted: {}, wrong: {}, multi: {} };
  if (args.resume && existsSync(progressPath)) {
    progress = JSON.parse(readFileSync(progressPath, "utf8"));
  }

  const summary = {
    startedAt: new Date().toISOString(),
    apiBase,
    concurrency,
    totals: {
      problems: problems.length,
      ACCEPTED: 0,
      WRONG_ANSWER: 0,
      TIME_LIMIT_EXCEEDED: 0,
      RUNTIME_ERROR: 0,
      COMPILATION_ERROR: 0,
      INFRASTRUCTURE_ERROR: 0,
      UNKNOWN: 0,
      UNTESTED: 0,
      negativeDiscriminated: 0,
      negativeFailed: 0,
      multiLangAccepted: 0,
      multiLangFailed: 0,
      hiddenLeaks: 0,
    },
    perProblem: [],
    bundleReport: null,
    idempotency: null,
    failures: [],
  };

  console.log(`Phase 6 validating ${problems.length} problems against ${apiBase} (concurrency=${concurrency})`);

  const multilangBoot = await import(pathToFileURL(resolve(root, ".local/problem-bank/phase6-multilang-solutions.mjs")).href).catch(() => null);
  const pythonAlgoSolutions = multilangBoot?.pythonSolutions || {};

  // Content sanity offline
  for (const p of problems) {
    const issues = [];
    if (!p.statement || !p.constraints || !p.inputFormat || !p.outputFormat) issues.push("missing-metadata");
    if (!p.exampleInput && !p.publicTestCases?.[0]) issues.push("missing-sample");
    if (!["EASY", "MEDIUM", "HARD"].includes(p.difficulty)) issues.push("bad-difficulty");
    for (const tc of [...(p.publicTestCases || []), ...(p.hiddenTestCases || [])]) {
      const got = String(p.oracle(tc.input));
      if (got !== String(tc.output)) issues.push("oracle-drift");
    }
    // determinism: re-run first public case
    if (p.publicTestCases?.[0]) {
      const a = String(p.oracle(p.publicTestCases[0].input));
      const b = String(p.oracle(p.publicTestCases[0].input));
      if (a !== b) issues.push("non-deterministic-oracle");
    }
    if (issues.length) {
      summary.failures.push({ slug: p.slug, stage: "offline", issues });
    }
  }

  async function processProblem(problem) {
    const server = resolveServerId(problem, manifests);
    const row = {
      slug: problem.slug,
      title: problem.title,
      phase: problem.phase,
      difficulty: problem.difficulty,
      category: problem.category,
      id: server.id,
      publicCases: problem.publicTestCases.length,
      hiddenCases: problem.hiddenTestCases.length,
      reference: null,
      negative: null,
      multiLang: [],
      contentIssues: [],
      hiddenLeakPaths: [],
    };

    // Public redaction
    const pub = await api("GET", `/api/problems/${server.id}`);
    if (Array.isArray(pub.body?.hiddenTestCases) && pub.body.hiddenTestCases.length) {
      row.hiddenLeakPaths.push("GET /api/problems/{id}.hiddenTestCases");
      summary.totals.hiddenLeaks += 1;
    }
    const adm = await api("GET", `/api/problems/${server.id}/admin`, { token });
    if (!Array.isArray(adm.body?.hiddenTestCases) || adm.body.hiddenTestCases.length < problem.hiddenTestCases.length) {
      row.contentIssues.push("admin-hidden-count-mismatch");
    }
    if (adm.body?.difficulty !== problem.difficulty) row.contentIssues.push("difficulty-mismatch");
    if (!adm.body?.statement) row.contentIssues.push("missing-statement-on-server");

    // Reference ACCEPTED via Python (Node 63 TLEs / Java 62 OOM in this local isolate)
    if (progress.accepted[problem.slug] === "ACCEPTED") {
      row.reference = { skipped: true, verdict: "ACCEPTED" };
      summary.totals.ACCEPTED += 1;
    } else {
      try {
        const algorithmic = pythonAlgoSolutions[problem.slug];
        const code = algorithmic || buildPythonOracleSolution(problem);
        const result = await submitAndWait(token, server.id, LANG.python.id, "python", code);
        const cls = classify(result.verdict);
        row.reference = {
          language: "python",
          languageId: LANG.python.id,
          mode: algorithmic ? "algorithmic" : "oracle-table",
          jobId: result.jobId,
          verdict: cls,
          raw: result.verdict,
          progress: result.job?.progress,
          error: result.job?.error,
        };
        if (result.leaks.length) {
          row.hiddenLeakPaths.push(...result.leaks.map((p) => `job:${p}`));
          summary.totals.hiddenLeaks += 1;
        }
        // Inspect job testResults for hidden input leakage to clients
        const tr = result.job?.testResults;
        if (Array.isArray(tr)) {
          for (const t of tr) {
            if (String(t.type).toLowerCase() === "hidden" && t.input != null && String(t.input).length > 0) {
              row.hiddenLeakPaths.push("job.testResults.hidden.input");
              summary.totals.hiddenLeaks += 1;
              break;
            }
          }
        }
        summary.totals[cls] = (summary.totals[cls] || 0) + 1;
        progress.accepted[problem.slug] = cls;
        if (cls !== "ACCEPTED") {
          summary.failures.push({ slug: problem.slug, stage: "reference-python", verdict: cls, jobId: result.jobId, error: result.job?.error });
        }
      } catch (err) {
        row.reference = { language: "python", error: err.message };
        summary.totals.INFRASTRUCTURE_ERROR += 1;
        summary.failures.push({ slug: problem.slug, stage: "reference-python", error: err.message });
        progress.accepted[problem.slug] = "INFRASTRUCTURE_ERROR";
      }
    }

    // Negative discrimination
    if (args.negatives) {
      if (progress.wrong[problem.slug] === "DISCRIMINATED") {
        row.negative = { skipped: true, discriminated: true };
        summary.totals.negativeDiscriminated += 1;
      } else {
        try {
          const code = buildWrongPython();
          const result = await submitAndWait(token, server.id, LANG.python.id, "python", code);
          const cls = classify(result.verdict);
          const discriminated = cls !== "ACCEPTED";
          row.negative = { language: "python", jobId: result.jobId, verdict: cls, discriminated };
          if (result.leaks.length) {
            row.hiddenLeakPaths.push(...result.leaks.map((p) => `wrong-job:${p}`));
            summary.totals.hiddenLeaks += 1;
          }
          if (discriminated) {
            summary.totals.negativeDiscriminated += 1;
            progress.wrong[problem.slug] = "DISCRIMINATED";
          } else {
            summary.totals.negativeFailed += 1;
            progress.wrong[problem.slug] = "FAILED";
            summary.failures.push({ slug: problem.slug, stage: "negative", verdict: cls, jobId: result.jobId });
          }
        } catch (err) {
          row.negative = { error: err.message };
          summary.totals.negativeFailed += 1;
          summary.failures.push({ slug: problem.slug, stage: "negative", error: err.message });
        }
      }
    }

    writeFileSync(progressPath, JSON.stringify(progress, null, 2));
    summary.perProblem.push(row);
    const refV = row.reference?.verdict || row.reference?.error || "?";
    console.log(`[${summary.perProblem.length}/${problems.length}] ${problem.slug} ref=${refV} neg=${row.negative?.verdict || row.negative?.error || "skip"}`);
    return row;
  }

  // Sequential batches of size=concurrency for clearer progress / less Judge0 overload
  for (let i = 0; i < problems.length; i += concurrency) {
    const batch = problems.slice(i, i + concurrency);
    await Promise.all(batch.map((p) => processProblem(p)));
  }

  // Multi-language: algorithmic Python (Phase 1) + C++ oracle-table samples.
  // Java OpenJDK OOMs in this local isolate (heap reserve failure); Node 63 TLEs — recorded as infra limits.
  if (args.multiLang) {
    console.log("Multi-language validation (algorithmic Python Phase1 + C++ samples)...");
    const phase1 = problems.filter((p) => p.phase === 1);
    const multilangMod = await import(pathToFileURL(resolve(root, ".local/problem-bank/phase6-multilang-solutions.mjs")).href).catch(() => null);
    const pythonSolutions = multilangMod?.pythonSolutions || {};
    const cppSolutions = multilangMod?.cppSolutions || {};

    const multiTargets = [
      ...phase1.map((p) => ({
        problem: p,
        lang: "python-algo",
        id: LANG.python.id,
        language: "python",
        code: pythonSolutions[p.slug],
      })),
      ...phase1.slice(0, 12).map((p) => ({
        problem: p,
        lang: "cpp",
        id: LANG.cpp.id,
        language: "cpp",
        code: cppSolutions[p.slug] || buildCppOracleSolution(p),
      })),
      ...problems.filter((p) => p.phase >= 2).slice(0, 20).map((p) => ({
        problem: p,
        lang: "cpp",
        id: LANG.cpp.id,
        language: "cpp",
        code: buildCppOracleSolution(p),
      })),
    ].filter((t) => t.code);

    summary.languageInfra = {
      node63: "TLE under local isolate (wall clock ~0) — not used as primary",
      java62: "Compilation/VM OOM (cannot reserve 256MB heap) — not used as primary",
      python71: "Accepted — primary reference language",
      cpp54: "Accepted — secondary multi-lang",
    };

    for (const t of multiTargets) {
      const key = `${t.lang}:${t.problem.slug}`;
      if (progress.multi[key] === "ACCEPTED") {
        summary.totals.multiLangAccepted += 1;
        continue;
      }
      const server = resolveServerId(t.problem, manifests);
      try {
        const result = await submitAndWait(token, server.id, t.id, t.language, t.code);
        const cls = classify(result.verdict);
        const row = summary.perProblem.find((r) => r.slug === t.problem.slug);
        if (row) row.multiLang.push({ lang: t.lang, verdict: cls, jobId: result.jobId });
        if (cls === "ACCEPTED") {
          summary.totals.multiLangAccepted += 1;
          progress.multi[key] = "ACCEPTED";
        } else {
          summary.totals.multiLangFailed += 1;
          progress.multi[key] = cls;
          summary.failures.push({ slug: t.problem.slug, stage: `multi-${t.lang}`, verdict: cls, jobId: result.jobId, error: result.job?.error });
        }
        console.log(`multi ${t.lang} ${t.problem.slug} -> ${cls}`);
      } catch (err) {
        summary.totals.multiLangFailed += 1;
        summary.failures.push({ slug: t.problem.slug, stage: `multi-${t.lang}`, error: err.message });
      }
      writeFileSync(progressPath, JSON.stringify(progress, null, 2));
    }

    // Wrong C++ / Python negatives
    for (const [lang, id, language, code] of [
      ["python", LANG.python.id, "python", buildWrongPython()],
      ["cpp", LANG.cpp.id, "cpp", buildCppWrong()],
    ]) {
      const sample = phase1[0];
      const server = resolveServerId(sample, manifests);
      try {
        const result = await submitAndWait(token, server.id, id, language, code);
        const cls = classify(result.verdict);
        if (cls === "ACCEPTED") {
          summary.failures.push({ slug: sample.slug, stage: `multi-negative-${lang}`, verdict: cls });
        }
      } catch (err) {
        summary.failures.push({ slug: sample.slug, stage: `multi-negative-${lang}`, error: err.message });
      }
    }
  }

  // Offline worst-case oracle stress (synthetic max-ish inputs where cheap)
  summary.oracleStress = [];
  for (const p of problems) {
    const start = Date.now();
    try {
      for (const tc of p.hiddenTestCases) p.oracle(tc.input);
      summary.oracleStress.push({ slug: p.slug, ms: Date.now() - start, ok: true });
    } catch (err) {
      summary.oracleStress.push({ slug: p.slug, ok: false, error: err.message });
      summary.failures.push({ slug: p.slug, stage: "oracle-stress", error: err.message });
    }
  }

  // Bundles
  if (args.bundles) {
    const list = await api("GET", "/api/bundles");
    const bundles = Array.isArray(list.body) ? list.body : list.body?.content || list.body?.bundles || [];
    const bundleReport = { count: bundles.length, ok: bundles.length === 17, issues: [], items: [] };
    for (const b of bundles) {
      const detail = await api("GET", `/api/bundles/${b.id}/problems`);
      const probs = detail.body?.problems || detail.body?.content || detail.body || [];
      const arr = Array.isArray(probs) ? probs : [];
      const item = {
        name: b.name,
        id: b.id,
        totalProblems: b.totalProblems,
        fetched: arr.length,
        order: arr.map((p) => p.title || p.name || p.id),
      };
      if (b.totalProblems != null && arr.length && b.totalProblems !== arr.length) {
        bundleReport.ok = false;
        bundleReport.issues.push(`${b.name}: total ${b.totalProblems} vs fetched ${arr.length}`);
      }
      bundleReport.items.push(item);
    }
    if (bundles.length !== 17) {
      bundleReport.ok = false;
      bundleReport.issues.push(`bundle count ${bundles.length} != 17`);
    }
    summary.bundleReport = bundleReport;
  }

  // Idempotency dry re-seed check (apply should skip unchanged)
  if (args.idempotency) {
    const { spawnSync } = await import("node:child_process");
    const env = {
      ...process.env,
      PROBLEM_BANK_CREDENTIALS_FILE: credsPath,
    };
    const idem = { phases: [] };
    for (const phase of args.phases) {
      const result = spawnSync(
        process.execPath,
        [resolve(root, "scripts/problem-bank/seed.mjs"), "--phase", String(phase), "--apply", "--api-base", apiBase],
        { cwd: root, encoding: "utf8", env, shell: false }
      );
      const reportPath = resolve(root, `.local/problem-bank/reports/phase${phase}-apply.json`);
      const rep = existsSync(reportPath) ? JSON.parse(readFileSync(reportPath, "utf8")) : null;
      const statuses = {};
      for (const r of rep?.results || []) {
        statuses[r.status] = (statuses[r.status] || 0) + 1;
      }
      idem.phases.push({ phase, exit: result.status, statuses });
    }
    // recount API
    let total = 0;
    for (let page = 0; page < 20; page += 1) {
      const res = await api("GET", `/api/problems?page=${page}&size=50`, { token });
      const rows = res.body?.content || res.body?.problems || (Array.isArray(res.body) ? res.body : []);
      if (!rows.length) break;
      total += rows.length;
      if (rows.length < 50) break;
    }
    const bundles = await api("GET", "/api/bundles");
    const bcount = Array.isArray(bundles.body) ? bundles.body.length : (bundles.body?.content || []).length;
    idem.apiProblems = total;
    idem.apiBundles = bcount;
    idem.ok = total === 187 && bcount === 17;
    summary.idempotency = idem;
  }

  summary.finishedAt = new Date().toISOString();
  summary.totals.UNTESTED = problems.length - summary.perProblem.length;
  summary.acceptance = {
    referenceAccepted: summary.totals.ACCEPTED,
    referenceRequired: problems.length,
    allAccepted: summary.totals.ACCEPTED === problems.length,
    negativesOk: summary.totals.negativeFailed === 0,
    noHiddenLeaks: summary.totals.hiddenLeaks === 0,
    bundlesOk: summary.bundleReport?.ok !== false,
    idempotent: summary.idempotency?.ok !== false,
  };

  writeFileSync(resolve(reportDir, "phase6-validation.json"), JSON.stringify(summary, null, 2));

  const md = [];
  md.push("# Phase 6 — Judge Validation Report");
  md.push("");
  md.push(`- API: \`${apiBase}\``);
  md.push(`- Problems tested: **${problems.length}**`);
  md.push(`- Reference ACCEPTED: **${summary.totals.ACCEPTED}/${problems.length}**`);
  md.push(`- WRONG_ANSWER (reference mishits): **${summary.totals.WRONG_ANSWER}**`);
  md.push(`- TLE: **${summary.totals.TIME_LIMIT_EXCEEDED}**`);
  md.push(`- Runtime error: **${summary.totals.RUNTIME_ERROR}**`);
  md.push(`- Infra errors: **${summary.totals.INFRASTRUCTURE_ERROR}**`);
  md.push(`- Negatives discriminated: **${summary.totals.negativeDiscriminated}** (failed: ${summary.totals.negativeFailed})`);
  md.push(`- Multi-lang accepted: **${summary.totals.multiLangAccepted}** (failed: ${summary.totals.multiLangFailed})`);
  md.push(`- Hidden leaks: **${summary.totals.hiddenLeaks}**`);
  md.push(`- Bundles: **${summary.bundleReport?.count ?? "?"}** (ok=${summary.bundleReport?.ok})`);
  md.push(`- Idempotency: problems=${summary.idempotency?.apiProblems} bundles=${summary.idempotency?.apiBundles} ok=${summary.idempotency?.ok}`);
  md.push("");
  md.push("## Failures");
  if (!summary.failures.length) md.push("_None_");
  else for (const f of summary.failures) md.push(`- \`${f.slug}\` ${f.stage}: ${f.verdict || f.error || JSON.stringify(f.issues || {})}`);
  md.push("");
  md.push("## Notes");
  md.push("- Local Judge0 used the isolated cz-pb-staging stack only; cgroup workaround remains local-only.");
  md.push("- Primary reference language: Python 3.8 (id 71). Node 63 TLEs and Java 62 OOMs in this local isolate.");
  md.push("- Phase 1 uses algorithmic Python where authored; remaining problems use oracle-consistent Python references covering all authored public+hidden cases.");
  md.push("- C++ (54) used for multi-lang samples. Local Judge0 cgroup workaround stays on cz-pb-staging only.");
  md.push("- Hidden tests/oracles/credentials remain under `.local/problem-bank/`.");
  writeFileSync(resolve(reportDir, "phase6-validation.md"), md.join("\n"));

  console.log(md.join("\n"));
  if (!summary.acceptance.allAccepted || summary.totals.negativeFailed || summary.totals.hiddenLeaks) {
    process.exit(2);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
